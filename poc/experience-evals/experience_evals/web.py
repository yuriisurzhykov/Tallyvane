"""Loopback-only interactive lab using the same contracts and reducer as the CLI."""

import copy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import secrets
import threading
from urllib.parse import urlsplit
import uuid

from pydantic import ValidationError

from .contract import output_schema
from .config import load_config
from .dataset import load_scenarios
from .providers import ApiProvider, ProviderError, ScriptedProvider, estimate_cost
from .runner import PROMPT_PATH, build_input, parse_proposal
from .state import TABLES, PARENTS, StateError, empty_state, record_message, apply_proposal

ROOT = Path(__file__).resolve().parents[1]
ASSETS = {"/": ("index.html", "text/html; charset=utf-8"),
          "/app.js": ("app.js", "text/javascript; charset=utf-8"),
          "/pending.js": ("pending.js", "text/javascript; charset=utf-8"),
          "/style.css": ("style.css", "text/css; charset=utf-8")}


class WebError(ValueError):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


def difference(before, after, path=""):
    if isinstance(before, dict) and isinstance(after, dict):
        return [row for key in sorted(before.keys() | after.keys())
                for row in difference(before.get(key), after.get(key), path + "/" + key)]
    if before == after:
        return []
    return [{"path": path, "before": before, "after": after}]


def redact(value, keys):
    if isinstance(value, str):
        for key in keys:
            if key:
                value = value.replace(key, "[REDACTED]")
        return value
    if isinstance(value, list):
        return [redact(item, keys) for item in value]
    if isinstance(value, dict):
        return {key: redact(item, keys) for key, item in value.items()}
    return value


