from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Optional

from .models import PlayerRecord
from .utils import utc_now


class PlayerStore:
    def __init__(self, db_path: Path) -> None:
        db_path.parent.mkdir(parents=True, exist_ok=True)
        self.connection = sqlite3.connect(db_path)
        self.connection.row_factory = sqlite3.Row
        self.connection.execute("PRAGMA journal_mode=WAL")
        self.connection.execute("PRAGMA synchronous=NORMAL")
        self.connection.execute(
            """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                nickname TEXT NOT NULL,
                wins INTEGER NOT NULL DEFAULT 0,
                losses INTEGER NOT NULL DEFAULT 0,
                games INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )
        self.connection.commit()

    def get_or_create_player(self, player_uuid: str, nickname: str) -> PlayerRecord:
        now = utc_now()
        with self.connection:
            row = self.connection.execute(
                "SELECT uuid, nickname, wins, losses, games FROM players WHERE uuid = ?",
                (player_uuid,),
            ).fetchone()
            if row is None:
                self.connection.execute(
                    """
                    INSERT INTO players (uuid, nickname, wins, losses, games, created_at, updated_at)
                    VALUES (?, ?, 0, 0, 0, ?, ?)
                    """,
                    (player_uuid, nickname, now, now),
                )
            else:
                self.connection.execute(
                    "UPDATE players SET nickname = ?, updated_at = ? WHERE uuid = ?",
                    (nickname, now, player_uuid),
                )
        return self.get_player(player_uuid) or PlayerRecord(player_uuid, nickname)

    def get_player(self, player_uuid: str) -> Optional[PlayerRecord]:
        row = self.connection.execute(
            "SELECT uuid, nickname, wins, losses, games FROM players WHERE uuid = ?",
            (player_uuid,),
        ).fetchone()
        if row is None:
            return None
        return PlayerRecord(
            uuid=row["uuid"],
            nickname=row["nickname"],
            wins=int(row["wins"]),
            losses=int(row["losses"]),
            games=int(row["games"]),
        )

    def list_players(self, limit: int = 100) -> list[PlayerRecord]:
        rows = self.connection.execute(
            """
            SELECT uuid, nickname, wins, losses, games
            FROM players
            ORDER BY wins DESC, games DESC, updated_at DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
        return [
            PlayerRecord(
                uuid=row["uuid"],
                nickname=row["nickname"],
                wins=int(row["wins"]),
                losses=int(row["losses"]),
                games=int(row["games"]),
            )
            for row in rows
        ]

    def player_count(self) -> int:
        row = self.connection.execute("SELECT COUNT(*) AS count FROM players").fetchone()
        return int(row["count"]) if row is not None else 0

    def update_player(self, player_uuid: str, nickname: str, wins: int, losses: int) -> Optional[PlayerRecord]:
        now = utc_now()
        with self.connection:
            cursor = self.connection.execute(
                """
                UPDATE players
                SET nickname = ?, wins = ?, losses = ?, games = ?, updated_at = ?
                WHERE uuid = ?
                """,
                (nickname, wins, losses, wins + losses, now, player_uuid),
            )
        if cursor.rowcount == 0:
            return None
        return self.get_player(player_uuid)

    def delete_player(self, player_uuid: str) -> bool:
        with self.connection:
            cursor = self.connection.execute("DELETE FROM players WHERE uuid = ?", (player_uuid,))
        return cursor.rowcount > 0

    def record_result(self, winner_uuid: Optional[str], loser_uuid: Optional[str]) -> None:
        now = utc_now()
        with self.connection:
            if winner_uuid:
                self.connection.execute(
                    """
                    UPDATE players
                    SET wins = wins + 1, games = games + 1, updated_at = ?
                    WHERE uuid = ?
                    """,
                    (now, winner_uuid),
                )
            if loser_uuid:
                self.connection.execute(
                    """
                    UPDATE players
                    SET losses = losses + 1, games = games + 1, updated_at = ?
                    WHERE uuid = ?
                    """,
                    (now, loser_uuid),
                )

    def close(self) -> None:
        self.connection.close()
