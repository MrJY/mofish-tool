from __future__ import annotations

import os
import pwd
import sys
from pathlib import Path


def prepare_data_directory(data_dir: Path, user_name: str) -> tuple[int, int]:
    user = pwd.getpwnam(user_name)
    data_dir.mkdir(parents=True, exist_ok=True)
    for path in (data_dir, *data_dir.rglob("*")):
        os.chown(path, user.pw_uid, user.pw_gid, follow_symlinks=False)
    return user.pw_uid, user.pw_gid


def main() -> None:
    command = sys.argv[1:]
    if not command:
        raise SystemExit("No server command was provided.")

    user_name = os.environ.get("GOMOKU_RUN_USER", "gomoku")
    data_dir = Path(os.environ.get("GOMOKU_DATA_DIR", "/data"))
    if os.geteuid() == 0:
        try:
            uid, gid = prepare_data_directory(data_dir, user_name)
        except OSError as exc:
            raise SystemExit(f"Cannot prepare writable data directory {data_dir}: {exc}") from exc
        os.initgroups(user_name, gid)
        os.setgid(gid)
        os.setuid(uid)

    if not os.access(data_dir, os.W_OK):
        raise SystemExit(f"Data directory is not writable: {data_dir}")
    os.execvp(command[0], command)


if __name__ == "__main__":
    main()
