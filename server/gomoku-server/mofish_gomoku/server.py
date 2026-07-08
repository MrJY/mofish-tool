from __future__ import annotations

import asyncio
import base64
import hashlib
import json
import re
import struct
import uuid
from urllib.parse import parse_qs, urlsplit

from .admin import render_admin_html
from .models import BOARD_SIZE, MIN_PLAYER_UUID_LENGTH, Client, Room, Stone
from .store import PlayerStore
from .utils import error, log


GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
NICKNAME_PATTERN = re.compile(r"^[A-Za-z0-9_\-\u4e00-\u9fa5]{1,32}$")


class GomokuServer:
    def __init__(self, store: PlayerStore, admin_token: str = "") -> None:
        self.store = store
        self.admin_token = admin_token.strip()
        self.clients_by_uuid: dict[str, Client] = {}
        self.nickname_keys: dict[str, str] = {}
        self.rooms_by_id: dict[str, Room] = {}
        self.random_queue: list[Client] = []
        self.lock = asyncio.Lock()

    async def serve(self, host: str, port: int) -> None:
        server = await asyncio.start_server(self.handle_connection, host, port)
        log(f"Gomoku server listening on ws://{host}:{port}/gomoku")
        log(f"Health check endpoint: http://{host}:{port}/health")
        log(f"Admin page endpoint: http://{host}:{port}/admin")
        async with server:
            await server.serve_forever()

    async def handle_connection(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        client = Client(reader, writer)
        try:
            if not await self.handshake(reader, writer):
                return
            while not writer.is_closing():
                message = await self.read_text_frame(reader)
                if message is None:
                    break
                await self.handle_message(client, message)
        except asyncio.IncompleteReadError:
            pass
        except Exception as exc:
            log(f"client error: {exc}")
        finally:
            await self.disconnect(client)

    async def handshake(self, reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> bool:
        raw_headers = await reader.readuntil(b"\r\n\r\n")
        lines = raw_headers.decode("iso-8859-1").split("\r\n")
        request_parts = lines[0].split(" ")
        raw_path = request_parts[1] if len(request_parts) >= 2 else "/"
        parsed_url = urlsplit(raw_path)
        path = parsed_url.path
        query = parse_qs(parsed_url.query)
        headers = {}
        for line in lines[1:]:
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.strip().lower()] = value.strip()
        if path == "/health":
            await self.send_http_response(writer, "ok\n", content_type="text/plain; charset=utf-8")
            return False
        if path == "/admin":
            await self.handle_admin_page(writer, query)
            return False
        ws_key = headers.get("sec-websocket-key")
        if not ws_key:
            await self.send_http_response(writer, "Not Found\n", status="404 Not Found", content_type="text/plain; charset=utf-8")
            return False
        accept = base64.b64encode(hashlib.sha1((ws_key + GUID).encode("ascii")).digest()).decode("ascii")
        writer.write(
            (
                "HTTP/1.1 101 Switching Protocols\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                f"Sec-WebSocket-Accept: {accept}\r\n"
                "\r\n"
            ).encode("ascii")
        )
        await writer.drain()
        return True

    async def handle_admin_page(self, writer: asyncio.StreamWriter, query: dict[str, list[str]]) -> None:
        if self.admin_token:
            request_token = query.get("token", [""])[0]
            if request_token != self.admin_token:
                await self.send_http_response(
                    writer,
                    "Forbidden\n",
                    status="403 Forbidden",
                    content_type="text/plain; charset=utf-8",
                )
                return
        await self.send_http_response(writer, self.admin_html(), content_type="text/html; charset=utf-8")

    async def send_http_response(
        self,
        writer: asyncio.StreamWriter,
        body: str,
        status: str = "200 OK",
        content_type: str = "text/plain; charset=utf-8",
    ) -> None:
        body_bytes = body.encode("utf-8")
        writer.write(
            (
                f"HTTP/1.1 {status}\r\n"
                f"Content-Type: {content_type}\r\n"
                f"Content-Length: {len(body_bytes)}\r\n"
                "Cache-Control: no-store\r\n"
                "Connection: close\r\n"
                "\r\n"
            ).encode("ascii") + body_bytes
        )
        await writer.drain()
        writer.close()
        await writer.wait_closed()

    def admin_html(self) -> str:
        online_clients = [client for client in self.clients_by_uuid.values() if client.registered]
        waiting_uuids = {client.player_uuid for client in self.random_queue if client.player_uuid}
        return render_admin_html(self.store, online_clients, self.rooms_by_id.values(), waiting_uuids)

    async def read_text_frame(self, reader: asyncio.StreamReader) -> str | None:
        first = await reader.readexactly(1)
        second = await reader.readexactly(1)
        first_byte = first[0]
        second_byte = second[0]
        opcode = first_byte & 0x0F
        if opcode == 0x8:
            return None
        if opcode != 0x1:
            raise ValueError("only text frames are supported")
        masked = bool(second_byte & 0x80)
        length = second_byte & 0x7F
        if length == 126:
            length = struct.unpack("!H", await reader.readexactly(2))[0]
        elif length == 127:
            length = struct.unpack("!Q", await reader.readexactly(8))[0]
        mask = await reader.readexactly(4) if masked else b""
        payload = bytearray(await reader.readexactly(length))
        if masked:
            for index in range(length):
                payload[index] ^= mask[index % 4]
        return payload.decode("utf-8")

    async def handle_message(self, client: Client, raw_message: str) -> None:
        try:
            payload = json.loads(raw_message)
        except json.JSONDecodeError:
            await client.send(error("消息不是有效 JSON。"))
            return
        message_type = payload.get("type")
        if message_type == "register":
            await self.register(client, str(payload.get("uuid", "")), str(payload.get("nickname", "")))
        elif message_type == "listUsers":
            await client.send(self.users_payload())
        elif message_type == "matchRandom":
            await self.match_random(client)
        elif message_type == "invite":
            await self.invite(client, str(payload.get("targetUuid", "")))
        elif message_type == "acceptInvite":
            await self.accept_invite(client, str(payload.get("fromUuid", "")))
        elif message_type == "declineInvite":
            await self.decline_invite(client, str(payload.get("fromUuid", "")))
        elif message_type == "move":
            await self.move(client, str(payload.get("roomId", "")), payload.get("x"), payload.get("y"))
        elif message_type == "resign":
            await self.resign(client, str(payload.get("roomId", "")))
        elif message_type == "leave":
            await self.leave_room(client)

    async def register(self, client: Client, player_uuid: str, nickname: str) -> None:
        clean_uuid = player_uuid.strip()
        clean_nickname = nickname.strip()[:32]
        if len(clean_uuid) < MIN_PLAYER_UUID_LENGTH:
            await client.send(error("UUID 不能少于 32 位。"))
            return
        if not NICKNAME_PATTERN.match(clean_nickname):
            await client.send(error("昵称只能包含中英文、数字、下划线或短横线，长度 1-32。"))
            return
        nickname_key = clean_nickname.casefold()
        async with self.lock:
            if clean_uuid in self.clients_by_uuid:
                await client.send(error("这个 UUID 已在线。"))
                return
            owner_uuid = self.nickname_keys.get(nickname_key)
            if owner_uuid is not None and owner_uuid != clean_uuid:
                await client.send(error("昵称已被占用。"))
                return
            record = self.store.get_or_create_player(clean_uuid, clean_nickname)
            client.player_uuid = clean_uuid
            client.refresh_from_record(record)
            self.clients_by_uuid[clean_uuid] = client
            self.nickname_keys[nickname_key] = clean_uuid
        await client.send({"type": "registered", **client.user_payload()})
        await self.broadcast_users()

    async def match_random(self, client: Client) -> None:
        if not await self.require_registered(client):
            return
        async with self.lock:
            if client.room_id:
                await client.send(error("当前已在对局中。"))
                return
            self.random_queue = [item for item in self.random_queue if item is not client]
            opponent = next((item for item in self.random_queue if item is not client and item.registered and not item.room_id), None)
            if opponent is None:
                self.random_queue.append(client)
                await client.send({"type": "info", "message": "已进入自动匹配队列。"})
                return
            self.random_queue.remove(opponent)
            await self.start_room(client, opponent)

    async def invite(self, client: Client, target_uuid: str) -> None:
        if not await self.require_registered(client):
            return
        target = self.clients_by_uuid.get(target_uuid)
        if target is None or target is client:
            await client.send(error("目标用户不存在。"))
            return
        if target.room_id:
            await client.send(error("目标用户正在对局中。"))
            return
        await target.send({"type": "invite", "from": client.nickname, "fromUuid": client.player_uuid})

    async def accept_invite(self, client: Client, from_uuid: str) -> None:
        inviter = self.clients_by_uuid.get(from_uuid)
        if inviter is None or inviter.room_id or client.room_id:
            await client.send(error("邀请已失效。"))
            return
        async with self.lock:
            self.random_queue = [item for item in self.random_queue if item not in (client, inviter)]
            await self.start_room(inviter, client)

    async def decline_invite(self, client: Client, from_uuid: str) -> None:
        inviter = self.clients_by_uuid.get(from_uuid)
        if inviter is not None:
            await inviter.send(error(f"{client.nickname or ''} 拒绝了邀请。"))

    async def start_room(self, black: Client, white: Client) -> None:
        room = Room(room_id=str(uuid.uuid4())[:8], black=black, white=white)
        self.rooms_by_id[room.room_id] = room
        black.room_id = room.room_id
        white.room_id = room.room_id
        await black.send(
            {
                "type": "gameStart",
                "roomId": room.room_id,
                "stone": "black",
                "opponent": white.nickname,
                "opponentUuid": white.player_uuid,
            }
        )
        await white.send(
            {
                "type": "gameStart",
                "roomId": room.room_id,
                "stone": "white",
                "opponent": black.nickname,
                "opponentUuid": black.player_uuid,
            }
        )
        await self.broadcast_users()

    async def move(self, client: Client, room_id: str, x_value: object, y_value: object) -> None:
        room = self.rooms_by_id.get(room_id)
        if room is None:
            await client.send(error("房间不存在。"))
            return
        if not isinstance(x_value, int) or not isinstance(y_value, int):
            await client.send(error("落子坐标无效。"))
            return
        stone = room.stone_for(client)
        if stone is None:
            await client.send(error("你不在这个房间。"))
            return
        if room.turn != stone:
            await client.send(error("还没轮到你。"))
            return
        if not (0 <= x_value < BOARD_SIZE and 0 <= y_value < BOARD_SIZE) or room.board[y_value][x_value] != Stone.EMPTY:
            await client.send(error("落子位置无效。"))
            return
        room.board[y_value][x_value] = stone
        room.turn = Stone.opposite(stone)
        await room.broadcast({"type": "move", "roomId": room.room_id, "x": x_value, "y": y_value, "stone": Stone.wire(stone)})
        if room.has_five(x_value, y_value, stone):
            await self.finish_room(room, stone, "五子连珠。")

    async def resign(self, client: Client, room_id: str) -> None:
        room = self.rooms_by_id.get(room_id)
        if room is None:
            return
        opponent = room.opponent_of(client)
        if opponent is None:
            return
        winner = room.stone_for(opponent) or Stone.EMPTY
        await self.finish_room(room, winner, "对手认输。")

    async def leave_room(self, client: Client) -> None:
        room = self.rooms_by_id.get(client.room_id or "")
        if room is None:
            return
        opponent = room.opponent_of(client)
        winner = room.stone_for(opponent) if opponent is not None else Stone.EMPTY
        await self.finish_room(room, winner or Stone.EMPTY, "对手离开。")

    async def finish_room(self, room: Room, winner: int, reason: str) -> None:
        self.rooms_by_id.pop(room.room_id, None)
        room.black.room_id = None
        room.white.room_id = None
        winner_client = room.client_for_stone(winner)
        loser_client = room.opponent_of(winner_client) if winner_client is not None else None
        self.store.record_result(
            winner_client.player_uuid if winner_client is not None else None,
            loser_client.player_uuid if loser_client is not None else None,
        )
        self.refresh_client_stats(room.black)
        self.refresh_client_stats(room.white)
        await room.broadcast({"type": "gameOver", "roomId": room.room_id, "winner": Stone.wire(winner), "reason": reason})
        await self.broadcast_users()

    async def disconnect(self, client: Client) -> None:
        room_to_finish: Room | None = None
        async with self.lock:
            self.random_queue = [item for item in self.random_queue if item is not client]
            if client.room_id:
                room_to_finish = self.rooms_by_id.get(client.room_id)
            if client.player_uuid:
                self.clients_by_uuid.pop(client.player_uuid, None)
            if client.nickname:
                self.nickname_keys.pop(client.nickname.casefold(), None)
        if room_to_finish is not None:
            opponent = room_to_finish.opponent_of(client)
            winner = room_to_finish.stone_for(opponent) if opponent is not None else Stone.EMPTY
            await self.finish_room(room_to_finish, winner or Stone.EMPTY, "对手离线。")
        await client.close()
        await self.broadcast_users()

    async def require_registered(self, client: Client) -> bool:
        if client.registered:
            return True
        await client.send(error("请先注册昵称。"))
        return False

    async def broadcast_users(self) -> None:
        payload = self.users_payload()
        await asyncio.gather(*(client.send(payload) for client in list(self.clients_by_uuid.values())), return_exceptions=True)

    def users_payload(self) -> dict:
        return {
            "type": "users",
            "users": sorted(
                (client.user_payload() for client in self.clients_by_uuid.values() if client.registered),
                key=lambda item: str(item["nickname"]).casefold(),
            ),
        }

    def refresh_client_stats(self, client: Client) -> None:
        if not client.player_uuid:
            return
        record = self.store.get_player(client.player_uuid)
        if record is not None:
            client.refresh_from_record(record)
