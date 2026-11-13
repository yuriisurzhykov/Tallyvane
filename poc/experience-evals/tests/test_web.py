import copy
import json
from pathlib import Path
import unittest
import threading
from urllib.request import Request, urlopen
from urllib.error import HTTPError

from experience_evals.providers import ModelReply, ProviderError
from experience_evals.web import Lab, WebError, make_server

ROOT = Path(__file__).resolve().parents[1]


class WebLabTests(unittest.TestCase):
    def setUp(self):
        self.lab = Lab(ROOT / "scenarios", [])

    def test_guided_steps_keep_ids_and_show_changes(self):
        session = self.lab.create("new_action_then_corrections")
        before = copy.deepcopy(session["state"])
        for _ in range(5):
            step = session["next_demo"]
            session = self.lab.turn({"session_id": session["id"], "provider": "offline-demo",
                                     "text": step["text"], "message_id": step["id"]})
            self.assertEqual(session["events"][-1]["status"], "applied")
        self.assertEqual(session["state"]["revision"], 5)
        self.assertIsNone(session["next_demo"])
        self.assertGreater(len(session["state"]["actions"]), len(before["actions"]))
        event = session["events"][-1]
        self.assertTrue(event["diff"])
        self.assertIn("previous_messages", event["input"])
        self.assertEqual(event["execution"], "offline_oracle")

    def test_offline_does_not_accept_arbitrary_text(self):
        s = self.lab.create("create_hierarchy")
        with self.assertRaises(WebError):
            self.lab.turn({"session_id": s["id"], "provider": "offline-demo", "text": "anything"})
        self.assertEqual(self.lab.view(s["id"])["state"]["messages"], {})

    def test_fork_is_independent(self):
        original = self.lab.create("create_hierarchy")
        fork = self.lab.fork(original["id"])
        step = fork["next_demo"]
        fork = self.lab.turn({"session_id": fork["id"], "provider": "offline-demo", "text": step["text"]})
        self.assertEqual(fork["state"]["revision"], 1)
        self.assertEqual(self.lab.view(original["id"])["state"]["revision"], 0)

    def test_live_failure_is_atomic_and_secret_is_redacted(self):
        class Failed:
            execution = "live"
            config = {}
            def complete(self, payload, prompt, schema):
                raise ProviderError("bad private-key", raw={"error": "private-key"})
        lab = Lab(ROOT / "scenarios", [{"name": "test", "model": "test", "protocol": "responses", "api_key":"private-key"}],
                  provider_factory=lambda config: Failed())
        s = lab.create()
        s = lab.turn({"session_id": s["id"], "provider": "test", "text": "Worked at Helix"})
        self.assertEqual(s["state"]["revision"], 0)
        self.assertEqual(len(s["state"]["messages"]), 1)
        self.assertEqual(s["events"][-1]["status"], "provider_error")
        self.assertNotIn("private-key", json.dumps(s))
        self.assertNotIn("private-key", json.dumps(lab.bootstrap()))
        self.assertTrue(lab.bootstrap()["providers"][0]["key_configured"])

    def test_redelivery_does_not_call_provider_twice(self):
        s = self.lab.create("create_hierarchy")
        step = s["next_demo"]
        body = {"session_id": s["id"], "provider": "offline-demo", "text": step["text"], "message_id": step["id"]}
        first = self.lab.turn(body)
        second = self.lab.turn(body)
        self.assertEqual(first, second)

    def test_live_redelivery_deduplicates_before_old_context_validation(self):
        calls = []
        proposal = self.lab.scenarios["move_action"].turns[0]["gold_proposal"]
        class Replies:
            execution = "live"
            config = {}
            def complete(self, *_):
                calls.append(1)
                return ModelReply(json.dumps(proposal))
        lab = Lab(ROOT / "scenarios", [{"name":"test","model":"test","protocol":"responses","api_key":"1"}],
                  provider_factory=lambda *a, **k: Replies())
        s = lab.create("move_action")
        action = s["state"]["actions"]["a1"]
        body = {"session_id":s["id"],"provider":"test","text":s["next_demo"]["text"],
                "message_id":s["next_demo"]["id"],"context":{
                    "action_id":"a1","task_id":action["task_id"]}}
        first = lab.turn(body)
        second = lab.turn(body)
        self.assertEqual(first, second)
        self.assertEqual(len(calls),1)

    def test_invalid_context_is_rejected_before_call(self):
        s = self.lab.create()
        with self.assertRaises(WebError):
            self.lab.turn({"session_id": s["id"], "provider": "offline-demo", "text": "x",
                           "context": {"action_id": "missing"}})

    def test_numeric_api_key_does_not_corrupt_json_numbers(self):
        class Replies:
            execution = "live"
            config = {}
            def complete(self, *_):
                return ModelReply('{"base_revision":0,"changes":[],"unresolved_references":[]}',
                                  usage={"input_tokens": 1, "output_tokens": 1})
        lab = Lab(ROOT / "scenarios", [{"name":"test","model":"test","protocol":"responses","api_key":"1"}],
                  provider_factory=lambda *a, **k: Replies())
        s = lab.create()
        s = lab.turn({"session_id":s["id"],"provider":"test","text":"hello"})
        self.assertEqual(s["events"][-1]["usage"]["input_tokens"],1)
        self.assertEqual(s["state"]["revision"],1)

    def test_browser_cannot_override_configured_key(self):
        s = self.lab.create()
        with self.assertRaises(WebError):
            self.lab.turn({"session_id":s["id"],"provider":"offline-demo","text":"hello","api_key":"override"})
        self.assertEqual(self.lab.view(s["id"])["state"]["messages"],{})

    def test_concurrent_turn_is_rejected(self):
        entered, release = threading.Event(), threading.Event()
        class Slow:
            config = {}
            execution = "live"
            def complete(self, *_):
                entered.set()
                release.wait(5)
                return ModelReply('{"base_revision":0,"changes":[],"unresolved_references":[]}')
        lab = Lab(ROOT / "scenarios", [{"name":"slow","model":"slow","protocol":"responses"}],
                  provider_factory=lambda *a, **k: Slow())
        s = lab.create()
        body = {"session_id":s["id"],"provider":"slow","text":"hello"}
        thread = threading.Thread(target=lambda: lab.turn(body))
        thread.start()
        self.assertTrue(entered.wait(2))
        try:
            with self.assertRaises(WebError) as caught:
                lab.turn(body)
            self.assertEqual(caught.exception.status, 409)
        finally:
            release.set()
            thread.join(5)
        self.assertEqual(lab.view(s["id"])["state"]["revision"], 1)

    def test_http_flow_requires_token_and_same_origin(self):
        server = make_server(self.lab, 0)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        base = f"http://127.0.0.1:{server.server_port}"
        try:
            with urlopen(base + "/") as response:
                self.assertIn("Experience Lab", response.read().decode())
                self.assertIn("frame-ancestors 'none'", response.headers["Content-Security-Policy"])
            with urlopen(base + "/api/bootstrap") as response:
                token = json.load(response)["token"]
            def post(value, token_value=token, origin=base):
                return urlopen(Request(base + "/api/sessions", data=json.dumps(value).encode(),
                        headers={"Content-Type":"application/json","X-Lab-Token":token_value,"Origin":origin}))
            for wrong_token, origin in (("wrong",base),(token,"https://evil.example")):
                with self.assertRaises(HTTPError) as caught:
                    post({}, wrong_token, origin)
                self.assertEqual(caught.exception.code,403)
            with post({"scenario":"create_hierarchy"}) as response:
                self.assertEqual(json.load(response)["state"]["revision"],0)
            with self.assertRaises(HTTPError) as caught:
                post([])
            self.assertEqual(caught.exception.code,400)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(3)

    def test_duplicate_scripted_step_finishes_without_model_call(self):
        s = self.lab.create("duplicate_delivery")
        while s["next_demo"]:
            step = s["next_demo"]
            old_step = s["step"]
            s = self.lab.turn({"session_id":s["id"],"provider":"offline-demo","text":step["text"],"message_id":step["id"]})
            self.assertGreater(s["step"],old_step)


if __name__ == "__main__":
    unittest.main()
