import json
import io
import threading
import unittest
from unittest.mock import patch
from urllib.error import HTTPError
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from experience_evals.providers import ApiProvider, ProviderError, RequestGate, retry_delay, estimate_cost


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        outer = self
        self.requests = []
        self.auth_headers = []
        self.response = {}
        self.status = 200
        self.extra_headers = {}

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                outer.auth_headers.append(dict(self.headers))
                outer.requests.append((self.path, json.loads(self.rfile.read(int(self.headers["Content-Length"])))) )
                body = json.dumps(outer.response).encode()
                self.send_response(outer.status)
                self.send_header("Content-Type", "application/json")
                for key,value in outer.extra_headers.items():
                    self.send_header(key,value)
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
                            "model": "test-model", "api_key": None, "min_request_interval_seconds":0, **kwargs})

    def test_identifies_application_instead_of_default_python_user_agent(self):
        self.response={"choices":[{"message":{"content":"{}"},"finish_reason":"stop"}]}
        self.provider("chat_completions").complete({},"prompt",{})
        headers={k.lower():v for k,v in self.auth_headers[0].items()}
        self.assertTrue(headers["user-agent"].startswith("Tallyvane-Experience-Lab/"))

    def test_403_stops_further_calls_across_provider_instances(self):
        self.status=403
        self.response={"error_code":"1010","error_name":"browser_signature_banned","detail":"secret-key blocked"}
        for _ in range(2):
            with self.assertRaises(ProviderError) as caught:
                self.provider("chat_completions",api_key="secret-key").complete({},"prompt",{})
            self.assertNotIn("secret-key",str(caught.exception))
            self.assertNotIn("browser_signature_banned",str(caught.exception))
        self.assertEqual(len(self.requests),1)

    def test_rate_limit_honors_retry_after_without_repeating_request(self):
        self.status=429
        self.extra_headers={"Retry-After":"120"}
        self.response={"error":{"message":"too many requests"}}
        for _ in range(2):
            with self.assertRaises(ProviderError):
                self.provider("chat_completions").complete({},"prompt",{})
        self.assertEqual(len(self.requests),1)

    def test_cloudflare_details_are_preserved_separately_from_error_message(self):
        self.status=403
        self.response={"error_code":"1010","error_name":"browser_signature_banned"}
        with self.assertRaises(ProviderError) as caught:
            self.provider("chat_completions").complete({},"prompt",{})
        self.assertEqual(caught.exception.raw["body"]["error_code"],"1010")
        self.assertLess(len(str(caught.exception)),300)

    def test_403_blocks_even_when_error_body_times_out(self):
        class TimeoutBody(io.BytesIO):
            def read(self, *_):
                raise TimeoutError("body timed out")
        provider=self.provider("chat_completions")
        error=HTTPError(self.base,403,"Forbidden",{},TimeoutBody())
        with patch("experience_evals.providers.build_opener") as opener:
            opener.return_value.open.side_effect=error
            for _ in range(2):
                with self.assertRaises(ProviderError):
                    provider.complete({},"prompt",{})
            self.assertEqual(opener.return_value.open.call_count,1)

    def test_request_id_cannot_leak_credential(self):
        self.status=403
        self.extra_headers={"x-request-id":"request-secret-key"}
        with self.assertRaises(ProviderError) as caught:
            self.provider("chat_completions",api_key="secret-key").complete({},"prompt",{})
        self.assertNotIn("secret-key",json.dumps(caught.exception.raw))

    def test_gate_rejects_parallel_request_and_spaces_successful_calls(self):
        gate = RequestGate()
        with patch("experience_evals.providers.time.monotonic",return_value=100), patch("experience_evals.providers.time.sleep") as sleep:
            gate.enter(1)
            with self.assertRaises(ProviderError):
                gate.enter(1)
            gate.lock.release()
            gate.enter(1)
            gate.lock.release()
            sleep.assert_called_once_with(1)

    def test_retry_after_supports_http_date_and_invalid_values(self):
        with patch("experience_evals.providers.time.time",return_value=0):
            self.assertEqual(retry_delay("Thu, 01 Jan 1970 00:02:00 GMT"),120)
        self.assertEqual(retry_delay("NaN"),60)
        self.assertEqual(retry_delay(None),60)
        self.assertEqual(retry_delay("-1"),1)

    def test_chat_completion_sends_schema_and_counts_usage(self):
        self.response = {"choices": [{"message": {"content": "{}"}, "finish_reason": "stop"}],
                         "usage": {"prompt_tokens": 100, "completion_tokens": 40, "prompt_tokens_details": {"cached_tokens": 30}}}
        reply = self.provider("chat_completions").complete({"message": "x"}, "prompt", {"type": "object"})
        path, body = self.requests[0]
        self.assertEqual(path, "/chat/completions")
        self.assertTrue(body["response_format"]["json_schema"]["strict"])
        self.assertEqual(reply.usage, {"input_tokens": 100, "cached_input_tokens": 30, "output_tokens": 40})

    def test_api_key_from_config_authenticates_without_entering_payload(self):
        self.response = {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}]}
        self.provider("responses", api_key="yaml-secret").complete({}, "prompt", {})
        self.assertEqual(self.auth_headers[0]["Authorization"], "Bearer yaml-secret")
        self.assertNotIn("yaml-secret", json.dumps(self.requests))
        self.assertNotIn("yaml-secret", json.dumps(self.provider("responses", api_key="yaml-secret").config))

    def test_invalid_config_key_is_rejected_without_echoing_secret(self):
        with self.assertRaises(ValueError):
            self.provider("responses", api_key=1234)

    def test_yaml_key_uses_each_protocols_auth_header(self):
        self.response = {"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}],
                         "choices":[{"message":{"content":"{}"},"finish_reason":"stop"}],
                         "candidates":[{"content":{"parts":[{"text":"{}"}]},"finishReason":"STOP"}],
                         "content":[{"type":"text","text":"{}"}],"stop_reason":"end_turn"}
        for protocol, header, expected in (("responses","Authorization","Bearer yaml-secret"),
                  ("chat_completions","Authorization","Bearer yaml-secret"),
                  ("gemini","X-Goog-Api-Key","yaml-secret"),("anthropic","X-Api-Key","yaml-secret")):
            with self.subTest(protocol=protocol):
                self.provider(protocol, api_key="yaml-secret").complete({},"prompt",{})
                headers={k.lower():v for k,v in self.auth_headers[-1].items()}
                self.assertEqual(headers[header.lower()],expected)

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
