"""Atomic in-memory domain reducer. No provider calls, clocks, or gold assertions."""

import copy
import math
import re
import uuid

from .contract import Deadline, Proposal, Result, Snapshot

TABLES = ("companies", "projects", "tasks", "actions")
PARENTS = {"projects": ("company_id", "companies"), "tasks": ("project_id", "projects"),
           "actions": ("task_id", "tasks")}


class StateError(ValueError):
    pass


def empty_state():
    return {"revision": 0, "companies": {}, "projects": {}, "tasks": {}, "actions": {},
            "messages": {}, "history": [], "processed": {}}


def record_message(state, message):
    if set(message) != {"id", "text"} or not all(isinstance(v, str) and v.strip() for v in message.values()):
        raise StateError("A message has nonempty id and text")
    old = state["messages"].get(message["id"])
    if old is not None and old != message:
        raise StateError("A message id cannot be reused with different text")
    state["messages"][message["id"]] = copy.deepcopy(message)


def normal(text):
    return "".join(c for c in text.casefold() if c.isalnum() or c in "+#.")


def require_supported(text, quote):
    if text is not None and (not normal(text) or normal(text) not in normal(quote)):
        raise StateError("fact is not supported verbatim by its quote: " + str(text))


def check_evidence(item, message):
    evidence = item["evidence"]
    if evidence["message_id"] != message["id"]:
        raise StateError("evidence must cite the current message")
    if not evidence["quote"].strip() or evidence["quote"] not in message["text"]:
        raise StateError("quote is not an exact substring of the current message")
    return evidence["quote"]


def metric_numbers(quote):
    numbers = {float(x.replace(",", ".")) for x in re.findall(r"(?<![\w.])-?\d+(?:[.,]\d+)?", quote)}
    # Deliberately bounded grammar, not a general natural-language number parser.
    # Requiring an introductory boundary avoids reading 'twenty two' as just 2.
    words = {"один":1,"одну":1,"два":2,"две":2,"три":3,"четыре":4,"пять":5,
             "шесть":6,"семь":7,"восемь":8,"девять":9,"десять":10,
             "one":1,"two":2,"three":3,"four":4,"five":5,"six":6,"seven":7,
             "eight":8,"nine":9,"ten":10}
    pattern = (r"(?:^|[.!?;:,]\s*|\b(?:в|за|by|in)\s+)(" + "|".join(words) +
               r")\s+(?:раз(?:а|ов)?|times|seconds?|секунд(?:а|ы|у)?)(?!\w)")
    numbers.update(words[m.group(1)] for m in re.finditer(pattern, quote.casefold()))
    return numbers


def check_metric_unit(unit, quote):
    aliases = (
        ({"x","×","times","fold","ratio"}, r"\b(?:раз(?:а|ов)?|times|fold)\b|\d\s*[x×](?!\w)"),
        ({"%","percent","percentage"}, r"%|\b(?:percent(?:age)?|процент\w*)\b"),
        ({"ms","millisecond","milliseconds"}, r"\b(?:ms|milliseconds?|миллисекунд\w*)\b"),
        ({"s","sec","second","seconds"}, r"\b(?:s|sec|seconds?|секунд\w*)\b"),
    )
    for names, pattern in aliases:
        if unit.strip().casefold() in names:
            if re.search(pattern, quote, re.IGNORECASE):
                return
            raise StateError("metric unit is not supported by its quote: " + unit)
    require_supported(unit, quote)


def check_result(result, quote):
    Result.model_validate(result)
    require_supported(result["description"], quote)
    require_supported(result["lesson"], quote)
    numbers = metric_numbers(quote)
    for metric in result["metrics"]:
        # Metric names are model-normalized labels, not verbatim quotations.
        if not normal(metric["name"]):
            raise StateError("metric name must contain a meaningful label")
        for key in ("value", "baseline", "target"):
            number = metric[key]
            if number is not None and (not math.isfinite(number) or number not in numbers):
                raise StateError("metric number is not in its quote")
        check_metric_unit(metric["unit"], quote)


def check_deadline(deadline, quote):
    Deadline.model_validate(deadline)
    for key in ("due_at", "completed_at", "reaction"):
        require_supported(deadline[key], quote)
    if deadline["status"] == "not_stated" and any(deadline[k] is not None for k in ("due_at", "completed_at", "reaction")):
        raise StateError("not_stated deadline has no asserted fields")


def validate_state(state):
    Snapshot.model_validate({k: state[k] for k in ("revision", *TABLES)})
    seen = set()
    for table in TABLES:
        for key, entity in state[table].items():
            if key in seen or key.startswith("$") or entity.get("id") != key:
                raise StateError("entity ids must be globally unique application ids")
            seen.add(key)
            if table in PARENTS:
                field, parent = PARENTS[table]
                if entity.get(field) not in state[parent]:
                    raise StateError("entity parent does not exist: " + key)
            if table == "actions":
                Result.model_validate(entity["result"])
            if table == "tasks":
                Deadline.model_validate(entity["deadline"])
    return state


def unique(values):
    result, seen = [], set()
    for value in values:
        key = normal(value)
        if not key:
            raise StateError("empty technology or approach")
        if key not in seen:
            result.append(value)
            seen.add(key)
    return result


