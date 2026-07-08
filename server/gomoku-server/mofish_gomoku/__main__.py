import argparse
import asyncio
import os
import sys
from pathlib import Path


if sys.version_info < (3, 7):
    raise SystemExit("MoFish Gomoku server requires Python 3.7+. Use Docker or upgrade Python.")

from .server import GomokuServer
from .store import PlayerStore
from .utils import log


def main() -> None:
    parser = argparse.ArgumentParser(description="MoFish Gomoku WebSocket server")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--db", default=str(Path(__file__).resolve().parent.parent / "gomoku.db"))
    parser.add_argument("--admin-token", default=os.environ.get("GOMOKU_ADMIN_TOKEN", ""))
    args = parser.parse_args()
    log(f"Using SQLite database: {Path(args.db).resolve()}")
    store = PlayerStore(Path(args.db))
    try:
        asyncio.run(GomokuServer(store, admin_token=args.admin_token).serve(args.host, args.port))
    finally:
        store.close()


if __name__ == "__main__":
    main()