class Lab:
    def __init__(self, scenarios, providers, *, provider_factory=ApiProvider):
        self.scenarios = {s.id: s for s in load_scenarios(scenarios)}
        if len({p["name"] for p in providers}) != len(providers) or any(p["name"] == "offline-demo" for p in providers):
            raise ValueError("Provider names must be unique and cannot be offline-demo")
        self.providers = {p["name"]: p for p in providers}
        self.provider_factory = provider_factory
        self.sessions = {}
        self.guard = threading.RLock()
        self.prompt = PROMPT_PATH.read_text(encoding="utf-8")
        self.schema = output_schema()

    def bootstrap(self):
        return {"providers": [{"name": p["name"], "model": p["model"], "protocol": p["protocol"],
                    "base_url": p.get("base_url"), "key_configured": bool(p.get("api_key")),
                    "prices": p.get("prices", {})} for p in self.providers.values()],
                "scenarios": [{"id": s.id, "description": s.description, "turns": len(s.turns)}
                              for s in self.scenarios.values()]}

    def _session(self, sid):
        with self.guard:
            if not isinstance(sid, str) or sid not in self.sessions:
                raise WebError("Session not found. The server may have restarted.", 404)
            return self.sessions[sid]

    def create(self, scenario=None):
        if scenario is not None and scenario not in self.scenarios:
            raise WebError("Unknown scenario")
        case = self.scenarios.get(scenario)
        sid = str(uuid.uuid4())
        session = {"id": sid, "scenario": scenario, "step": 0,
                   "state": copy.deepcopy(case.initial_state) if case else empty_state(),
                   "events": [], "lock": threading.RLock(), "demo_valid": True}
        with self.guard:
            if len(self.sessions) >= 100:
                raise WebError("Session limit reached. Export your work and restart the local server.", 409)
            self.sessions[sid] = session
        return self.view(sid)

    def view(self, sid):
        s = self._session(sid)
        with s["lock"]:
            case = self.scenarios.get(s["scenario"])
            step = case.turns[s["step"]] if case and s["demo_valid"] and s["step"] < len(case.turns) else None
            return copy.deepcopy({"id": sid, "scenario": s["scenario"], "step": s["step"],
                "state": s["state"], "events": s["events"], "context": step["context"] if step else {},
                "next_demo": step["input"] if step else None})

    def fork(self, sid):
        source = self._session(sid)
        with source["lock"]:
            created = self.create(source["scenario"])
            target = self._session(created["id"])
            for key in ("state", "events", "step", "demo_valid"):
                target[key] = copy.deepcopy(source[key])
            return self.view(target["id"])

    def context(self, state, context):
        if not isinstance(context, dict) or set(context) - {"company_id", "project_id", "task_id", "action_id"}:
            raise WebError("Invalid context")
        result = {key: value for key, value in context.items() if value is not None}
        for table, field in reversed(list(zip(TABLES, ("company_id", "project_id", "task_id", "action_id")))):
            if field not in result:
                continue
            key = result[field]
            if not isinstance(key, str) or key not in state[table]:
                raise WebError("Unknown context entity: " + field)
            if table in PARENTS:
                parent_field, _ = PARENTS[table]
                parent_id = state[table][key][parent_field]
                if parent_field in result and result[parent_field] != parent_id:
                    raise WebError("Context entities belong to different branches")
                result[parent_field] = parent_id
        return result

    def turn(self, body):
        s = self._session(body.get("session_id"))
        if not s["lock"].acquire(blocking=False):
            raise WebError("A request is already running for this session", 409)
        try:
            return self._turn(s, body)
        finally:
            s["lock"].release()

    def _turn(self, s, body):
        if "api_key" in body:
            raise WebError("API keys must be configured in providers.local.yaml")
        text = body.get("text")
        if not isinstance(text, str) or not text.strip() or len(text) > 30_000:
            raise WebError("Enter a message of 1–30000 characters")
        mid = body.get("message_id") or str(uuid.uuid4())
        if not isinstance(mid, str) or not mid.strip() or len(mid) > 200:
            raise WebError("Invalid message id")
        if mid in s["state"]["processed"]:
            if s["state"]["messages"][mid]["text"] != text:
                raise WebError("Message id already used with different text")
            step = self.view(s["id"])["next_demo"]
            if body.get("provider") == "offline-demo" and step and step["id"] == mid:
                s["step"] += 1
            return self.view(s["id"])
        context = self.context(s["state"], body.get("context", {}))
        name = body.get("provider")
        demo = name == "offline-demo"
        config = {}
        keys = []
        if demo:
            step = self.view(s["id"])["next_demo"]
            if not step or text != step["text"]:
                raise WebError("Offline demo accepts only the next scripted example. Choose a live API for free text.")
            mid = step["id"]
            context = self.context(s["state"], self.view(s["id"])["context"])
            provider = ScriptedProvider.from_scenarios([self.scenarios[s["scenario"]]])
        else:
            if name not in self.providers:
                raise WebError("Unknown provider")
            config = copy.deepcopy(self.providers[name])
            model = body.get("model")
            if model is not None:
                if not isinstance(model, str) or not model.strip() or len(model) > 200:
                    raise WebError("Invalid model")
                if model != config["model"]:
                    config["model"], config["prices"] = model, {}  # Original model's rates do not apply.
            keys = [config.get("api_key")]
            provider = self.provider_factory(config)
        message = {"id": mid, "text": text}
        before = copy.deepcopy(s["state"])
        try:
            record_message(s["state"], message)
        except StateError as error:
            raise WebError(str(error)) from None
        payload = build_input(s["state"], context, message)
        event = {"message": message, "provider": name, "model": config.get("model"),
                 "execution": provider.execution, "input": payload, "prompt": self.prompt,
                 "output_schema": self.schema, "status": "pending", "response_text": None,
                 "raw_response": None, "proposal": None, "usage": None, "latency_ms": None,
                 "cost_usd": None, "error": None, "diff": []}
        try:
            reply = provider.complete(payload, self.prompt, self.schema)
            event.update(response_text=reply.text, raw_response=reply.raw, usage=reply.usage,
                         latency_ms=reply.latency_ms)
            proposal = parse_proposal(reply.text)
            event["proposal"] = proposal.model_dump()
            s["state"] = apply_proposal(s["state"], proposal, message)
            event["applied_proposal"] = s["state"]["history"][-1]["proposal"]
            event["status"] = "applied"
            if demo:
                s["step"] += 1
            else:
                s["demo_valid"] = False
        except ProviderError as error:
            event.update(status="provider_error", error=str(error), raw_response=error.raw,
                         usage=error.usage, latency_ms=error.latency_ms)
        except (ValidationError, ValueError) as error:
            event.update(status="domain_error" if isinstance(error, StateError) else "schema_error", error=str(error))
        event["cost_usd"] = 0.0 if demo else estimate_cost(event["usage"], config.get("prices", {}))
        event["diff"] = difference({t: before[t] for t in TABLES}, {t: s["state"][t] for t in TABLES})
        event["state_after"] = copy.deepcopy(s["state"])
        # Auth credentials never enter the payload or canonical state. Only provider-originated
        # diagnostics may echo them. Never rewrite immutable user facts, IDs or message history.
        for field in ("error", "raw_response", "response_text"):
            event[field] = redact(event[field], keys)
        s["events"].append(event)
        return self.view(s["id"])


