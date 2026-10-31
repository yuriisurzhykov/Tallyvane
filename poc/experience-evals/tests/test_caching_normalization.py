import copy
import unittest
from experience_evals.state import apply_proposal, record_message, StateError
from test_state import initial_state, unknown_result


class CachingNormalizationTests(unittest.TestCase):
    def apply(self, technologies, operation="add_technologies", text="Я добавил кеширование, и время открытия уменьшилось практически в 2 раза"):
        state=initial_state()
        message={"id":"m1","text":text}
        record_message(state,message)
        e={"message_id":"m1","quote":text}
        changes=[{"operation":operation,"action_id":"a1","technologies":technologies,"evidence":e}]
        if operation=="add_technologies" and "2" in text:
            changes.append({"operation":"set_result","action_id":"a1","evidence":e,"result":{
                "status":"achieved","description":text,"lesson":None,"metrics":[{
                    "name":"speedup","value":2,"unit":"x","baseline":None,"target":None,"approximate":True}]}})
        proposal={"base_revision":0,"changes":changes,"unresolved_references":[]}
        return apply_proposal(state,proposal,message),proposal

    def test_reported_response_is_normalized_with_audit(self):
        actual,proposal=self.apply(["Caching"])
        self.assertEqual(actual["actions"]["a1"]["technologies"],["Kotlin"])
        self.assertEqual(actual["actions"]["a1"]["approaches"],["кеширование"])
        self.assertEqual(actual["actions"]["a1"]["result"]["metrics"][0]["value"],2)
        self.assertTrue(actual["actions"]["a1"]["result"]["metrics"][0]["approximate"])
        self.assertEqual(actual["history"][-1]["original_proposal"],proposal)
        self.assertEqual(actual["history"][-1]["proposal"]["changes"][0]["operation"],"add_approaches")

    def test_unsupported_alias_and_destructive_replacement_are_rejected(self):
        for op,text in (("add_technologies","Used Room"),("replace_technologies","Вместо Kotlin использовал кеширование")):
            with self.subTest(op=op), self.assertRaises(StateError):
                self.apply(["Caching"],op,text)

    def test_new_action_preserves_room_and_deduplicates_caching(self):
        state=initial_state()
        text="Добавил кеширование на Room"
        message={"id":"m1","text":text}
        record_message(state,message)
        change={"operation":"create_action","ref":"$cache","task_id":"t1","description":"Добавил кеширование",
                "reason":None,"technologies":["Room","Caching"],"approaches":["Caching"],"result":unknown_result(),
                "evidence":{"message_id":"m1","quote":text}}
        actual=apply_proposal(state,{"base_revision":0,"changes":[change],"unresolved_references":[]},message,id_factory=lambda:"new-action")
        self.assertEqual(actual["actions"]["new-action"]["technologies"],["Room"])
        self.assertEqual(actual["actions"]["new-action"]["approaches"],["кеширование"])
