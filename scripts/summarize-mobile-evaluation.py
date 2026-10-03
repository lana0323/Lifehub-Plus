"""Score unchanged cases against captured native-model responses; omit timings."""
import argparse
import csv
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from evaluate_holdout import frozen_score, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--results", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    cases = json.loads(args.cases.read_text(encoding="utf-8"))
    captured = json.loads(args.results.read_text(encoding="utf-8"))
    by_id = {c["id"]: c for c in cases}
    assert len(by_id) == len(cases)
    assert len({r["id"] for r in captured}) == len(captured)
    rows = []
    for source in captured:
        row = dict(source)
        case = by_id[row["id"]]
        row["checks"] = frozen_score(case, row.get("actual", {}), decimal_amounts=True)
        row["routeCorrect"] = row["checks"].get("status", False) and row["checks"].get("module", False)
        row["allChecksCorrect"] = all(row["checks"].values())
        rows.append(row)
    assert rows, "No completed inference cases"
    # The development set predates the required-field annotations.
    annotated = [dict(c, requiredFields=c.get("requiredFields", []), tags=c.get("tags", [])) for c in cases]
    def accuracy_summary(selected):
        result = summary(selected, rows)
        result.pop("latencyMs", None)
        return result
    report = {"model": "Qwen2.5-1.5B-Instruct q8 ekv4096", "runtime": "Windows host JVM LiteRT-LM 0.10.2 CPU",
              "datasetSha256": hashlib.sha256(args.cases.read_bytes()).hexdigest(),
              "complete": len(rows) == len(cases), "summary": accuracy_summary(annotated), "results": rows,
              "scope": "Draft contract regression; database persistence and device performance require separate checks."}
    if all("language" in c for c in annotated):
        report["byLanguage"] = {lang: accuracy_summary([c for c in annotated if c["language"] == lang])
                                for lang in sorted({c["language"] for c in annotated})}
    if all("group" in c for c in annotated):
        report["byWorkflow"] = {group: accuracy_summary([c for c in annotated if c["group"] == group])
                                for group in sorted({c["group"] for c in annotated})}
    for row in rows:
        row.pop("latencyMs", None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with args.output.with_suffix(".csv").open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "input", "routing_correct", "all_fields_correct", "failed_checks", "error"])
        for row in rows:
            writer.writerow([row["id"], by_id[row["id"]]["text"], row["routeCorrect"], row["allChecksCorrect"],
                             "; ".join(k for k,v in row["checks"].items() if not v), row.get("error", "")])
    print(json.dumps({k:v for k,v in report.items() if k != "results"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
