import html
from datetime import datetime, timezone
from typing import Optional


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def escape(value: object) -> str:
    return html.escape(str(value or ""), quote=True)


def short_uuid(value: Optional[str]) -> str:
    clean = value or ""
    if len(clean) <= 12:
        return clean
    return f"{clean[:8]}...{clean[-4:]}"


def error(message: str) -> dict:
    return {"type": "error", "message": message}


def log(message: str) -> None:
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] {message}", flush=True)
