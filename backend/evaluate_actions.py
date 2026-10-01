"""Local-only, field-level four-module regression evaluation. Not a universal semantic score."""
import argparse
import json
import math
import time
import hashlib
from datetime import datetime
from pathlib import Path
from action_service import create_action, ACTION_SCHEMA, ACTION_SYSTEM
from task_service import call_local_model, local_settings, ServiceError


def flatten(result):
    fields = dict(result.get("draft") or result.get("fields") or {})
    if result.get("module") == "memo":
        fields["date"] = fields.pop("dueDate", None)
    fields.update(status=result.get("status"), module=result.get("module"), reason=result.get("reason"))
    return fields


def matches(actual, expected):
    if isinstance(expected, dict):
        if "oneOf" in expected:
            return actual in expected["oneOf"]
        if "containsAny" in expected:
            return isinstance(actual, str) and any(term.casefold() in actual.casefold() for term in expected["containsAny"])
        raise ValueError("Unknown expectation")
    if isinstance(actual, str) and isinstance(expected, str):
        return actual.strip().casefold() == expected.strip().casefold()
    return actual == expected


def score(case, result):
    actual = flatten(result)
    valid_draft = result.get("status") == "draft" and isinstance(result.get("draft") or result.get("fields"), dict)
    return {key: key in actual and (key in ("status", "module", "reason") or valid_draft)
            and matches(actual[key], expected) for key, expected in case["expected"].items()}


def summarize(cases, rows):
    fields = sorted({key for case in cases for key in case["expected"]})
    accuracy = {}
    for field in fields:
        eligible = [i for i, case in enumerate(cases) if field in case["expected"]]
        correct = sum(rows[i].get("checks", {}).get(field, False) for i in eligible)
        accuracy[field] = {"correct": correct, "total": len(eligible), "rate": correct / len(eligible)}
    latencies = sorted(row["latencyMs"] for row in rows)
    return {"cases": len(cases), "fieldAccuracy": accuracy,
            "allCheckedFieldsAccuracy": sum(row["correct"] for row in rows) / len(rows),
            "errors": sum("error" in row for row in rows),
            "latencyP50Ms": latencies[math.ceil(len(rows)*.5)-1],
            "latencyP95Ms": latencies[math.ceil(len(rows)*.95)-1],
            "inputTokens": sum(row.get("inputTokens", 0) for row in rows),
            "outputTokens": sum(row.get("outputTokens", 0) for row in rows)}


def by_module(cases, rows):
    result = {}
    for module in ("memo", "finance", "schedule", "health", None):
        indices = [i for i, case in enumerate(cases) if case["expected"].get("module") == module]
        if indices:
            result[module or "clarification"] = summarize([cases[i] for i in indices], [rows[i] for i in indices])
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true")
    parser.add_argument("--cases", default=str(Path(__file__).with_name("eval_actions_fields.json")))
    parser.add_argument("--output", default="docs/evaluation-four-modules-fields.json")
    args = parser.parse_args()
    if not args.live:
        parser.error("Pass --live to use the installed local model; no paid provider or fallback is used.")
    source = Path(args.cases).read_bytes()
    cases = json.loads(source)
    if not cases or any(not case.get("expected") for case in cases):
        parser.error("Every case needs nonempty field expectations")
    _, model = local_settings()
    rows = []
    for case in cases:
        started = time.perf_counter()
        tokens = {}
        def provider(text):
            raw, metrics = call_local_model(text, ACTION_SCHEMA, ACTION_SYSTEM + "\nRequested module hint: " + case.get("module", "auto"))
            tokens.update(metrics)
            return raw, metrics
        row = {"id": case["id"], "input": case["text"], "expected": case["expected"]}
        try:
            actual = create_action({"text": case["text"], "timezone": case["timezone"], "module": case.get("module", "auto")},
                                   provider=provider, now=datetime.fromisoformat(case["now"]))
            checks = score(case, actual)
            row.update(actual=actual, checks=checks, correct=all(checks.values()))
        except ServiceError as error:
            row.update(error=error.code, correct=False, checks={key: False for key in case["expected"]})
        row.update(latencyMs=round((time.perf_counter()-started)*1000), inputTokens=tokens.get("prompt_tokens", 0), outputTokens=tokens.get("completion_tokens", 0))
        rows.append(row)
        print(f"{len(rows)}/{len(cases)} {case['id']}: {'pass' if row['correct'] else 'review'}", flush=True)
    report = {"provider": "ollama-local", "model": model, "datasetSha256": hashlib.sha256(source).hexdigest(),
              "scope": "Fixed development regression set. Title checks use keywords; unlisted fields and general semantic equivalence are not scored. Not an independent holdout.",
              "estimatedApiCostUsd": 0, **summarize(cases, rows), "byModule": by_module(cases, rows), "results": rows}
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2)+"\n", encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items() if k!='results'}, ensure_ascii=False, indent=2))
    return 0 if all(row["correct"] for row in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
