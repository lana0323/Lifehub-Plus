"""Publish accuracy tables and bilingual CSVs from a completed captured run."""
import csv
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/evaluation-mobile"
read = lambda p: json.loads(p.read_text(encoding="utf-8"))
report = read(OUT / "results.json")
baseline = read(OUT / "first-pass-results.json")
reliability = read(OUT / "reliability.json")
cases = read(ROOT / "backend/evaluation/holdout_v2.json")
assert report["complete"] and baseline["complete"] and len(report["results"]) == 240
by_id = {row["id"]: row for row in report["results"]}


def ratio(value):
    return f'{value["passed"]}/{value["total"]} ({value["rate"]:.1%})'


metrics = [("Module routing", "routingOverall"), ("Required fields", "requiredFieldMicro"),
           ("All specified fields", "allSpecifiedFields"), ("Supported draft contract", "supportedDraftContract")]
lines = ["# Mobile-model regression results", "", "Evaluated on October 2, 2026.", "",
         "**Model:** Qwen2.5-1.5B-Instruct int8, ekv4096. **Execution:** Windows 11, Intel i5-12400F, CPU, JVM LiteRT-LM 0.10.2, JDK 21.0.7, Python 3.11.7. Scoring uses tzdata 2026.2, matching the APK. The model file and model prompt match the Android implementation. Android application checks are listed separately.", "",
         "This is an **application-pipeline regression evaluation**: model extraction plus deterministic input grounding and validation. The 240 cases were used in previous evaluations, so these results are not an unseen holdout score. The input set and expected answers were not changed.", "",
         "## Accuracy", "", "| Metric | Chinese | English | Combined |", "|---|---|---|---|"]
for label, key in metrics:
    lines.append(f'| {label} | {ratio(report["byLanguage"]["zh"][key])} | {ratio(report["byLanguage"]["en"][key])} | {ratio(report["summary"][key])} |')
lines += ["", "- Routing requires both the expected response status and destination module.",
          "- Required-field accuracy counts individual annotated fields, including values deliberately left unset for manual completion. It does not mean every input is ready to save.",
          "- All-specified-field accuracy requires every annotated check for an input to pass. Title checks use the fixed dataset's required words; unannotated details are not comprehensively judged.",
          "- Supported draft contract excludes requests whose expected outcome is clarification. It checks the reviewable draft; it is not a count of records written to the database.", "",
          "## Before and after validation refinements", "",
          "The first pass used frozen configuration [protocol.json](protocol.json), source snapshot `4ac23d1`, one attempt per input and no retries. The captured native model outputs were then replayed through the final validation rules. No second model-generation pass or manual correction of responses was used to obtain the refined scores. Deterministic preflight checks also handle unsupported Health logging before model generation in the app.", "",
          "| Metric | Frozen first pass | Final validation replay |", "|---|---|---|"]
for label,key in metrics:
    lines.append(f'| {label} | {ratio(baseline["summary"][key])} | {ratio(report["summary"][key])} |')
lines += ["", "Changes address explicit task routing, negated transaction context, missing payment accounts, supported usage-query vocabulary, unsupported Health logging, grounded event metadata, and financial descriptions/categories. No rule looks up a case ID or expected answer.", "",
          "## Workflow breakdown", "", "| Workflow group | Routing | All specified fields | Supported draft contract |", "|---|---|---|---|"]
for group, stats in report["byWorkflow"].items():
    lines.append(f'| {group.title()} | {ratio(stats["routingOverall"])} | {ratio(stats["allSpecifiedFields"])} | {ratio(stats["supportedDraftContract"])} |')
lines += ["", "Each workflow group contains 60 inputs, split evenly between Chinese and English. The groups include complete, incomplete, ambiguous and unsupported requests.", "",
          "## Application checks", "", "| Check | Passed | Environment / scope |", "|---|---|---|"]
for key,label,scope in [("python","Python validation","Offline unit checks"),("androidUnit","Android/JVM unit checks","Gradle testDebugUnitTest"),("androidReliability","Android reliability","API 34 isolated x86_64 emulator; persistence, account isolation, cancellation, review and packaged validation")]:
    result=reliability[key]
    lines.append(f'| {label} | {result["passed"]}/{result["total"]} | {scope} |')
lines += ["| Signed APK | Verified | ARM64, com.lifeHub, version 1.1.0; signature, installation and login-screen launch checked |", "",
          "The packaged validation subset is included in the reliability count and is not counted twice. Model accuracy does not include user edits or UI/database-test outcomes. The 40-case development set remains for debugging: [development results](development-results.json).", "",
          "## Remaining failures", "", "| Case | Checks not satisfied |", "|---|---|"]
for row in report["results"]:
    if not row["allChecksCorrect"]:
        failure = row.get("error") or ", ".join(k for k,v in row["checks"].items() if not v)
        lines.append(f'| {row["id"]} | {failure} |')
lines += ["", "A fresh independent dataset is needed to measure generalization after these refinements. Generated content remains editable and requires user confirmation before saving.", "",
          "## Data and reproduction", "",
          "- [Chinese 120-case CSV](chinese-120.csv) and [English 120-case CSV](english-120.csv)",
          "- [Final outputs and per-field checks](results.json) and [frozen first-pass outputs](first-pass-results.json)",
          "- [Protocol and source hashes](protocol.json), [refinement source hashes](refinement.json), [application-check evidence](reliability.json)",
          "- [Unchanged 240-case input set](../../backend/evaluation/holdout_v2.json)", "",
          "Use JDK 21, Python 3.11, and the [pinned mobile model](../ANDROID_INSTALL.md). The runner installs tzdata 2026.2 into its private output directory. From the repository root:", "", "```powershell",
          r'.\scripts\mobile-evaluation\run.ps1 -ModelPath "C:/models/mobile-qwen2.5.litertlm"', "```", "",
          "The script downloads pinned JVM dependencies, creates a fresh conversation for each request and uses the app's current prompt/validation adapter. Output goes to `.local/mobile-reproduction`. For the historical first pass, check out source snapshot `4ac23d1`. The Android implementation creates an engine per request; the host runner reuses an engine with a fresh conversation per case.", ""]
(OUT / "REPORT.md").write_text("\n".join(lines), encoding="utf-8")
for language,name in [("zh","chinese-120.csv"),("en","english-120.csv")]:
    selected=[c for c in cases if c["language"]==language]
    assert len(selected)==120
    with (OUT/name).open("w",encoding="utf-8-sig",newline="") as f:
        writer=csv.writer(f)
        writer.writerow(["id","module_group","input","expected_status","expected_module","actual_status","actual_module","routing_correct","required_fields_correct","required_fields_total","all_fields_correct","failed_checks","expected","actual"])
        for c in selected:
            r=by_id[c["id"]];actual=r.get("actual",{})
            writer.writerow([c["id"],c["group"],c["text"],c["expected"]["status"],c["expected"].get("module"),actual.get("status"),actual.get("module"),r["routeCorrect"],sum(r["checks"].get(k,False) for k in c["requiredFields"]),len(c["requiredFields"]),r["allChecksCorrect"],r.get("error") or "; ".join(k for k,v in r["checks"].items() if not v),json.dumps(c["expected"],ensure_ascii=False),json.dumps(actual,ensure_ascii=False)])
print("Report and two 120-case CSV files written")
