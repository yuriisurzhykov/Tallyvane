import copy
import unittest

from experience_evals.contract import Proposal
from experience_evals.state import StateError, apply_proposal, empty_state, record_message


def evidence(text, mid="m1"):
    return {"message_id": mid, "quote": text}


def unknown_result():
    return {"status": "not_stated", "description": None, "metrics": [], "lesson": None}


def initial_state():
    state = empty_state()
    state["companies"] = {"c1": {"id": "c1", "name": "Acme"}, "c2": {"id": "c2", "name": "Beta"}}
    state["projects"] = {
        "p1": {"id": "p1", "company_id": "c1", "name": "Panel", "requirements": [], "responsibility": None},
        "p2": {"id": "p2", "company_id": "c2", "name": "Mobile", "requirements": [], "responsibility": None},
    }
    deadline = {"due_at": None, "completed_at": None, "status": "not_stated", "reaction": None}
    state["tasks"] = {
        "t1": {"id": "t1", "project_id": "p1", "title": "Login", "requirements": [], "deadline": deadline},
        "t2": {"id": "t2", "project_id": "p2", "title": "Login", "requirements": [], "deadline": copy.deepcopy(deadline)},
    }
    for aid, tid in [("a1", "t1"), ("a2", "t2")]:
        state["actions"][aid] = {"id": aid, "task_id": tid, "description": "Built login", "reason": None,
                                 "technologies": ["Kotlin"], "approaches": [], "result": unknown_result(), "retracted": False}
    return state


