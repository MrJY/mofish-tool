import asyncio
import tempfile
import unittest
from pathlib import Path
from urllib.parse import urlencode

from mofish_gomoku.admin import render_admin_html
from mofish_gomoku.server import GomokuServer
from mofish_gomoku.store import PlayerStore


class FakeWriter:
    def __init__(self) -> None:
        self.data = bytearray()
        self.closed = False

    def write(self, data: bytes) -> None:
        self.data.extend(data)

    async def drain(self) -> None:
        pass

    def close(self) -> None:
        self.closed = True

    async def wait_closed(self) -> None:
        pass


class AdminMaintenanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.store = PlayerStore(Path(self.temp_dir.name) / "gomoku.db")
        self.player_uuid = "1234567890abcdef1234567890abcdef"
        self.store.get_or_create_player(self.player_uuid, "before")

    def tearDown(self) -> None:
        self.store.close()
        self.temp_dir.cleanup()

    def test_update_and_delete_player(self) -> None:
        server = GomokuServer(self.store, admin_token="secret")

        update_message = asyncio.run(
            server.handle_admin_action(
                {
                    "action": ["update"],
                    "uuid": [self.player_uuid],
                    "nickname": ["after"],
                    "wins": ["7"],
                    "losses": ["3"],
                }
            )
        )
        updated = self.store.get_player(self.player_uuid)

        self.assertEqual("玩家数据已保存。", update_message)
        self.assertIsNotNone(updated)
        self.assertEqual("after", updated.nickname)
        self.assertEqual(7, updated.wins)
        self.assertEqual(3, updated.losses)
        self.assertEqual(10, updated.games)

        delete_message = asyncio.run(
            server.handle_admin_action({"action": ["delete"], "uuid": [self.player_uuid]})
        )

        self.assertEqual("玩家数据已删除。", delete_message)
        self.assertIsNone(self.store.get_player(self.player_uuid))

    def test_online_player_cannot_be_modified(self) -> None:
        server = GomokuServer(self.store, admin_token="secret")
        server.clients_by_uuid[self.player_uuid] = object()

        message = asyncio.run(
            server.handle_admin_action(
                {
                    "action": ["delete"],
                    "uuid": [self.player_uuid],
                }
            )
        )

        self.assertIn("玩家当前在线", message)
        self.assertIsNotNone(self.store.get_player(self.player_uuid))

    def test_admin_page_is_read_only_without_token(self) -> None:
        readonly_html = render_admin_html(self.store, [], [], set())
        editable_html = render_admin_html(self.store, [], [], set(), admin_token="secret")

        self.assertIn("当前为只读模式", readonly_html)
        self.assertNotIn('value="delete"', readonly_html)
        self.assertIn('value="delete"', editable_html)
        self.assertIn('value="update"', editable_html)

    def test_admin_post_updates_player_and_redirects(self) -> None:
        server = GomokuServer(self.store, admin_token="secret")
        writer = FakeWriter()
        body = urlencode(
            {
                "action": "update",
                "uuid": self.player_uuid,
                "nickname": "http_update",
                "wins": "4",
                "losses": "2",
            }
        ).encode("utf-8")

        asyncio.run(server.handle_admin_page(writer, {"token": ["secret"]}, "POST", body))
        response = writer.data.decode("utf-8")
        updated = self.store.get_player(self.player_uuid)

        self.assertIn("HTTP/1.1 303 See Other", response)
        self.assertIn("Location: /admin?", response)
        self.assertTrue(writer.closed)
        self.assertEqual("http_update", updated.nickname)
        self.assertEqual(6, updated.games)


if __name__ == "__main__":
    unittest.main()
