"""Small explicit HTTP adapters. No SDK state, automatic retries or silent fallbacks."""

from dataclasses import dataclass
import copy
import json
import math
import os
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


@dataclass
class ModelReply:
    text: str
    usage: dict | None = None
    latency_ms: float = 0.0
    raw: dict | None = None


class ProviderError(RuntimeError):
    def __init__(self, message, *, usage=None, raw=None, latency_ms=0.0):
        super().__init__(message)
        self.usage = usage
        self.raw = raw
        self.latency_ms = latency_ms


def estimate_cost(usage, prices):
    """USD estimate from provider-reported billable tokens and user-supplied per-million rates."""
    if usage is None or not {"input_tokens", "output_tokens"} <= usage.keys():
        return None
    cached = usage.get("cached_input_tokens", 0)
    written = usage.get("cache_write_input_tokens", 0)
    counts = {"input": usage["input_tokens"] - cached - written, "output": usage["output_tokens"],
              "cached_input": cached, "cache_write_input": written}
    total = 0.0
    for name, count in counts.items():
        if not isinstance(count, (int, float)) or isinstance(count, bool) or not math.isfinite(count) or count < 0:
            return None
        if count:
            rate = prices.get(name)
            if not isinstance(rate, (int, float)) or isinstance(rate, bool) or not math.isfinite(rate) or rate < 0:
                return None
            total += count * rate / 1_000_000
    return total


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ProviderError("API redirects are disabled; configure the final base_url explicitly")


