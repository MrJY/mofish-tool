FROM python:3.12-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1

COPY gomoku_server.py /app/gomoku_server.py
COPY mofish_gomoku /app/mofish_gomoku

RUN useradd --system --create-home --home-dir /home/gomoku gomoku \
    && mkdir -p /data \
    && chown -R gomoku:gomoku /data /app

USER gomoku

EXPOSE 8765

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/health', timeout=2).read()"

CMD ["python", "-m", "mofish_gomoku", "--host", "0.0.0.0", "--port", "8765", "--db", "/data/gomoku.db"]
