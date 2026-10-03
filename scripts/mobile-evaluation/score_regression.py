"""Score a fresh, post-fix run of the now-seen holdout without replacing first-pass evidence."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from score_holdout import ROOT, frozen_score, raw_route, summarize, write_csv


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validated", type=Path, required=True)
    parser.add_argument("--protocol", type=Path, default=ROOT / "docs/evaluation-mobile-regression/protocol.json")
    parser.add_argument("--output", type=Path, default=ROOT / "docs/evaluation-mobile-regression/results.json")
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError("Choose a new output path; recorded runs are immutable")
    protocol = json.loads(args.protocol.read_text(encoding="utf-8"))
    for name, digest in protocol["sourceSha256"].items():
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, "Changed source: " + name
    dataset = ROOT / protocol["dataset"]
    assert hashlib.sha256(dataset.read_bytes()).hexdigest() == protocol["datasetSha256"]
    cases = json.loads(dataset.read_text(encoding="utf-8"))
    captured = json.loads(args.validated.read_text(encoding="utf-8"))
    assert len(captured) == len(cases) == 240
    assert [r["id"] for r in captured] == [c["id"] for c in cases]
    rows = {}
    for case, original in zip(cases, captured):
        row = {k:v for k,v in original.items() if k != "latencyMs"}
        checks = frozen_score(case, row.get("actual", {}), decimal_amounts=True)
        if "error" in row: checks = {k:False for k in case["expected"]}
        row.update(checks=checks, routeCorrect=checks["status"] and checks["module"],
                   allChecksCorrect=all(checks.values()), modelInvoked="raw" in row or "error" in row)
        row["rawRoute"] = raw_route(row) if row["modelInvoked"] else None
        row["rawRouteCorrect"] = row["rawRoute"] == {k:case["expected"][k] for k in ("status", "module")} if row["modelInvoked"] else None
        rows[case["id"]] = row
    report = dict(model=protocol["model"], runtime=protocol["runtime"],
        completedAtUtc=datetime.now(timezone.utc).isoformat(),
        datasetSha256=protocol["datasetSha256"], datasetRole=protocol["datasetRole"],
        execution=protocol["execution"], complete=True, summary=summarize(cases, rows),
        byLanguage={lang:summarize([c for c in cases if c["language"]==lang], rows) for lang in ("zh", "en")},
        byWorkflow={g:summarize([c for c in cases if c["group"]==g], rows) for g in ("memo", "finance", "schedule", "health")},
        results=list(rows.values()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    write_csv(args.output.with_suffix(".csv"), cases, rows)
    for lang, name in [("zh", "chinese-120.csv"), ("en", "english-120.csv")]:
        write_csv(args.output.parent / name, [c for c in cases if c["language"]==lang], rows)
    write_csv(args.output.parent / "failures.csv", [c for c in cases if not rows[c["id"]]["allChecksCorrect"]], rows)
    write_report(args.output.parent, report)
    print(json.dumps(report["summary"], indent=2))


def fraction(value):
    return f"{value['passed']}/{value['total']} ({100*value['rate']:.1f}%)" if value["rate"] is not None else "N/A"


def write_report(output, report):
    baseline = json.loads((ROOT / "docs/evaluation-mobile-holdout/results.json").read_text(encoding="utf-8"))
    summary = report["summary"]
    text = ["# Mobile AI post-fix regression", "",
        "This is a **fresh model run on a seen regression set**, following analysis of the original holdout failures. It is not a new holdout or a claim of unseen model accuracy. The original [first-pass report](../evaluation-mobile-holdout/REPORT.md), raw responses, expected answers and failure records remain unchanged.", "",
        "## Scope", "",
        "- Qwen2.5-1.5B-Instruct int8 ekv4096, the same pinned 1.6 GB model file.",
        "- Windows CPU, LiteRT-LM JVM 0.10.2; the APK's prompt, preflight, validation and asynchronous Unicode-safe message transport. Fresh conversation per input, one attempt, no manual answer repair.",
        "- 120 Chinese and 120 English inputs, 30 per workflow in each language. Identical frozen expectations and scoring functions; SHA-256 recorded in [protocol.json](protocol.json).",
        "- These scores measure routing and reviewable draft fields. They do not measure Android inference accuracy, UI completion or actual database writes. Title checks use predeclared keywords rather than full semantic grading.",
        "- No latency statistics are reported. Account, confirmation and persistence checks are separate in [reliability.json](reliability.json).", "",
        "## Before and after", "", "| Metric | Original first pass | Post-fix regression |", "|---|---|---|"]
    metrics = [("routing", "Routing after validation"), ("requiredFields", "Required fields"),
               ("allSpecifiedFields", "All specified fields"), ("supportedDraft", "Supported draft checks"),
               ("clarification", "Clarification handling"), ("dates", "Date checks (including expected blanks)")]
    for key, label in metrics:
        text.append(f"| {label} | {fraction(baseline['summary'][key])} | {fraction(summary[key])} |")
    text += ["", "| Language | Routing | Required fields | All specified fields |", "|---|---|---|---|"]
    for lang, name in [("zh", "Chinese"), ("en", "English")]:
        row = report["byLanguage"][lang]
        text.append(f"| {name} | {fraction(row['routing'])} | {fraction(row['requiredFields'])} | {fraction(row['allSpecifiedFields'])} |")
    text += ["", "| Workflow | Routing | All specified fields |", "|---|---|---|"]
    for name, row in report["byWorkflow"].items():
        text.append(f"| {name.title()} | {fraction(row['routing'])} | {fraction(row['allSpecifiedFields'])} |")
    text += ["", "## Reliability of this run", "",
        f"- {summary['modelCalls']} native model calls and {summary['preflight']} preflight responses; {summary['errors']} pipeline errors.",
        f"- {summary['unsafeDrafts']} unsupported inputs became drafts (a draft is not a database write).",
        f"- Raw model routing on the invoked subset: **{fraction(summary['rawModelRouting'])}**. Application rules contribute to the final scores. Its denominator differs from the original run because preflight now rejects more unsupported operations.",
        "- The four emoji inputs that interrupted the original native run are included again; failures are never silently retried.", "",
        "## Changes", "",
        "- Escape the serialized JNI JSON to ASCII, preserving Unicode code points for the native parser. Emoji and supplementary CJK characters are not removed. This narrowly scoped compatibility shim is pinned to LiteRT-LM 0.10.2; an SDK upgrade requires verifying its conversation contract. [Upstream boundary](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc).",
        "- Reject unsupported record mutations, account/calendar queries, app controls, other-device usage requests and message sending before generation. Explicit tasks or notes about those actions remain valid drafts.",
        "- Ignore explicitly negated module requests during routing; recognize more Health navigation and usage expressions.",
        "- Keep amount, date, time and account uncertainty separate; retain clear fields. Distinguish quoted names from date instructions, support hyphenated priorities and preserve AM/PM adjacent to Chinese text.",
        "- Preserve grounded file/remark metadata as notes. Accept a single redundant closing brace, while rejecting multiple JSON actions, unknown commands and ungrounded metadata.", "",
        "## Remaining failures", "", "| Input | Failed checks |", "|---|---|"]
    cases = {c["id"]:c for c in json.loads((ROOT / "backend/evaluation/mobile_holdout_v3.json").read_text(encoding="utf-8"))}
    for row in report["results"]:
        if not row["allChecksCorrect"]:
            text.append("| " + cases[row["id"]]["text"].replace("|", "\\|") + " | " + ", ".join(k for k,v in row["checks"].items() if not v) + " |")
    text += ["", "Results informed these fixes, so improvements must be confirmed on a future untouched holdout before being described as generalization. The review screen continues to allow corrections before confirmation.", "",
        "## Files and reproduction", "",
        "[All results](results.csv) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [Remaining failures](failures.csv) · [Machine-readable results](results.json) · [Frozen run protocol](protocol.json)", "",
        "```powershell", ".\\scripts\\mobile-evaluation\\run_regression.ps1 -ModelPath C:/models/mobile-qwen2.5.litertlm -Python python -JavaHome $env:JAVA_HOME -OutputDirectory .local/reproduced-regression", "```", "",
        "Use a fresh output directory. The script checks source/data hashes before inference, records interrupted inputs as failures, and never overwrites the published run. To reproduce the historical first pass, check out its freeze commit from the original report rather than using the modified production rules.", ""]
    (output / "REPORT.md").write_text("\n".join(text), encoding="utf-8")


if __name__ == "__main__": main()
