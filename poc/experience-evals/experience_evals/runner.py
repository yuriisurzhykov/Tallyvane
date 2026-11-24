"""Isolated and chained semantic evaluation; expected answers never enter model inputs."""

import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import time

from pydantic import ValidationError

from .contract import InterviewInput, Proposal, output_schema
from .providers import ProviderError, estimate_cost
from .state import PARENTS, TABLES, StateError, apply_proposal, normal, record_message

PROMPT_PATH = Path(__file__).resolve().parents[1] / "prompts" / "extract.md"


def build_input(state, context, message):
    # Whitelist fields; gold replay histories contain expected proposals and must never reach a live model.
    return InterviewInput.model_validate({"state": {key: copy.deepcopy(state[key]) for key in ("revision", *TABLES)},
            "context": {key: context.get(key) for key in ("company_id", "project_id", "task_id", "action_id")},
            "previous_messages": [copy.deepcopy(m) for mid, m in state["messages"].items() if mid != message["id"]],
            "message": copy.deepcopy(message)}).model_dump()


def canonical_state(state, stable_ids):
    identities = {}
    for table in TABLES:
        for key, entity in state[table].items():
            if key in stable_ids:
                identities[key] = key
            else:
                field = "description" if table == "actions" else "title" if table == "tasks" else "name"
                parent = identities[entity[PARENTS[table][0]]] if table in PARENTS else "root"
                identities[key] = "new:" + table + ":" + parent + ":" + entity[field]
    result = {"revision": state["revision"]}
    for table in TABLES:
        entities = []
        for key, entity in state[table].items():
            value = copy.deepcopy(entity)
            value["id"] = identities[key]
            if table in PARENTS:
                field, _ = PARENTS[table]
                value[field] = identities[value[field]]
            for field in ("technologies", "approaches"):
                if field in value:
                    value[field] = sorted({normal(v) for v in value[field]})
            # Metrics are a set of asserted indicators; order carries no meaning.
            if table == "actions":
                value["result"]["metrics"].sort(key=lambda m: json.dumps(m, sort_keys=True))
            entities.append(value)
        result[table] = sorted(entities, key=lambda e: json.dumps(e, sort_keys=True, ensure_ascii=False))
    return result


def align_new_entities(mapping, expected, actual):
    """Bind a new gold ID once, then keep that correspondence through every later edit/move."""
    used = set(mapping.values())
    for table in TABLES:
        label = "description" if table == "actions" else "title" if table == "tasks" else "name"
        for gold_id, entity in expected[table].items():
            if gold_id in mapping:
                continue
            parent_field = PARENTS[table][0] if table in PARENTS else None
            if parent_field and entity[parent_field] not in mapping:
                continue
            matches = [key for key, value in actual[table].items()
                       if key not in used and value[label] == entity[label]
                       and (not parent_field or value[parent_field] == mapping[entity[parent_field]])]
            if len(matches) == 1:
                mapping[gold_id] = matches[0]
                used.add(matches[0])


def aligned_actual_state(actual, expected, mapping):
    inverse = {actual_id: gold_id for gold_id, actual_id in mapping.items()}
    renamed = copy.deepcopy(actual)
    for table in TABLES:
        renamed[table] = {}
        for key, entity in actual[table].items():
            value = copy.deepcopy(entity)
            value["id"] = inverse.get(key, key)
            if table in PARENTS:
                field, _ = PARENTS[table]
                value[field] = inverse.get(value[field], value[field])
            renamed[table][value["id"]] = value
    gold_ids = {key for table in TABLES for key in expected[table]}
    return canonical_state(renamed, gold_ids)


def unresolved_key(proposal, inverse=None):
    inverse = inverse or {}
    return sorted((normal(x["text"]), tuple(sorted(inverse.get(key, key) for key in x["candidate_ids"])))
                  for x in proposal["unresolved_references"])


def parse_proposal(text):
    def distinct_pairs(pairs):
        obj = {}
        for key, value in pairs:
            if key in obj:
                raise ValueError("duplicate JSON object key: " + key)
            obj[key] = value
        return obj

    return Proposal.model_validate(json.loads(text, object_pairs_hook=distinct_pairs))


