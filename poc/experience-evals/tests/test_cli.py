import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from experience_evals.__main__ import main
from experience_evals.contract import InterviewInput, output_schema

ROOT = Path(__file__).resolve().parents[1]


class CliTests(unittest.TestCase):
    def test_web_defaults_to_local_yaml(self):
        with patch("experience_evals.web.serve") as serve:
            self.assertEqual(main(["serve"]),0)
            serve.assert_called_once_with(ROOT / "providers.local.yaml",8765)

    def test_missing_local_yaml_has_setup_instructions(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(main(["serve","--config",str(Path(temp)/"providers.local.yaml")]),2)
            self.assertIn("Copy providers.example.yaml",err.getvalue())

    def test_invalid_yaml_does_not_echo_credential_line(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stderr(io.StringIO()) as err:
            config=Path(temp)/"providers.local.yaml"
            config.write_text("providers:\n  - api_key: secret-value: invalid\n")
            self.assertEqual(main(["serve","--config",str(config)]),2)
            self.assertNotIn("secret-value",err.getvalue())
            self.assertIn("Invalid provider YAML",err.getvalue())
    def test_checked_in_schemas_match_runtime_contracts(self):
        self.assertEqual(json.loads((ROOT / "contracts/input.schema.json").read_text()), InterviewInput.model_json_schema())
        self.assertEqual(json.loads((ROOT / "contracts/output.schema.json").read_text()), output_schema())

    def test_demo_produces_a_report_and_checkpoints_without_keys(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()):
            out = Path(temp) / "demo"
            status = main(["demo", "--scenario", "new_action_then_corrections", "--out", str(out)])
            self.assertEqual(status, 0)
            runs = json.loads((out / "results.json").read_text())
            self.assertEqual({r["execution"] for r in runs}, {"offline_oracle"})
            self.assertEqual(len((out / "turns.jsonl").read_text().splitlines()), 10)
            self.assertTrue((out / "report.html").is_file())

    def test_demo_refuses_to_overwrite_previous_results(self):
        with tempfile.TemporaryDirectory() as temp, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            (Path(temp) / "keep.txt").write_text("keep")
            self.assertEqual(main(["demo", "--out", temp]), 2)
            self.assertEqual((Path(temp) / "keep.txt").read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
