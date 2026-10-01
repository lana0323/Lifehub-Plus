"""Opt-in live semantic evaluation, not a JSON-parse success benchmark."""
import argparse
import json
import math
import os
from datetime import datetime
from pathlib import Path
from task_service import create_draft, ServiceError, local_settings


def score(case, result):
    draft = result.get("draft") or {}
    fields = {"status": result.get("status") == case["status"]}
    if case["status"] == "draft":
        fields.update(
            title=draft.get("title", "").strip().casefold() in [s.casefold() for s in case["titles"]],
            dueDate=draft.get("dueDate") == case["dueDate"] and bool(draft),
            priority=draft.get("priority") == case["priority"] and bool(draft),
            notes=case.get("notesContains", "") in draft.get("notes", "") and bool(draft),
        )
    return fields


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Run real inference (local Ollama by default, no API fee)")
    parser.add_argument("--output", default="backend/eval-results.json")
    parser.add_argument("--cases", default=str(Path(__file__).with_name("eval_cases.json")))
    parser.add_argument("--input-price", type=float, help="USD per million input tokens from your provider")
    parser.add_argument("--output-price", type=float, help="USD per million output tokens from your provider")
    args = parser.parse_args()
    if not args.live:
        parser.error("Use --live for real model inference; use unittest for isolated offline checks.")
    provider = os.environ.get("AI_PROVIDER", "ollama")
    if provider != "ollama" and (os.environ.get("ENABLE_PAID_AI") != "true" or not os.environ.get("AI_API_KEY") or not os.environ.get("AI_MODEL")):
        parser.error("Set server-side AI_API_KEY and AI_MODEL first.")
    model = local_settings()[1] if provider == "ollama" else os.environ["AI_MODEL"]
    cases = json.loads(Path(args.cases).read_text(encoding="utf-8"))
    rows, latencies = [], []
    input_tokens = output_tokens = 0
    for index, case in enumerate(cases):
        try:
            result = create_draft({"text": case["text"], "timezone": case["timezone"]},
                                  now=datetime.fromisoformat(case["now"]))
            fields = score(case, result)
            metrics = result["metrics"]
            latencies.append(metrics["latencyMs"])
            input_tokens += metrics["inputTokens"]
            output_tokens += metrics["outputTokens"]
            rows.append({"case": index + 1, "fields": fields, "correct": all(fields.values()), "actual": result})
        except ServiceError as error:
            rows.append({"case": index + 1, "correct": False, "error": error.code})
    # Failed requests count as incorrect in every applicable field denominator.
    accuracy = {}
    for field in ("status", "title", "dueDate", "priority", "notes"):
        applicable = [i for i, case in enumerate(cases) if field == "status" or case["status"] == "draft"]
        accuracy[field] = sum(rows[i].get("fields", {}).get(field, False) for i in applicable) / len(applicable)
    latency_sorted = sorted(latencies)
    report = {"provider": provider, "model": model, "cases": len(cases), "fieldAccuracy": accuracy,
              "wholeTaskAccuracy": sum(row["correct"] for row in rows) / len(rows),
              "errors": sum("error" in row for row in rows),
              "latencyP50Ms": latency_sorted[math.ceil(len(latencies) * .5) - 1] if latencies else None,
              "latencyP95Ms": latency_sorted[math.ceil(len(latencies) * .95) - 1] if latencies else None,
              "inputTokens": input_tokens, "outputTokens": output_tokens,
              "estimatedCostUsd": (0 if provider == "ollama" else (input_tokens * args.input_price + output_tokens * args.output_price) / 1e6
                  if args.input_price is not None and args.output_price is not None else None),
              "results": rows}
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "results"}, indent=2))


if __name__ == "__main__":
    main()
