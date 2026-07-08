from __future__ import annotations

import asyncio
import json
import struct
from dataclasses import dataclass, field
from typing import Optional


BOARD_SIZE = 15
MIN_PLAYER_UUID_LENGTH = 32


class Stone:
    EMPTY = 0
    BLACK = 1
    WHITE = 2

    @staticmethod
    def wire(value: int) -> str:
        return {Stone.BLACK: "black", Stone.WHITE: "white"}.get(value, "empty")

    @staticmethod
    def opposite(value: int) -> int:
        return Stone.WHITE if value == Stone.BLACK else Stone.BLACK


@dataclass
class PlayerRecord:
    uuid: str
    nickname: str
    wins: int = 0
    losses: int = 0
    games: int = 0

    def payload(self) -> dict:
        return {
            "uuid": self.uuid,
            "nickname": self.nickname,
            "wins": self.wins,
            "losses": self.losses,
            "games": self.games,
        }


@dataclass
class Client:
    reader: asyncio.StreamReader
    writer: asyncio.StreamWriter
    player_uuid: Optional[str] = None
    nickname: Optional[str] = None
    wins: int = 0
    losses: int = 0
    games: int = 0
    room_id: Optional[str] = None
    write_lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    @property
    def registered(self) -> bool:
        return bool(self.player_uuid and self.nickname)

    def user_payload(self) -> dict:
        return {
            "uuid": self.player_uuid,
            "nickname": self.nickname,
            "wins": self.wins,
            "losses": self.losses,
            "games": self.games,
        }

    def refresh_from_record(self, record: PlayerRecord) -> None:
        self.nickname = record.nickname
        self.wins = record.wins
        self.losses = record.losses
        self.games = record.games

    async def send(self, payload: dict) -> None:
        if self.writer.is_closing():
            return
        data = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        if len(data) < 126:
            header = bytes([0x81, len(data)])
        elif len(data) <= 0xFFFF:
            header = bytes([0x81, 126]) + struct.pack("!H", len(data))
        else:
            header = bytes([0x81, 127]) + struct.pack("!Q", len(data))
        async with self.write_lock:
            self.writer.write(header + data)
            await self.writer.drain()

    async def close(self) -> None:
        if not self.writer.is_closing():
            self.writer.close()
            await self.writer.wait_closed()


@dataclass
class Room:
    room_id: str
    black: Client
    white: Client
    board: list[list[int]] = field(
        default_factory=lambda: [[Stone.EMPTY for _ in range(BOARD_SIZE)] for _ in range(BOARD_SIZE)]
    )
    turn: int = Stone.BLACK

    def stone_for(self, client: Client) -> Optional[int]:
        if client is self.black:
            return Stone.BLACK
        if client is self.white:
            return Stone.WHITE
        return None

    def client_for_stone(self, stone: int) -> Optional[Client]:
        if stone == Stone.BLACK:
            return self.black
        if stone == Stone.WHITE:
            return self.white
        return None

    def opponent_of(self, client: Optional[Client]) -> Optional[Client]:
        if client is self.black:
            return self.white
        if client is self.white:
            return self.black
        return None

    async def broadcast(self, payload: dict) -> None:
        await asyncio.gather(self.black.send(payload), self.white.send(payload), return_exceptions=True)

    def has_five(self, x: int, y: int, stone: int) -> bool:
        return any(
            1 + self._count(x, y, dx, dy, stone) + self._count(x, y, -dx, -dy, stone) >= 5
            for dx, dy in ((1, 0), (0, 1), (1, 1), (1, -1))
        )

    def _count(self, x: int, y: int, dx: int, dy: int, stone: int) -> int:
        count = 0
        current_x = x + dx
        current_y = y + dy
        while 0 <= current_x < BOARD_SIZE and 0 <= current_y < BOARD_SIZE and self.board[current_y][current_x] == stone:
            count += 1
            current_x += dx
            current_y += dy
        return count