class StateTests(unittest.TestCase):
    def setUp(self):
        self.state = initial_state()
        self.message = {"id": "m1", "text": "Java, not Kotlin"}
        record_message(self.state, self.message)

    def proposal(self, changes, revision=0):
        return Proposal.model_validate({"base_revision": revision, "changes": changes, "unresolved_references": []})

    def correction(self, aid="a1"):
        return {"operation": "replace_technologies", "action_id": aid, "technologies": ["Java"], "evidence": evidence(self.message["text"])}

    def test_correction_keeps_ids_and_other_project_unchanged(self):
        result = apply_proposal(self.state, self.proposal([self.correction()]), self.message)
        self.assertEqual(result["actions"]["a1"]["technologies"], ["Java"])
        self.assertEqual(result["actions"]["a1"]["id"], "a1")
        self.assertEqual(result["actions"]["a2"], self.state["actions"]["a2"])
        self.assertEqual(result["revision"], 1)
        self.assertEqual(self.state["actions"]["a1"]["technologies"], ["Kotlin"])

    def test_invalid_second_change_rolls_back_entire_batch(self):
        original = copy.deepcopy(self.state)
        with self.assertRaises(StateError):
            apply_proposal(self.state, self.proposal([self.correction(), self.correction("invented-id")]), self.message)
        self.assertEqual(original, self.state)
        self.assertIn("m1", self.state["messages"])

    def test_stale_revision_is_rejected(self):
        with self.assertRaisesRegex(StateError, "revision"):
            apply_proposal(self.state, self.proposal([self.correction()], 9), self.message)

    def test_redelivery_is_idempotent(self):
        once = apply_proposal(self.state, self.proposal([self.correction()]), self.message)
        twice = apply_proposal(once, self.proposal([self.correction()]), self.message)
        self.assertEqual(once, twice)

    def test_reused_message_id_with_different_text_is_rejected(self):
        with self.assertRaises(StateError):
            record_message(self.state, {"id": "m1", "text": "different"})

    def test_fabricated_evidence_is_rejected(self):
        change = self.correction()
        change["evidence"]["quote"] = "not in the message"
        with self.assertRaisesRegex(StateError, "quote"):
            apply_proposal(self.state, self.proposal([change]), self.message)

    def test_technology_missing_from_evidence_is_rejected(self):
        change = self.correction()
        change["technologies"] = ["Redis"]
        with self.assertRaises(StateError):
            apply_proposal(self.state, self.proposal([change]), self.message)

    def test_result_array_is_not_a_valid_proposal(self):
        with self.assertRaises(ValueError):
            self.proposal([{"operation": "set_result", "action_id": "a1", "result": [], "evidence": evidence("Java")}])

    def test_unknown_result_cannot_contain_an_invented_outcome(self):
        result = unknown_result()
        result["description"] = "Improved performance"
        with self.assertRaises(ValueError):
            self.proposal([{"operation": "set_result", "action_id": "a1", "result": result, "evidence": evidence("Java")}])

    def test_new_ids_are_allocated_by_application_and_local_refs_resolve(self):
        message = {"id": "m2", "text": "Acme2 project Portal task Search Built cache Redis"}
        record_message(self.state, message)
        e = evidence(message["text"], "m2")
        changes = [
            {"operation": "create_company", "ref": "$c", "name": "Acme2", "evidence": e},
            {"operation": "create_project", "ref": "$p", "company_id": "$c", "name": "Portal", "requirements": [], "responsibility": None, "evidence": e},
            {"operation": "create_task", "ref": "$t", "project_id": "$p", "title": "Search", "requirements": [], "deadline": self.state["tasks"]["t1"]["deadline"], "evidence": e},
            {"operation": "create_action", "ref": "$a", "task_id": "$t", "description": "Built cache", "reason": None, "technologies": ["Redis"], "approaches": [], "result": unknown_result(), "evidence": e},
        ]
        ids = iter(["server-c", "server-p", "server-t", "server-a"])
        result = apply_proposal(self.state, self.proposal(changes), message, id_factory=lambda: next(ids))
        self.assertEqual(result["actions"]["server-a"]["task_id"], "server-t")
        self.assertNotIn("$a", result["actions"])
        self.assertEqual(result["actions"]["server-a"]["result"], unknown_result())

    def test_duplicate_local_ref_is_rejected(self):
        message = {"id": "m2", "text": "Acme2"}
        record_message(self.state, message)
        change = {"operation": "create_company", "ref": "$c", "name": "Acme2", "evidence": evidence("Acme2", "m2")}
        with self.assertRaises(StateError):
            apply_proposal(self.state, self.proposal([change, change]), message)

    def test_metric_number_must_be_in_its_quote(self):
        result = {"status": "achieved", "description": "Java", "metrics": [{"name": "latency", "value": 99.0, "unit": "ms", "baseline": None, "target": None, "approximate": False}], "lesson": None}
        with self.assertRaises(StateError):
            apply_proposal(self.state, self.proposal([{"operation": "set_result", "action_id": "a1", "result": result, "evidence": evidence("Java")}]), self.message)

    def test_normalized_metric_label_and_multiplier_apply_with_room_technology(self):
        text="Я добавил кеширование в Room database, таким образом, что открытие экрана ускорилось в 2 раза по прошлым показателям."
        message={"id":"m2","text":text}
        record_message(self.state,message)
        metric={"name":"Opening screen speed","value":2,"unit":"x","baseline":None,"target":None,"approximate":False}
        result={"status":"achieved","description":text,"metrics":[metric],"lesson":None}
        changes=[{"operation":"set_result","action_id":"a1","result":result,"evidence":evidence(text,"m2")},
                 {"operation":"add_technologies","action_id":"a1","technologies":["Room"],"evidence":evidence(text,"m2")}]
        actual=apply_proposal(self.state,self.proposal(changes),message)
        self.assertEqual(actual["actions"]["a1"]["result"]["metrics"],[metric])
        self.assertIn("Room",actual["actions"]["a1"]["technologies"])
        self.assertEqual(actual["history"][-1]["proposal"]["changes"][0]["evidence"]["quote"],text)

    def test_multiplier_can_be_stated_in_words(self):
        for phrase in ("в два раза быстрее","в две секунды","two times faster"):
            with self.subTest(phrase=phrase):
                from experience_evals.state import check_result
                result={"status":"achieved","description":phrase,"metrics":[{
                    "name":"Opening screen speed","value":2,"unit":"s" if "секунды" in phrase else "x",
                    "baseline":None,"target":None,"approximate":False}],"lesson":None}
                check_result(result,phrase)

    def test_normalized_metric_does_not_allow_invented_numbers_or_units(self):
        from experience_evals.state import check_result
        result={"status":"achieved","description":"Ускорилось в 2 раза","metrics":[{
            "name":"Opening screen speed","value":99,"unit":"x","baseline":None,"target":None,"approximate":False}],"lesson":None}
        with self.assertRaises(StateError):
            check_result(result,"Ускорилось в 2 раза")
        result["metrics"][0].update(value=2,unit="kg")
        with self.assertRaises(StateError):
            check_result(result,"Ускорилось в 2 раза")
        result["description"]="two times faster"
        result["metrics"][0]["unit"]="s"
        with self.assertRaises(StateError):
            check_result(result,"two times faster")

    def test_invalid_initial_action_type_is_rejected_before_any_change(self):
        self.state["actions"]["a1"]["technologies"] = "Kotlin"
        with self.assertRaises(ValueError):
            apply_proposal(self.state, self.proposal([self.correction()]), self.message)

    def test_distinct_programming_languages_are_not_collapsed(self):
        message = {"id": "m2", "text": "Also used C, C++ and C#."}
        record_message(self.state, message)
        change = {"operation": "add_technologies", "action_id": "a1", "technologies": ["C", "C++", "C#"],
                  "evidence": evidence(message["text"], "m2")}
        result = apply_proposal(self.state, self.proposal([change]), message)
        self.assertEqual(result["actions"]["a1"]["technologies"], ["Kotlin", "C", "C++", "C#"])


if __name__ == "__main__":
    unittest.main()
