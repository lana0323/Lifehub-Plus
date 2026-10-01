"""Local development HTTP adapter. Deploy behind authenticated HTTPS ingress."""
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from task_service import ServiceError, create_draft
from action_service import create_action

SLOTS = threading.BoundedSemaphore(1)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # No prompts, credentials, or provider payloads in access logs.

    def setup(self):
        super().setup()
        self.connection.settimeout(35)

    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        # HTTP/1.0 closes each response; advertise this so pooled clients do not
        # reuse a socket that the server has already closed.
        self.send_header("Connection", "close")
        self.close_connection = True
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200, {"status": "ok"}) if self.path == "/health" else self.reply(404, {"error": "not_found"})

    def do_POST(self):
        if self.path not in ("/v1/task-drafts", "/v1/action-drafts"):
            return self.reply(404, {"error": "not_found"})
        if not SLOTS.acquire(blocking=False):
            return self.reply(429, {"error": "busy"})
        try:
            if self.headers.get_content_type() != "application/json":
                raise ServiceError("json_required", 415)
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                raise ServiceError("invalid_body", 400)
            if not 0 < length <= 16000:
                raise ServiceError("invalid_body", 413)
            try:
                body = json.loads(self.rfile.read(length))
            except (ValueError, UnicodeDecodeError):
                raise ServiceError("invalid_json", 400)
            self.reply(200, create_action(body) if self.path == "/v1/action-drafts" else create_draft(body))
        except ServiceError as error:
            self.reply(error.status, {"error": error.code})
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:
            self.reply(500, {"error": "internal_error"})
        finally:
            SLOTS.release()


if __name__ == "__main__":
    host, port = os.environ.get("HOST", "127.0.0.1"), int(os.environ.get("PORT", "8080"))
    print(f"LifeHub task drafts listening on {host}:{port}")
    ThreadingHTTPServer((host, port), Handler).serve_forever()
