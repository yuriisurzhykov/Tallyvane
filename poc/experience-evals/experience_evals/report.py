"""Auditable JSON/CSV plus a self-contained, escaped HTML report."""

from collections import Counter, defaultdict
import csv
import difflib
import html
import json
from pathlib import Path
import statistics


def summarize(run):
    rows = run["turns"]
    groups = defaultdict(list)
    for row in rows:
        groups[(row["scenario"], row["repeat"])].append(row)
    called = [r for r in rows if r["model_called"]]
    known = [r["cost_usd"] for r in called if r["cost_usd"] is not None]
    latency = sorted(r["latency_ms"] for r in called)
    live = run["execution"] == "live"
    statuses = Counter(r["status"] for r in rows)
    return {"provider": run["provider"], "execution": run["execution"], "mode": run["mode"],
            "repeats": run["repeats"], "dialogues_passed": sum(all(r["passed"] for r in rs) for rs in groups.values()),
            "dialogues_total": len(groups), "turns_passed": sum(r["passed"] for r in rows), "turns_total": len(rows),
            "api_calls": len(called) if live else 0,
            "schema_failures": statuses["schema_failure"], "domain_failures": statuses["domain_failure"],
            "semantic_failures": statuses["semantic_failure"], "provider_errors": statuses["provider_error"],
            "latency_p50_ms": statistics.median(latency) if latency and live else None,
            "latency_p95_ms": latency[min(len(latency) - 1, int((len(latency) - 1) * .95))] if latency and live else None,
            "total_cost_usd": sum(known) if live and len(known) == len(called) else None,
            "known_cost_subtotal_usd": sum(known) if live else None,
            "calls_with_unknown_cost": len(called) - len(known) if live else 0,
            "eligible_for_model_comparison": live,
            "input_tokens": sum(r["usage"].get("input_tokens", 0) for r in called if r["usage"]),
            "output_tokens": sum(r["usage"].get("output_tokens", 0) for r in called if r["usage"]),
            "calls_with_missing_usage": sum(r["usage"] is None for r in called) if live else 0}


def pretty(value):
    return html.escape(json.dumps(value, ensure_ascii=False, indent=2))


def write_reports(runs, output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / "results.json").write_text(json.dumps(runs, ensure_ascii=False, indent=2), encoding="utf-8")
    summaries = [summarize(run) for run in runs]
    with (output / "summary.csv").open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(summaries[0]) if summaries else ["provider"])
        writer.writeheader()
        writer.writerows(summaries)
    content = []
    escape = lambda value: html.escape(str(value))
    for run, summary in zip(runs, summaries):
        live = run["execution"] == "live"
        label = "LIVE API EVALUATION" if live else "OFFLINE ORACLE: checks the bench, not model quality"
        total_cost = summary["total_cost_usd"]
        money = f"${total_cost:.6f}" if total_cost is not None else "Unknown / not applicable"
        content.append(f'<section><h2>{escape(run["provider"])} · {escape(run["mode"])}</h2><p class="badge">{label}</p>'
                       f'<p>Dialogs: {summary["dialogues_passed"]}/{summary["dialogues_total"]}; turns: {summary["turns_passed"]}/{summary["turns_total"]}; '
                       f'API calls: {summary["api_calls"]}; estimated total cost: {money}.</p>'
                       f'<p>Cost uses configured prices and provider usage. Unknown-cost calls: {summary["calls_with_unknown_cost"]}. '
                       'Reasoning tokens count when included in provider output usage. Cached input is priced separately.</p>'
                       f'<details><summary>Run configuration and fingerprints</summary><pre>{pretty({k: v for k, v in run.items() if k != "turns"})}</pre></details>')
        for row in run["turns"]:
            status = "PASS" if row["passed"] else "FAIL"
            title = f'{status} · {row["scenario"]} · repetition {row["repeat"]} · turn {row["turn"]} · {row["status"]}'
            expected = json.dumps(row.get("expected_state"), ensure_ascii=False, indent=2, sort_keys=True).splitlines()
            actual = json.dumps(row.get("actual_state"), ensure_ascii=False, indent=2, sort_keys=True).splitlines()
            diff = "\n".join(difflib.unified_diff(expected, actual, fromfile="expected", tofile="actual", lineterm=""))
            content.append(f'<details class="turn"><summary>{escape(title)}</summary><p>{escape(row["message"]["text"])}</p>'
                           f'<p>{escape(row.get("error") or "No failure")}</p><h3>State difference</h3><pre>{escape(diff or "No state difference")}</pre>'
                           f'<h3>Model input</h3><pre>{pretty(row.get("input"))}</pre>'
                           f'<h3>Actual proposed changes</h3><pre>{pretty(row.get("proposed_changes"))}</pre>'
                           f'<h3>Expected unresolved references</h3><pre>{pretty(row.get("expected_unresolved"))}</pre>'
                           f'<h3>Response and usage</h3><pre>{pretty({k: row.get(k) for k in ["response_text", "raw_response", "usage", "cost_usd", "latency_ms"]})}</pre></details>')
        content.append("</section>")
    document = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tallyvane experience extraction evaluation</title><style>
:root{color-scheme:light dark;font-family:system-ui,sans-serif}body{max-width:1050px;margin:auto;padding:24px;line-height:1.5}
h1,h2,h3{font-weight:600}section{margin:30px 0 48px}.badge{font-weight:600}details{border:1px solid GrayText;border-radius:5px;padding:10px;margin:10px 0}
summary{cursor:pointer;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;padding:12px;background:light-dark(#f3f4f6,#202329)}p{overflow-wrap:anywhere}
</style><h1>Experience extraction evaluation</h1><p>Compare whole-dialog correctness before cost. Schema validity alone is not factual accuracy.
Only compare live runs with matching implementation, dataset, prompt and schema fingerprints and the same mode. This small dataset is not a production accuracy guarantee.</p>'''
    (output / "report.html").write_text(document + "".join(content) + "</html>", encoding="utf-8")
    return summaries
