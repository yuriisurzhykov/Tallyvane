import tempfile
from pathlib import Path
import unittest

from experience_evals.report import summarize, write_reports


class ReportTests(unittest.TestCase):
    def run_data(self):
        return {"provider": "candidate", "execution": "live", "mode": "chained", "repeats": 1,
                "prompt_sha256": "p", "dataset_sha256": "d", "schema_sha256": "s", "created_at": "now", "config": {},
                "turns": [{"scenario": "test", "repeat": 1, "turn": 1, "passed": True, "status": "passed", "message": {"text": "</script><script>bad()</script>"},
                           "model_called": True, "latency_ms": 20, "cost_usd": .01, "usage": {"input_tokens": 5, "output_tokens": 6}},
                          {"scenario": "test", "repeat": 1, "turn": 2, "passed": False, "status": "provider_error", "message": {"text": "next"},
                           "model_called": True, "latency_ms": 50, "cost_usd": None, "usage": None}]}

    def test_unknown_failed_call_cost_does_not_become_zero(self):
        summary = summarize(self.run_data())
        self.assertIsNone(summary["total_cost_usd"])
        self.assertEqual(summary["known_cost_subtotal_usd"], .01)
        self.assertEqual(summary["dialogues_passed"], 0)
        self.assertEqual(summary["turns_passed"], 1)

    def test_report_escapes_model_and_user_text_and_creates_three_formats(self):
        with tempfile.TemporaryDirectory() as folder:
            write_reports([self.run_data()], Path(folder))
            text = (Path(folder) / "report.html").read_text()
            self.assertNotIn("<script>bad()", text)
            self.assertIn("&lt;script&gt;", text)
            self.assertTrue((Path(folder) / "results.json").is_file())
            self.assertTrue((Path(folder) / "summary.csv").is_file())

    def test_oracle_is_explicitly_offline_and_not_a_model_quality_result(self):
        run = self.run_data()
        run["execution"] = "offline_oracle"
        summary = summarize(run)
        self.assertEqual(summary["api_calls"], 0)
        self.assertFalse(summary["eligible_for_model_comparison"])


if __name__ == "__main__":
    unittest.main()
