from __future__ import annotations

from datetime import datetime
from typing import Iterable
from urllib.parse import quote_plus

from .models import Client, Room, Stone
from .store import PlayerStore
from .utils import escape, short_uuid


def render_admin_html(
    store: PlayerStore,
    online_clients: Iterable[Client],
    rooms: Iterable[Room],
    waiting_uuids: set[str],
    admin_token: str = "",
    message: str = "",
) -> str:
    online_list = sorted(online_clients, key=lambda item: (item.nickname or "").casefold())
    room_list = sorted(rooms, key=lambda item: item.room_id)
    players = store.list_players(limit=1000)
    online_rows = "\n".join(
        f"""
        <tr>
          <td>{escape(client.nickname)}</td>
          <td><code>{escape(short_uuid(client.player_uuid))}</code></td>
          <td>{escape(client.room_id or "-")}</td>
          <td>{"是" if client.player_uuid in waiting_uuids else "否"}</td>
          <td>{client.games}</td>
          <td>{client.wins}</td>
          <td>{client.losses}</td>
        </tr>
        """
        for client in online_list
    ) or '<tr><td colspan="7" class="empty">暂无在线用户</td></tr>'
    room_rows = "\n".join(
        f"""
        <tr>
          <td>{escape(room.room_id)}</td>
          <td>{escape(room.black.nickname)}<br><code>{escape(short_uuid(room.black.player_uuid))}</code></td>
          <td>{escape(room.white.nickname)}<br><code>{escape(short_uuid(room.white.player_uuid))}</code></td>
          <td>{escape(Stone.wire(room.turn))}</td>
        </tr>
        """
        for room in room_list
    ) or '<tr><td colspan="4" class="empty">暂无进行中对局</td></tr>'
    maintenance_enabled = bool(admin_token)
    admin_action = f"/admin?token={quote_plus(admin_token)}" if maintenance_enabled else "/admin"
    player_row_items = []
    for index, player in enumerate(players, start=1):
        maintenance = ""
        if maintenance_enabled:
            maintenance = f"""
            <form method="post" action="{escape(admin_action)}" class="player-form">
              <input type="hidden" name="uuid" value="{escape(player.uuid)}">
              <label>昵称<input name="nickname" value="{escape(player.nickname)}" maxlength="32" required></label>
              <label>胜<input type="number" name="wins" value="{player.wins}" min="0" max="1000000" required></label>
              <label>负<input type="number" name="losses" value="{player.losses}" min="0" max="1000000" required></label>
              <button type="submit" name="action" value="update">保存</button>
              <button type="submit" name="action" value="delete" class="danger"
                      onclick="return confirm('确认删除玩家 {escape(player.nickname)}？此操作不可恢复。')">删除</button>
            </form>
            """
        else:
            maintenance = '<span class="muted">配置管理口令后可维护</span>'
        player_row_items.append(
            f"""
        <tr>
          <td>{index}</td>
          <td>{escape(player.nickname)}</td>
          <td><code class="uuid">{escape(player.uuid)}</code></td>
          <td>{player.games}</td>
          <td>{player.wins}</td>
          <td>{player.losses}</td>
          <td>{maintenance}</td>
        </tr>
        """
        )
    player_rows = "\n".join(player_row_items) or '<tr><td colspan="7" class="empty">暂无玩家数据</td></tr>'
    generated_at = escape(datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    message_html = f'<div class="notice">{escape(message)}</div>' if message else ""
    maintenance_notice = "" if maintenance_enabled else (
        '<div class="notice warning">当前为只读模式。请配置 GOMOKU_ADMIN_TOKEN 后使用编辑和删除功能。</div>'
    )
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>mofish5 管理</title>
  <style>
    :root {{
      color-scheme: light dark;
      --bg: #f6f7f9;
      --panel: #ffffff;
      --text: #20242a;
      --muted: #6b7280;
      --border: #d9dee7;
      --accent: #2563eb;
      --danger: #dc2626;
    }}
    @media (prefers-color-scheme: dark) {{
      :root {{
        --bg: #111318;
        --panel: #1a1d24;
        --text: #e5e7eb;
        --muted: #9ca3af;
        --border: #303541;
        --accent: #60a5fa;
      }}
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 1.5;
    }}
    main {{
      width: min(1180px, calc(100vw - 32px));
      margin: 24px auto 48px;
    }}
    header {{
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 16px;
      margin-bottom: 18px;
    }}
    h1 {{ margin: 0; font-size: 24px; }}
    h2 {{ margin: 0 0 12px; font-size: 17px; }}
    .muted {{ color: var(--muted); font-size: 13px; }}
    .stats {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
      gap: 10px;
      margin-bottom: 14px;
    }}
    .stat, section {{
      background: var(--panel);
      border: 1px solid var(--border);
      border-radius: 8px;
    }}
    .stat {{ padding: 14px; }}
    .stat strong {{ display: block; font-size: 26px; color: var(--accent); }}
    section {{ padding: 14px; margin-top: 14px; overflow: hidden; }}
    table {{ width: 100%; border-collapse: collapse; font-size: 14px; }}
    th, td {{ padding: 9px 8px; border-top: 1px solid var(--border); text-align: left; vertical-align: top; }}
    th {{ color: var(--muted); font-weight: 600; }}
    code {{ font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }}
    .uuid {{ overflow-wrap: anywhere; }}
    .empty {{ color: var(--muted); text-align: center; padding: 24px; }}
    .notice {{ padding: 10px 12px; margin: 10px 0; border: 1px solid var(--border); border-radius: 6px; background: var(--panel); }}
    .warning {{ color: #b45309; }}
    .player-form {{ display: flex; flex-wrap: wrap; align-items: end; gap: 6px; min-width: 360px; }}
    .player-form label {{ display: grid; gap: 2px; color: var(--muted); font-size: 12px; }}
    .player-form input {{ width: 70px; padding: 5px 6px; border: 1px solid var(--border); border-radius: 4px; background: var(--bg); color: var(--text); }}
    .player-form label:first-of-type input {{ width: 130px; }}
    button {{ padding: 6px 10px; border: 1px solid var(--border); border-radius: 4px; background: var(--panel); color: var(--text); cursor: pointer; }}
    button:hover {{ border-color: var(--accent); }}
    button.danger {{ color: var(--danger); }}
  </style>
</head>
<body>
  <main>
    <header>
      <div>
        <h1>mofish5 管理</h1>
        <div class="muted">生成时间：{generated_at}</div>
      </div>
      <div><a href="{escape(admin_action)}">刷新页面</a></div>
    </header>

    {message_html}
    {maintenance_notice}

    <div class="stats">
      <div class="stat"><span class="muted">在线用户</span><strong>{len(online_list)}</strong></div>
      <div class="stat"><span class="muted">等待匹配</span><strong>{len(waiting_uuids)}</strong></div>
      <div class="stat"><span class="muted">进行中对局</span><strong>{len(room_list)}</strong></div>
      <div class="stat"><span class="muted">累计玩家</span><strong>{store.player_count()}</strong></div>
    </div>

    <section>
      <h2>在线用户</h2>
      <table>
        <thead><tr><th>昵称</th><th>UUID</th><th>房间</th><th>等待匹配</th><th>局数</th><th>胜</th><th>负</th></tr></thead>
        <tbody>{online_rows}</tbody>
      </table>
    </section>

    <section>
      <h2>进行中对局</h2>
      <table>
        <thead><tr><th>房间</th><th>黑棋</th><th>白棋</th><th>当前回合</th></tr></thead>
        <tbody>{room_rows}</tbody>
      </table>
    </section>

    <section>
      <h2>战绩榜</h2>
      <table>
        <thead><tr><th>#</th><th>昵称</th><th>UUID</th><th>局数</th><th>胜</th><th>负</th><th>维护</th></tr></thead>
        <tbody>{player_rows}</tbody>
      </table>
    </section>
  </main>
</body>
</html>
"""