def run_suite(scenarios, provider, *, mode="chained", repeats=1, prompt=None, on_turn=None):
    if mode not in {"chained", "isolated"} or not isinstance(repeats, int) or repeats < 1:
        raise ValueError("mode is chained or isolated; repeats is positive")
    prompt = prompt if prompt is not None else PROMPT_PATH.read_text(encoding="utf-8")
    schema = output_schema()
    rows = []
    started = time.perf_counter()
    for repeat in range(repeats):
        for scenario in scenarios:
            actual = copy.deepcopy(scenario.initial_state)
            alignment = {key: key for table in TABLES for key in actual[table]}
            for index, turn in enumerate(scenario.turns):
                if mode == "isolated":
                    actual = copy.deepcopy(turn["gold_before"])
                    alignment = {key: key for table in TABLES for key in actual[table]}
                message = turn["input"]
                row = {"scenario": scenario.id, "description": scenario.description, "tags": scenario.tags,
                       "repeat": repeat + 1, "turn": index + 1, "message": message, "mode": mode,
                       "passed": False, "status": "not_run", "usage": None, "cost_usd": None,
                       "latency_ms": 0.0, "model_called": False, "error": None, "raw_response": None,
                       "response_text": None}
                reply = None
                try:
                    record_message(actual, message)
                    payload = build_input(actual, turn["context"], message)
                    row["input"] = payload
                    if message["id"] in actual["processed"]:
                        # A redelivered message is a domain no-op; do not pay for another model call.
                        row["status"] = "deduplicated"
                        row["cost_usd"] = 0.0
                        expected_proposal = next(h["proposal"] for h in actual["history"] if h["message_id"] == message["id"])
                    else:
                        row["model_called"] = True
                        reply = provider.complete(payload, prompt, schema)
                        row.update(usage=reply.usage, latency_ms=reply.latency_ms, raw_response=reply.raw, response_text=reply.text)
                        proposed = parse_proposal(reply.text)
                        expected_proposal = proposed.model_dump()
                        actual = apply_proposal(actual, proposed, message)
                        row["status"] = "passed"
                    align_new_entities(alignment, turn["gold_after"], actual)
                    gold_ids = {key for table in TABLES for key in turn["gold_after"][table]}
                    expected = canonical_state(turn["gold_after"], gold_ids)
                    found = aligned_actual_state(actual, turn["gold_after"], alignment)
                    row["id_alignment"] = copy.deepcopy(alignment)
                    row["expected_state"], row["actual_state"] = expected, found
                    row["expected_unresolved"] = turn["gold_proposal"]["unresolved_references"]
                    row["proposed_changes"] = expected_proposal
                    inverse = {actual_id: gold_id for gold_id, actual_id in alignment.items()}
                    row["passed"] = expected == found and unresolved_key(expected_proposal, inverse) == unresolved_key(turn["gold_proposal"])
                    if not row["passed"]:
                        row["status"] = "semantic_failure"
                        row["error"] = "Complete state or unresolved references differ from gold"
                except ProviderError as error:
                    row.update(status="provider_error", error=str(error), usage=error.usage,
                               raw_response=error.raw, latency_ms=error.latency_ms)
                except (ValidationError, json.JSONDecodeError) as error:
                    row.update(status="schema_failure", error=str(error))
                except StateError as error:
                    row.update(status="domain_failure", error=str(error))
                except ValueError as error:
                    row.update(status="schema_failure", error=str(error))
                if row["model_called"]:
                    row["cost_usd"] = estimate_cost(row["usage"], provider.config.get("prices", {}))
                if "actual_state" not in row:
                    align_new_entities(alignment, turn["gold_after"], actual)
                    row["actual_state"] = aligned_actual_state(actual, turn["gold_after"], alignment)
                    row["expected_state"] = canonical_state(turn["gold_after"], {key for table in TABLES for key in turn["gold_after"][table]})
                rows.append(row)
                if on_turn is not None:
                    on_turn(row)
    manifest = [{"id": s.id, "description": s.description, "initial": s.initial_state,
                 "turns": [{"input": t["input"], "context": t["context"], "gold": t["gold_proposal"]} for t in s.turns]} for s in scenarios]
    implementation = b"".join((Path(__file__).parent / name).read_bytes() for name in ("contract.py", "state.py", "runner.py", "providers.py"))
    return {"version": 1, "implementation_sha256": hashlib.sha256(implementation).hexdigest(),
            "provider": provider.name, "execution": provider.execution, "mode": mode,
            "repeats": repeats, "created_at": datetime.now(timezone.utc).isoformat(),
            "duration_ms": (time.perf_counter() - started) * 1000,
            "prompt_text": prompt, "output_schema": schema,
            "prompt_sha256": hashlib.sha256(prompt.encode()).hexdigest(),
            "schema_sha256": hashlib.sha256(json.dumps(schema, sort_keys=True).encode()).hexdigest(),
            "dataset_sha256": hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest(),
            "config": {k: copy.deepcopy(v) for k, v in provider.config.items() if k not in {"api_key", "headers"}},
            "turns": rows}
