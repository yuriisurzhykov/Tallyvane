import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from experience_evals.providers import ApiProvider, ProviderError, estimate_cost


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        outer = self
        self.requests = []
        self.response = {}

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                outer.requests.append((self.path, json.loads(self.rfile.read(int(self.headers["Content-Length"])))) )
                body = json.dumps(outer.response).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = "http://127.0.0.1:" + str(self.server.server_port)

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def provider(self, protocol, **kwargs):
        return ApiProvider({"name": "test", "protocol": protocol, "base_url": self.base,
                            "model": "test-model", "api_key_env": None, **kwargs})

    def test_chat_completion_sends_schema_and_counts_usage(self):
        self.response = {"choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}],
                         "usage": {"prompt_tokens": 100, "completion_tokens": 40, "prompt_tokens_details": {"cached_tokens": 30}}}
        reply = self.provider("chat_completions").complete({"message": "x"}, "prompt", {"type": "object"})
        path, body = self.requests[0]
        self.assertEqual(path, "/chat/completions")
        self.assertTrue(body["response_format"]["json_schema"]["strict"])
        self.assertEqual(reply.usage, {"input_tokens": 100, "cached_input_tokens": 30, "output_tokens": 40})

    def test_responses_extracts_text_and_rejects_incomplete(self):
        self.response = {"status": "completed", "output": [{"type": "message", "content": [{"type": "output_text", "text": "{}"}]}], "usage": {"input_tokens": 5, "output_tokens": 8}}
        reply = self.provider("responses").complete({}, "prompt", {"type": "object"})
        self.assertEqual(reply.text, "{}")
        self.assertEqual(self.requests[0][0], "/responses")
        self.response["status"] = "incomplete"
        with self.assertRaises(ProviderError) as error:
            self.provider("responses").complete({}, "prompt", {})
        self.assertEqual(error.exception.usage["output_tokens"], 8)

    def test_gemini_usage_includes_thinking_and_cached_input(self):
        self.response = {"candidates": [{"content": {"parts": [{"text": "{}"}]}, "finishReason": "STOP"}],
                         "usageMetadata": {"promptTokenCount": 100, "candidatesTokenCount": 20, "thoughtsTokenCount": 30, "cachedContentTokenCount": 10}}
        reply = self.provider("gemini").complete({}, "prompt", {"type": "object"})
        self.assertEqual(reply.usage, {"input_tokens": 100, "cached_input_tokens": 10, "output_tokens": 50})
        self.assertEqual(self.requests[0][0], "/models/test-model:generateContent")

    def test_anthropic_usage_counts_cache_creation_separately(self):
        self.response = {"content": [{"type": "text", "text": "{}"}], "stop_reason": "end_turn",
                         "usage": {"input_tokens": 10, "cache_read_input_tokens": 20, "cache_creation_input_tokens": 30, "output_tokens": 4}}
        reply = self.provider("anthropic").complete({}, "prompt", {"type": "object"})
        self.assertEqual(reply.usage["input_tokens"], 60)
        self.assertEqual(reply.usage["cache_write_input_tokens"], 30)

    def test_truncated_chat_response_is_not_accepted(self):
        self.response = {"choices": [{"message": {"content": "{}"}, "finish_reason": "length"}], "usage": {"prompt_tokens": 2, "completion_tokens": 3}}
        with self.assertRaises(ProviderError):
            self.provider("chat_completions").complete({}, "prompt", {})

    def test_missing_usage_is_unknown_not_zero(self):
        self.assertIsNone(estimate_cost(None, {"input": 1, "output": 2}))
        self.assertIsNone(estimate_cost({"input_tokens": 20, "output_tokens": 5}, {}))

    def test_cached_input_is_not_double_charged(self):
        cost = estimate_cost({"input_tokens": 1000, "cached_input_tokens": 400, "output_tokens": 100},
                             {"input": 1, "cached_input": 0.1, "output": 2})
        self.assertAlmostEqual(cost, 0.00084)

    def test_reserved_request_fields_cannot_override_contract(self):
        with self.assertRaises(ValueError):
            self.provider("chat_completions", parameters={"messages": []})

    def test_responses_instructions_cannot_override_the_measured_prompt(self):
        with self.assertRaises(ValueError):
            self.provider("responses", parameters={"instructions": "different prompt"})

    def test_non_object_api_response_is_a_reportable_provider_failure(self):
        self.response = ["not an API object"]
        with self.assertRaises(ProviderError):
            self.provider("responses").complete({}, "prompt", {})

    def test_malformed_usage_is_a_reportable_provider_failure(self):
        self.response = {"candidates": [{"content": {"parts": [{"text": "{}"}]}, "finishReason": "STOP"}],
                         "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": None}}
        with self.assertRaises(ProviderError):
            self.provider("gemini").complete({}, "prompt", {})

    def test_null_choice_preserves_usage_as_provider_failure(self):
        self.response = {"choices": [None], "usage": {"prompt_tokens": 1, "completion_tokens": 2}}
        with self.assertRaises(ProviderError) as error:
            self.provider("chat_completions").complete({}, "prompt", {})
        self.assertEqual(error.exception.usage["output_tokens"], 2)


if __name__ == "__main__":
    unittest.main()