class ApiProvider:
    execution = "live"

    def __init__(self, config):
        self.config = dict(config)
        self.name = self.config["name"]
        self.protocol = self.config["protocol"]
        if self.protocol not in {"chat_completions", "responses", "gemini", "anthropic"}:
            raise ValueError("Unknown API protocol: " + self.protocol)
        self.base = self.config["base_url"].rstrip("/")
        url = urlsplit(self.base)
        if url.username or url.password or url.query or url.fragment or not url.hostname:
            raise ValueError("base_url must not contain credentials, query strings or fragments")
        if url.scheme != "https" and not (url.scheme == "http" and url.hostname in {"localhost", "127.0.0.1", "::1"}):
            raise ValueError("Use HTTPS, or HTTP on loopback for a local provider")
        self.mode = self.config.get("response_mode", "prompt" if self.protocol == "anthropic" else "json_schema")
        if self.mode not in {"json_schema", "json", "prompt"}:
            raise ValueError("Unknown response_mode")
        if self.protocol == "anthropic" and self.mode != "prompt":
            raise ValueError("The Anthropic adapter uses prompt JSON mode; native schema mode is not implemented")
        if self.protocol == "gemini" and self.mode != "json_schema":
            raise ValueError("The Gemini adapter requires json_schema mode")
        self.parameters = self.config.get("parameters", {})
        reserved = {"model", "messages", "input", "system", "systemInstruction", "contents", "response_format",
                    "text", "generationConfig", "max_tokens", "max_completion_tokens", "max_output_tokens",
                    "maxOutputTokens", "responseMimeType", "responseJsonSchema", "stream", "instructions", "store", "tools"}
        if not isinstance(self.parameters, dict) or reserved.intersection(self.parameters):
            raise ValueError("parameters cannot override model, prompt, output schema or token limits")
        self.timeout = self.config.get("timeout_seconds", 90)
        self.max_tokens = self.config.get("max_output_tokens", 4096)
        if not isinstance(self.max_tokens, int) or isinstance(self.max_tokens, bool) or self.max_tokens < 1:
            raise ValueError("max_output_tokens must be a positive integer")
        if not isinstance(self.timeout, (int, float)) or not math.isfinite(self.timeout) or self.timeout <= 0:
            raise ValueError("timeout_seconds must be positive")

    def _request(self, payload, prompt, schema):
        user = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
        # Include the same logical contract in every provider's instruction text as well as native schema mode.
        system = prompt + "\nReturn JSON matching this schema:\n" + json.dumps(schema, separators=(",", ":"))
        model = self.config["model"]
        params = dict(self.parameters)
        if self.protocol == "chat_completions":
            body = {"model": model, "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
                    "max_completion_tokens": self.max_tokens, **params}
            if self.mode == "json_schema":
                body["response_format"] = {"type": "json_schema", "json_schema": {"name": "interview_changes", "strict": True, "schema": schema}}
            elif self.mode == "json":
                body["response_format"] = {"type": "json_object"}
            # Some compatible APIs (notably DeepSeek) use max_tokens instead.
            if self.config.get("token_limit_field") == "max_tokens":
                body["max_tokens"] = body.pop("max_completion_tokens")
            return "/chat/completions", body
        if self.protocol == "responses":
            body = {"model": model, "instructions": system, "input": user, "max_output_tokens": self.max_tokens, "store": False, **params}
            if self.mode == "json_schema":
                body["text"] = {"format": {"type": "json_schema", "name": "interview_changes", "strict": True, "schema": schema}}
            elif self.mode == "json":
                body["text"] = {"format": {"type": "json_object"}}
            return "/responses", body
        if self.protocol == "gemini":
            return "/models/" + quote(model, safe="-._") + ":generateContent", {
                "systemInstruction": {"parts": [{"text": system}]},
                "contents": [{"role": "user", "parts": [{"text": user}]}],
                "generationConfig": {"responseMimeType": "application/json", "responseJsonSchema": schema,
                                     "maxOutputTokens": self.max_tokens, **params}}
        return "/messages", {"model": model, "system": system, "messages": [{"role": "user", "content": user}],
                              "max_tokens": self.max_tokens, **params}

    def complete(self, payload, prompt, schema):
        key_name = self.config.get("api_key_env")
        key = os.environ.get(key_name) if key_name else None
        if key_name and not key:
            raise ProviderError("Missing environment variable: " + key_name)
        path, body = self._request(payload, prompt, schema)
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if key:
            if self.protocol == "gemini":
                headers["x-goog-api-key"] = key
            elif self.protocol == "anthropic":
                headers["x-api-key"] = key
            else:
                headers["Authorization"] = "Bearer " + key
        if self.protocol == "anthropic":
            headers["anthropic-version"] = "2023-06-01"
        request = Request(self.base + path, data=json.dumps(body).encode(), headers=headers, method="POST")
        started = time.perf_counter()
        try:
            with build_opener(NoRedirect).open(request, timeout=self.timeout) as response:
                raw_text = response.read(8_000_001).decode("utf-8")
            if len(raw_text.encode()) > 8_000_000:
                raise ProviderError("Provider response exceeds the 8 MB diagnostic limit")
            if key:
                raw_text = raw_text.replace(key, "[REDACTED]")
            raw = json.loads(raw_text)
        except HTTPError as error:
            text = error.read(20_000).decode("utf-8", errors="replace")
            if key:
                text = text.replace(key, "[REDACTED]")
            raise ProviderError(f"HTTP {error.code}: {text}", latency_ms=(time.perf_counter() - started) * 1000) from None
        except (URLError, TimeoutError, OSError, ValueError) as error:
            text = str(error).replace(key, "[REDACTED]") if key else str(error)
            raise ProviderError(text, latency_ms=(time.perf_counter() - started) * 1000) from None
        elapsed = (time.perf_counter() - started) * 1000
        if not isinstance(raw, dict):
            raise ProviderError("API response is not an object", raw=raw, latency_ms=elapsed)
        try:
            usage = self._usage(raw)
            if usage is not None and any(not isinstance(v, int) or isinstance(v, bool) or v < 0 for v in usage.values()):
                raise ValueError("Token counts must be nonnegative integers")
        except (TypeError, ValueError, AttributeError, KeyError) as error:
            raise ProviderError("Invalid provider usage: " + str(error), raw=raw, latency_ms=elapsed) from None
        try:
            text = self._text(raw)
        except (KeyError, IndexError, TypeError, AttributeError, ProviderError) as error:
            raise ProviderError(str(error), usage=usage, raw=raw, latency_ms=elapsed) from None
        return ModelReply(text, usage, elapsed, raw)

    def _usage(self, raw):
        if self.protocol == "gemini":
            usage = raw.get("usageMetadata")
            if not usage or "promptTokenCount" not in usage or "candidatesTokenCount" not in usage:
                return None
            return {"input_tokens": usage["promptTokenCount"], "cached_input_tokens": usage.get("cachedContentTokenCount", 0),
                    "output_tokens": usage["candidatesTokenCount"] + usage.get("thoughtsTokenCount", 0)}
        usage = raw.get("usage")
        if not usage:
            return None
        if self.protocol == "anthropic":
            if not {"input_tokens", "output_tokens"} <= usage.keys():
                return None
            cached, written = usage.get("cache_read_input_tokens", 0), usage.get("cache_creation_input_tokens", 0)
            return {"input_tokens": usage["input_tokens"] + cached + written, "cached_input_tokens": cached,
                    "cache_write_input_tokens": written, "output_tokens": usage["output_tokens"]}
        ikey, okey = ("input_tokens", "output_tokens") if self.protocol == "responses" else ("prompt_tokens", "completion_tokens")
        if not {ikey, okey} <= usage.keys():
            return None
        details = usage.get("input_tokens_details" if self.protocol == "responses" else "prompt_tokens_details") or {}
        return {"input_tokens": usage[ikey], "cached_input_tokens": usage.get("prompt_cache_hit_tokens", details.get("cached_tokens", 0)),
                "output_tokens": usage[okey]}

    def _text(self, raw):
        if self.protocol == "chat_completions":
            choice = raw["choices"][0]
            if choice.get("finish_reason") != "stop" or choice["message"].get("refusal"):
                raise ProviderError("Response refused, truncated or not a completed JSON answer")
            text = choice["message"]["content"]
        elif self.protocol == "responses":
            if raw.get("status") != "completed":
                raise ProviderError("Responses status is not completed: " + str(raw.get("status")))
            parts = [part for item in raw.get("output", []) if item.get("type") == "message" for part in item.get("content", [])]
            if any(part.get("type") == "refusal" for part in parts):
                raise ProviderError("Model refused the request")
            text = "".join(p["text"] for p in parts if p.get("type") == "output_text")
        elif self.protocol == "gemini":
            choice = raw["candidates"][0]
            if choice.get("finishReason") != "STOP":
                raise ProviderError("Gemini answer is blocked or truncated")
            text = "".join(p.get("text", "") for p in choice["content"]["parts"] if not p.get("thought"))
        else:
            if raw.get("stop_reason") != "end_turn":
                raise ProviderError("Anthropic answer is refused, truncated or incomplete")
            text = "".join(p["text"] for p in raw["content"] if p.get("type") == "text")
        if not isinstance(text, str) or not text.strip():
            raise ProviderError("Provider returned empty or non-text content")
        return text


