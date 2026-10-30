"""Run from this standalone directory: python -m experience_evals --help."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import sys

import yaml

from .contract import InterviewInput, output_schema
from .dataset import load_scenarios
from .providers import ApiProvider, ScriptedProvider
from .report import write_reports
from .runner import PROMPT_PATH, run_suite

ROOT = Path(__file__).resolve().parents[1]


def main(argv=None):
    parser = argparse.ArgumentParser(description="Evaluate interview extraction and updates across API models.")
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("validate", "demo", "run"):
        command = commands.add_parser(name)
        command.add_argument("--scenarios", type=Path, default=ROOT / "scenarios")
        command.add_argument("--scenario", action="append", help="Only this scenario id; repeat to select several")
        if name != "validate":
            command.add_argument("--mode", choices=["isolated", "chained", "both"], default="both")
            command.add_argument("--repeats", type=int, default=1 if name == "demo" else 3)
            command.add_argument("--out", type=Path, default=ROOT / "results" / (name + "-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")))
            command.add_argument("--prompt", type=Path, default=PROMPT_PATH)
        if name == "run":
            command.add_argument("--config", type=Path, required=True)
            command.add_argument("--provider", action="append", required=True, help="Configured provider name; repeat to compare")
    schema = commands.add_parser("schema")
    schema.add_argument("--out", type=Path)
    schema.add_argument("--kind", choices=["input", "output"], default="output")
    report = commands.add_parser("report")
    report.add_argument("inputs", nargs="+", type=Path, help="results.json files to combine")
    report.add_argument("--out", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == "schema":
            value = InterviewInput.model_json_schema() if args.kind == "input" else output_schema()
            text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
            if args.out:
                args.out.write_text(text, encoding="utf-8")
            else:
                print(text)
            return 0
        if args.command == "report":
            runs = [run for path in args.inputs for run in json.loads(path.read_text(encoding="utf-8"))]
            print(json.dumps(write_reports(runs, args.out), ensure_ascii=False, indent=2))
            return 0
        cases = load_scenarios(args.scenarios)
        if args.scenario:
            requested = set(args.scenario)
            if requested - {c.id for c in cases}:
                raise ValueError("Unknown scenario ids: " + ", ".join(sorted(requested - {c.id for c in cases})))
            cases = [c for c in cases if c.id in requested]
        if args.command == "validate":
            print(f"Validated {len(cases)} scenarios, {sum(len(c.turns) for c in cases)} turns and all gold-state transitions.")
            return 0
        if args.repeats < 1:
            raise ValueError("--repeats must be positive")
        if args.out.exists() and any(args.out.iterdir()):
            raise ValueError("Output directory must be empty; choose a new --out to preserve previous runs")
        if args.command == "demo":
            providers = [ScriptedProvider.from_scenarios(cases)]
        else:
            config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
            entries = config["providers"]
            if len({p["name"] for p in entries}) != len(entries):
                raise ValueError("Provider names must be unique")
            selected = {p["name"]: p for p in entries if p["name"] in args.provider}
            if set(args.provider) - selected.keys():
                raise ValueError("Unknown provider name")
            providers = [ApiProvider(selected[name]) for name in dict.fromkeys(args.provider)]
            for provider in providers:
                key = provider.config.get("api_key_env")
                if key and not os.environ.get(key):
                    raise ValueError("Missing environment variable: " + key)
        modes = ["isolated", "chained"] if args.mode == "both" else [args.mode]
        logical_calls = len(providers) * len(modes) * args.repeats * sum(len(c.turns) for c in cases)
        print(f"{len(cases)} scenarios; up to {logical_calls} model calls. Duplicate deliveries are local no-ops.")
        print("OFFLINE ORACLE ONLY" if args.command == "demo" else "LIVE API RUN: requests can incur provider charges.")
        args.out.mkdir(parents=True, exist_ok=True)
        runs = []
        prompt = args.prompt.read_text(encoding="utf-8")
        for provider in providers:
            for mode in modes:
                print(f"Running {provider.name}, {mode}, repetitions={args.repeats} ...", flush=True)
                def checkpoint(row):
                    with (args.out / "turns.jsonl").open("a", encoding="utf-8") as file:
                        file.write(json.dumps({"provider": provider.name, "execution": provider.execution, **row}, ensure_ascii=False) + "\n")
                run = run_suite(cases, provider, mode=mode, repeats=args.repeats, prompt=prompt, on_turn=checkpoint)
                runs.append(run)
                summaries = write_reports(runs, args.out)
                last = summaries[-1]
                print(f"Dialogs passed: {last['dialogues_passed']}/{last['dialogues_total']}; report: {args.out / 'report.html'}", flush=True)
        return 0 if all(row["passed"] for run in runs for row in run["turns"]) else 1
    except KeyboardInterrupt:
        print("Interrupted. Completed turn diagnostics remain in the output turns.jsonl.", file=sys.stderr)
        return 130
    except (ValueError, KeyError, TypeError, OSError) as error:
        print(f"Configuration/data error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