def normalize_tag_changes(changes):
    """Normalize only explicit, source-supported caching aliases; never infer a product."""
    output = []
    for original in changes:
        change = copy.deepcopy(original)
        quote = change["evidence"]["quote"]
        match = re.search(r"\b(?:к[еэ]ширование|caching)\b", quote, re.IGNORECASE)

        def caching(value):
            return match is not None and value.casefold().strip() in {"caching", "кеширование", "кэширование"}

        op = change["operation"]
        if op in {"create_action", "add_technologies", "replace_technologies"}:
            methods = [match.group() for value in change["technologies"] if caching(value)]
            if methods:
                if op == "replace_technologies":
                    raise StateError("Caching is an approach; it cannot replace a concrete technology. Use add_approaches.")
                change["technologies"] = [value for value in change["technologies"] if not caching(value)]
                if op == "create_action":
                    change["approaches"] += methods
                else:
                    if change["technologies"]:
                        output.append(change)
                    output.append({"operation":"add_approaches", "action_id":change["action_id"],
                                   "approaches":unique(methods), "evidence":change["evidence"]})
                    continue
        if "approaches" in change:
            change["approaches"] = unique([match.group() if caching(value) else value for value in change["approaches"]])
        output.append(change)
    return output


def apply_proposal(state, proposal, message, id_factory=None):
    proposal = proposal if isinstance(proposal, Proposal) else Proposal.model_validate(proposal)
    if state["messages"].get(message["id"]) != message:
        raise StateError("record the immutable message before processing it")
    if message["id"] in state["processed"]:
        return copy.deepcopy(state)
    if proposal.base_revision != state["revision"]:
        raise StateError("stale state revision")
    validate_state(state)
    working = copy.deepcopy(state)
    id_factory = id_factory or (lambda: str(uuid.uuid4()))
    aliases = {}
    all_ids = {key for table in TABLES for key in state[table]}
    original_proposal = proposal.model_dump()
    changes = normalize_tag_changes(original_proposal["changes"])
    for change in changes:
        if change["operation"].startswith("create_"):
            ref = change["ref"]
            if ref in aliases:
                raise StateError("duplicate local ref")
            new_id = id_factory()
            if not isinstance(new_id, str) or not new_id or new_id.startswith("$") or new_id in all_ids:
                raise StateError("id allocator returned a duplicate or invalid id")
            aliases[ref] = new_id
            all_ids.add(new_id)

    def resolve(ref):
        if ref.startswith("$") and ref not in aliases:
            raise StateError("unknown local ref: " + ref)
        return aliases.get(ref, ref)

    def existing(table, ref):
        key = resolve(ref)
        if key not in working[table]:
            raise StateError("unknown " + table + " id: " + key)
        return working[table][key]

    for change in changes:
        quote = check_evidence(change, message)
        op = change["operation"]
        if op.startswith("create_"):
            kind = op.removeprefix("create_")
            table = {"company": "companies", "project": "projects", "task": "tasks", "action": "actions"}[kind]
            entity = {k: copy.deepcopy(v) for k, v in change.items() if k not in {"operation", "ref", "evidence"}}
            entity["id"] = aliases[change["ref"]]
            if table in PARENTS:
                field, _ = PARENTS[table]
                entity[field] = resolve(entity[field])
            for field in ("name", "title", "description", "reason", "responsibility"):
                if field in entity:
                    require_supported(entity[field], quote)
            for field in ("requirements", "technologies", "approaches"):
                if field in entity:
                    for text in entity[field]:
                        require_supported(text, quote)
                    entity[field] = unique(entity[field])
            if table == "actions":
                check_result(entity["result"], quote)
                entity["retracted"] = False
            if table == "tasks":
                check_deadline(entity["deadline"], quote)
            working[table][entity["id"]] = entity
        elif op in {"add_technologies", "replace_technologies", "add_approaches"}:
            action = existing("actions", change["action_id"])
            field = "approaches" if op == "add_approaches" else "technologies"
            for text in change[field]:
                require_supported(text, quote)
            old = [] if op == "replace_technologies" else action[field]
            action[field] = unique(old + change[field])
        elif op in {"set_result", "set_reason", "set_description"}:
            action = existing("actions", change["action_id"])
            field = op.removeprefix("set_")
            if field == "result":
                check_result(change[field], quote)
            else:
                require_supported(change[field], quote)
            action[field] = copy.deepcopy(change[field])
        elif op == "move_action":
            action = existing("actions", change["action_id"])
            action["task_id"] = existing("tasks", change["task_id"])["id"]
        elif op == "retract_action":
            existing("actions", change["action_id"])["retracted"] = True
        elif op == "set_deadline":
            check_deadline(change["deadline"], quote)
            existing("tasks", change["task_id"])["deadline"] = copy.deepcopy(change["deadline"])
        elif op == "set_requirements":
            table = "projects" if change["target_type"] == "project" else "tasks"
            for text in change["requirements"]:
                require_supported(text, quote)
            existing(table, change["target_id"])["requirements"] = unique(change["requirements"])

    for unresolved in proposal.unresolved_references:
        value = unresolved.model_dump()
        quote = check_evidence(value, message)
        require_supported(value["text"], quote)
        if any(candidate not in all_ids for candidate in value["candidate_ids"]):
            raise StateError("unresolved reference names an unknown candidate")
    validate_state(working)
    working["revision"] += 1
    working["processed"][message["id"]] = working["revision"]
    applied = {**original_proposal, "changes": changes}
    history = {"revision": working["revision"], "message_id": message["id"],
               "proposal": applied, "allocated_ids": aliases}
    if applied != original_proposal:
        history["original_proposal"] = original_proposal
    working["history"].append(history)
    return working