class ScriptedProvider:
    """A visible offline oracle for checking the bench itself, never a model benchmark."""
    name = "offline-oracle"
    execution = "offline_oracle"
    config = {}

    def __init__(self, outputs):
        self.outputs = outputs

    @classmethod
    def from_scenarios(cls, scenarios):
        outputs = {}
        for case in scenarios:
            for turn in case.turns:
                outputs.setdefault(turn["input"]["id"], {"proposal": turn["gold_proposal"], "before": turn["gold_before"]})
        return cls(outputs)

    def complete(self, payload, prompt, schema):
        item = self.outputs[payload["message"]["id"]]
        proposal = copy.deepcopy(item["proposal"])
        # Only the offline oracle knows gold state. Its server-generated IDs must be mapped to this run's IDs.
        mapping = {}
        parents = {"projects": "company_id", "tasks": "project_id", "actions": "task_id"}
        for table in ("companies", "projects", "tasks", "actions"):
            for key, entity in item["before"][table].items():
                expected = {k: copy.deepcopy(v) for k, v in entity.items() if k != "id"}
                if table in parents:
                    field = parents[table]
                    expected[field] = mapping.get(expected[field], expected[field])
                if key in payload["state"][table]:
                    mapping[key] = key
                    continue
                matches = [actual_id for actual_id, actual in payload["state"][table].items()
                           if {k: v for k, v in actual.items() if k != "id"} == expected]
                if len(matches) != 1:
                    raise ProviderError("Offline oracle cannot align a gold-generated ID")
                mapping[key] = matches[0]
        for change in proposal["changes"]:
            for key, value in list(change.items()):
                if key.endswith("_id") and isinstance(value, str):
                    change[key] = mapping.get(value, value)
        for unresolved in proposal["unresolved_references"]:
            unresolved["candidate_ids"] = [mapping.get(key, key) for key in unresolved["candidate_ids"]]
        return ModelReply(json.dumps(proposal, ensure_ascii=False))
