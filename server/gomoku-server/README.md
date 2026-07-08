# 五子棋服务端

这是 `mofish五子棋` 标签页使用的轻量 WebSocket 服务端。

服务端只使用 Python 标准库，不需要安装第三方依赖。玩家身份、昵称和战绩使用 SQLite 保存。直接运行需要 Python 3.7+；Docker 部署会使用镜像内的 Python 3.12。

## 项目结构

```text
gomoku-server/
├── Dockerfile
├── docker-compose.yml
├── gomoku_server.py          # 兼容入口，仍可直接执行
└── mofish_gomoku/
    ├── __main__.py           # 推荐入口：python -m mofish_gomoku
    ├── admin.py              # 管理页面
    ├── models.py             # 棋局、玩家和房间模型
    ├── server.py             # WebSocket 和 HTTP 服务
    ├── store.py              # SQLite 持久化
    └── utils.py
```

## Docker 部署

推荐用 Docker Compose 部署，SQLite 数据会保存到 Docker volume，不会因为容器重建而丢失。

先把 `server/gomoku-server` 目录上传到服务器，然后进入该目录：

```bash
cd gomoku-server
```

构建并启动：

```bash
docker compose up -d --build
```

查看运行状态：

```bash
docker compose ps
```

查看日志：

```bash
docker compose logs -f
```

正常启动后日志里应该能看到类似内容：

```text
[2026-07-07 18:21:56] Using SQLite database: /data/gomoku.db
[2026-07-07 18:21:56] Gomoku server listening on ws://0.0.0.0:8765/gomoku
[2026-07-07 18:21:56] Health check endpoint: http://0.0.0.0:8765/health
```

检查服务是否正常：

```bash
curl https://demo.mrjy.online/health
```

返回下面内容表示服务已启动并能接受连接：

```text
ok
```

查看 Docker 健康状态：

```bash
docker compose ps
```

如果状态里显示 `healthy`，说明容器内健康检查也通过了。

如果 `docker compose logs -f` 没有启动日志，或者 `curl /health` 返回 `Empty reply from server`，优先强制重建容器，避免仍在运行旧镜像：

```bash
docker compose down
docker compose up -d --build --force-recreate
docker compose logs -f
```

继续排查时可以看容器内健康检查：

```bash
docker inspect --format='{{json .State.Health}}' mofish-gomoku-server
```

也可以直接在容器内请求健康接口：

```bash
docker compose exec gomoku-server python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8765/health', timeout=2).read().decode())"
```

如果容器内返回 `ok`，但宿主机 `curl http://127.0.0.1:8765/health` 不通，通常是端口映射或防火墙问题。

打开简单管理页面：

```text
https://demo.mrjy.online/admin
```

管理页面会显示在线用户、等待匹配人数、进行中对局和战绩榜，每 10 秒自动刷新。

如果要给管理页面加口令，可以编辑 `docker-compose.yml`：

```yaml
environment:
  GOMOKU_ADMIN_TOKEN: "换成你的管理口令"
```

然后重启：

```bash
docker compose up -d --build
```

设置口令后访问：

```text
https://demo.mrjy.online/admin?token=换成你的管理口令
```

停止服务：

```bash
docker compose down
```

默认对外暴露端口：

```text
8765
```

默认连接地址：

```text
wss://demo.mrjy.online/gomoku
```

SQLite 数据库在容器内路径是：

```text
/data/gomoku.db
```

`docker-compose.yml` 已经把容器内的 `/data` 挂到当前项目目录下的 `./data`。所以实际数据文件在宿主机：

```text
./data/gomoku.db
```

只要不删除这个目录和数据库文件，服务端重启、容器重建后，玩家昵称、对局数、胜局数、负局数都会保留。

如果要备份数据，可以执行：

```bash
cp data/gomoku.db gomoku.db.backup
```

## 直接启动

不使用 Docker 时，可以直接用 Python 启动。

在 `gomoku-server` 目录执行：

```bash
python3 -m mofish_gomoku --host 0.0.0.0 --port 8765
```

