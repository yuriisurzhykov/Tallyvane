import copy
import json
from pathlib import Path
import tempfile
import unittest

import yaml

from experience_evals.dataset import load_scenarios
from experience_evals.runner import build_input, canonical_state, run_suite
from experience_evals.providers import ModelReply, ScriptedProvider
from test_state import initial_state


ROOT = Path(__file__).resolve().parents[1]


class RunnerTests(unittest.TestCase):
    def extended_creation_case(self, text, expected):
        case = yaml.safe_load((ROOT / "scenarios/create_hierarchy.yaml").read_text())
        case["turns"].append({"input": {"id": "creation_followup", "text": text}, "expected": expected})
        with tempfile.TemporaryDirectory() as folder:
            (Path(folder) / "case.yaml").write_text(yaml.safe_dump(case, allow_unicode=True))
            return load_scenarios(folder)

    def test_swapping_two_created_action_ids_is_detected_on_later_turn(self):
        text = "Названия Реализовал экран и Добавил тесты подтверждаю, ничего не меняй."
        cases = self.extended_creation_case(text, {"changes": [], "unresolved_references": []})
        oracle = ScriptedProvider.from_scenarios(cases)

        class SwapProvider:
            name = "swaps-created-identities"
            execution = "live"
            config = {}

            def complete(self, payload, prompt, schema):
                if payload["message"]["id"] != "creation_followup":
                    return oracle.complete(payload, prompt, schema)
                actions = payload["state"]["actions"]
                screen = next(a for a in actions.values() if a["description"] == "Реализовал экран")
                tests = next(a for a in actions.values() if a["description"] == "Добавил тесты")
                return ModelReply(json.dumps({"base_revision": payload["state"]["revision"], "changes": [
                    {"operation": "set_description", "action_id": screen["id"], "description": tests["description"], "evidence": {"message_id": "creation_followup", "quote": text}},
                    {"operation": "set_description", "action_id": tests["id"], "description": screen["description"], "evidence": {"message_id": "creation_followup", "quote": text}}], "unresolved_references": []}))

        for mode in ["isolated", "chained"]:
            result = run_suite(cases, SwapProvider(), mode=mode)
            self.assertTrue(result["turns"][0]["passed"])
            self.assertFalse(result["turns"][1]["passed"], mode)

    def test_unresolved_candidates_support_new_ids_and_gold_aliases(self):
        text = "Тот UI, не помню какой."
        cases = self.extended_creation_case(text, {"changes": [], "unresolved_references": [
            {"text": "Тот UI", "candidate_ids": ["@screen", "@tests"], "evidence": {"message_id": "creation_followup", "quote": text}}]})
        for mode in ["isolated", "chained"]:
            result = run_suite(cases, ScriptedProvider.from_scenarios(cases), mode=mode)
            self.assertTrue(all(t["passed"] for t in result["turns"]), mode)

    def test_gold_fixtures_all_validate_and_pass_in_both_modes(self):
        cases = load_scenarios(ROOT / "scenarios")
        self.assertGreaterEqual(len(cases), 27)
        for mode in ["isolated", "chained"]:
            result = run_suite(cases, ScriptedProvider.from_scenarios(cases), mode=mode, repeats=2)
            self.assertTrue(all(x["passed"] for x in result["turns"]), [x for x in result["turns"] if not x["passed"]])
            self.assertEqual(result["execution"], "offline_oracle")

    def test_model_input_has_no_gold_operations_or_history_of_proposals(self):
        state = initial_state()
        state["history"] = [{"proposal": {"gold_secret": "must never reach model"}}]
        payload = build_input(state, {"action_id": "a1"}, {"id": "m1", "text": "new"})
        text = json.dumps(payload)
        self.assertNotIn("gold_secret", text)
        self.assertNotIn("expected", text)
        self.assertNotIn("history", payload["state"])

    def test_missing_correction_is_detected_even_if_json_is_valid(self):
        cases = [x for x in load_scenarios(ROOT / "scenarios") if x.id == "correction_then_addition"]

        class NoChanges:
            name = "broken"
            execution = "live"
            config = {}

            def complete(self, payload, prompt, schema):
                return ModelReply(json.dumps({"base_revision": payload["state"]["revision"], "changes": [], "unresolved_references": []}))

        result = run_suite(cases, NoChanges(), mode="chained")
        self.assertTrue(all(not t["passed"] for t in result["turns"]))
        self.assertEqual(result["turns"][0]["status"], "semantic_failure")

    def test_ids_of_existing_entities_are_not_normalized_away(self):
        original = initial_state()
        changed = copy.deepcopy(original)
        changed["actions"]["a1"]["task_id"] = "t2"
        stable = {key for table in ["companies", "projects", "tasks", "actions"] for key in original[table]}
        self.assertNotEqual(canonical_state(original, stable), canonical_state(changed, stable))

    def test_new_ids_and_technology_order_do_not_change_semantic_score(self):
        a = initial_state()
        b = copy.deepcopy(a)
        stable = {key for table in ["companies", "projects", "tasks", "actions"] for key in a[table]}
        for state, aid in [(a, "uuid-one"), (b, "uuid-two")]:
            state["actions"][aid] = {**copy.deepcopy(state["actions"]["a1"]), "id": aid, "description": "New action"}
            state["actions"][aid]["technologies"] = ["Room", "Kotlin"] if state is a else ["Kotlin", "Room"]
        self.assertEqual(canonical_state(a, stable), canonical_state(b, stable))


if __name__ == "__main__":
    unittest.main()
