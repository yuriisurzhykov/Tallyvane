"""Readable scenario loading and independent gold-state replay."""

import copy
from dataclasses import dataclass
from pathlib import Path

import yaml

from .contract import Proposal
from .state import TABLES, apply_proposal, empty_state, record_message, validate_state


@dataclass
class Scenario:
    id: str
    description: str
    tags: list[str]
    context: dict
    initial_state: dict
    turns: list[dict]


def load_scenarios(directory):
    directory = Path(directory)
    scenarios, ids, messages = [], set(), {}
    for path in sorted(directory.glob("*.yaml")):
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or data.get("version") != 1:
            raise ValueError(f"{path}: expected scenario version 1")
        sid = data["id"]
        if not isinstance(sid, str) or not sid.strip() or sid in ids:
            raise ValueError(f"{path}: missing or duplicate scenario id")
        ids.add(sid)
        state = empty_state()
        initial = data.get("initial_state", {})
        if isinstance(initial, str):
            initial = yaml.safe_load((path.parent / initial).resolve().read_text(encoding="utf-8"))
        for table in TABLES:
            state[table] = copy.deepcopy(initial.get(table, {}))
        validate_state(state)
        scenario = Scenario(sid, data["description"], data.get("tags", []), data.get("context", {}), copy.deepcopy(state), [])
        if not data.get("turns"):
            raise ValueError(f"{path}: scenario must have turns")
        sequence = 0
        known_aliases = {}

        def resolve_gold_refs(value, key=""):
            if isinstance(value, dict):
                return {k: resolve_gold_refs(v, k) for k, v in value.items()}
            if isinstance(value, list):
                return [resolve_gold_refs(v, key) for v in value]
            if (key.endswith("_id") or key == "candidate_ids") and isinstance(value, str) and value.startswith("@"):
                ref = "$" + value[1:]
                if ref not in known_aliases:
                    raise ValueError(f"{path}: unknown gold reference {value}")
                return known_aliases[ref]
            return value

        def allocate():
            nonlocal sequence
            sequence += 1
            return f"gold_{sid}_{sequence}"

        for raw_turn in data["turns"]:
            message = raw_turn["input"]
            # Duplicate delivery is legal inside one scenario, never an oracle lookup collision across scenarios.
            if message["id"] in messages and messages[message["id"]] != sid:
                raise ValueError("Message ids must be unique across scenarios")
            messages[message["id"]] = sid
            before = copy.deepcopy(state)
            record_message(state, message)
            expected = resolve_gold_refs(raw_turn["expected"])
            proposal = Proposal.model_validate({"base_revision": state["revision"],
                                                 "changes": expected["changes"],
                                                 "unresolved_references": expected.get("unresolved_references", [])})
            state = apply_proposal(state, proposal, message, id_factory=allocate)
            for ref, entity_id in state["history"][-1]["allocated_ids"].items():
                if ref in known_aliases and known_aliases[ref] != entity_id:
                    raise ValueError("Gold local reference reused across turns: " + ref)
                known_aliases[ref] = entity_id
            scenario.turns.append({"input": copy.deepcopy(message),
                                   "context": raw_turn.get("context", scenario.context),
                                   "gold_before": before, "gold_after": copy.deepcopy(state),
                                   "gold_proposal": proposal.model_dump()})
        scenarios.append(scenario)
    if not scenarios:
        raise ValueError("No scenario YAML files found in " + str(directory))
    return scenarios