def make_server(lab, port):
    token = secrets.token_urlsafe(32)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass  # Keep message text, credentials and diagnostics out of access logs.

        def respond(self, status, value, content_type="application/json; charset=utf-8"):
            data = value if isinstance(value, bytes) else json.dumps(value, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'")
            self.end_headers()
            self.wfile.write(data)

        def check_host(self):
            expected = {f"127.0.0.1:{self.server.server_port}", f"localhost:{self.server.server_port}"}
            if self.headers.get("Host") not in expected:
                raise WebError("Invalid Host", 403)
            origin = self.headers.get("Origin")
            if origin and origin not in {"http://" + host for host in expected}:
                raise WebError("Cross-origin requests are disabled", 403)

        def do_GET(self):
            try:
                self.check_host()
                path = urlsplit(self.path).path
                if path in ASSETS:
                    filename, mime = ASSETS[path]
                    return self.respond(200, (ROOT / "web" / filename).read_bytes(), mime)
                if path == "/api/bootstrap":
                    return self.respond(200, {"token": token, **lab.bootstrap()})
                raise WebError("Not found", 404)
            except WebError as error:
                self.respond(error.status, {"error": str(error)})

        def do_POST(self):
            try:
                self.check_host()
                if not secrets.compare_digest(self.headers.get("X-Lab-Token", ""), token):
                    raise WebError("Invalid request token. Reload the page.", 403)
                if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
                    raise WebError("Expected application/json", 415)
                if self.headers.get("Transfer-Encoding"):
                    raise WebError("Chunked requests are unsupported", 400)
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 512_000:
                    raise WebError("Request body must be between 1 byte and 512 KB", 413)
                self.connection.settimeout(10)
                body = json.loads(self.rfile.read(length))
                if not isinstance(body, dict):
                    raise WebError("Expected a JSON object")
                if self.path == "/api/sessions":
                    result = lab.create(body.get("scenario"))
                elif self.path == "/api/fork":
                    result = lab.fork(body.get("session_id"))
                elif self.path == "/api/view":
                    result = lab.view(body.get("session_id"))
                elif self.path == "/api/turn":
                    result = lab.turn(body)
                else:
                    raise WebError("Not found", 404)
                self.respond(200, result)
            except WebError as error:
                self.respond(error.status, {"error": str(error)})
            except (ValueError, TypeError, KeyError) as error:
                self.respond(400, {"error": "Invalid request/configuration: " + str(error)})

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    return server


def serve(config_path, port=8765):
    config = load_config(config_path)
    lab = Lab(ROOT / "scenarios", config["providers"])
    server = make_server(lab, port)
    print(f"Interactive lab: http://127.0.0.1:{server.server_port}", flush=True)
    print("Sessions are held in memory. Export JSON before stopping the server. Ctrl+C to stop.", flush=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()
