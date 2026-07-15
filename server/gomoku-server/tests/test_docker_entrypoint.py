import importlib.util
import os
import pwd
import tempfile
import unittest
from pathlib import Path


ENTRYPOINT_PATH = Path(__file__).resolve().parents[1] / "docker_entrypoint.py"
SPEC = importlib.util.spec_from_file_location("mofish5_docker_entrypoint", ENTRYPOINT_PATH)
ENTRYPOINT = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(ENTRYPOINT)


class DockerEntrypointTest(unittest.TestCase):
    def test_prepare_data_directory_includes_existing_database_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            data_dir = Path(temp_dir) / "data"
            database = data_dir / "gomoku.db"
            data_dir.mkdir()
            database.write_bytes(b"sqlite-data")
            current_user = pwd.getpwuid(os.getuid()).pw_name

            uid, gid = ENTRYPOINT.prepare_data_directory(data_dir, current_user)

            self.assertEqual(os.getuid(), uid)
            self.assertEqual(os.getgid(), gid)
            self.assertEqual(uid, data_dir.stat().st_uid)
            self.assertEqual(uid, database.stat().st_uid)
            self.assertTrue(os.access(data_dir, os.W_OK))


if __name__ == "__main__":
    unittest.main()