也可以继续使用兼容入口，在项目根目录执行：

```bash
python3 server/gomoku-server/gomoku_server.py --host 0.0.0.0 --port 8765
```

本机默认连接地址：

```text
ws://localhost:8765/gomoku
```

本机健康检查地址：

```text
http://localhost:8765/health
```

插件端当前会固定连接这个地址，不在界面里提供服务器配置。

SQLite 数据库默认写入：

```text
server/gomoku-server/gomoku.db
```

也可以手动指定数据库路径：

```bash
python3 -m mofish_gomoku --host 0.0.0.0 --port 8765 --db /data/mofish/gomoku.db
```

直接启动时也可以给管理页设置口令：

```bash
python3 -m mofish_gomoku --host 0.0.0.0 --port 8765 --admin-token your-token
```

SQLite 是持久化数据库。只要 `.db` 文件还在，服务端重启后玩家昵称、对局数、胜局数、负局数都会保留。

如果需要公网访问，建议在服务器前面配置反向代理并使用 `wss://`，再同步修改插件里的默认地址。

## 玩家身份

- 每个玩家由 UUID 唯一标识。
- 插件第一次进入五子棋标签页时，如果设置里没有 UUID，会自动生成一个 32 位 UUID 并保存到插件设置。
- 用户可以在插件设置里查看或修改自己的 UUID。
- 自定义 UUID 不能少于 32 位。
- 同一个 UUID 代表同一个玩家，战绩也绑定到这个 UUID。
- 昵称只是展示名；同一个 UUID 下次用新昵称登录时，服务端会更新保存的昵称。
- 在线状态下昵称大小写不敏感唯一，例如 `abcd` 和 `ABCD` 不能同时在线。

## 功能

- 用户输入昵称后连接服务端。
- 支持在线用户列表，列表展示昵称、对局数、胜局数和负局数。
- 支持自动匹配：点击后进入等待队列；当另一位在线用户也进入等待队列时，服务端自动创建对局。
- 支持邀请指定在线用户。
- 支持落子、认输、离线结束对局。
- 服务端校验回合、棋盘边界、重复落子和五子连珠胜负。
- 对局结束后，服务端把胜负和总局数写入 SQLite。

## 协议

所有消息都是 WebSocket JSON 文本帧。

客户端发送：

```json
{"type":"register","uuid":"8f6d5f6f8d2547a9a1a4d5e5e5b2f3c1","nickname":"abcd"}
{"type":"listUsers"}
{"type":"matchRandom"}
{"type":"invite","targetUuid":"other-player-uuid"}
{"type":"acceptInvite","fromUuid":"other-player-uuid"}
{"type":"declineInvite","fromUuid":"other-player-uuid"}
{"type":"move","roomId":"room-id","x":7,"y":7}
{"type":"resign","roomId":"room-id"}
{"type":"leave","roomId":"room-id"}
```

服务端返回：

```json
{"type":"registered","uuid":"8f6d5f6f8d2547a9a1a4d5e5e5b2f3c1","nickname":"abcd","wins":0,"losses":0,"games":0}
{"type":"users","users":[{"uuid":"8f6d5f6f8d2547a9a1a4d5e5e5b2f3c1","nickname":"abcd","wins":0,"losses":0,"games":0}]}
{"type":"invite","from":"otherUser","fromUuid":"other-player-uuid"}
{"type":"gameStart","roomId":"room-id","stone":"black","opponent":"otherUser","opponentUuid":"other-player-uuid"}
{"type":"move","roomId":"room-id","x":7,"y":7,"stone":"black"}
{"type":"gameOver","roomId":"room-id","winner":"black","reason":"五子连珠。"}
{"type":"error","message":"昵称已被占用。"}
```

## 棋局规则

- 棋盘大小：15x15。
- 黑棋先手。
- 坐标从 `0` 开始，`x` 是列，`y` 是行。
- 任意方向连续五子即胜。
- 当前版本未实现禁手规则。
