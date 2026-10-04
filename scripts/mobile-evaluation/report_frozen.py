"""Report an already scored frozen run, without inference or rescoring."""
import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
from score_holdout import write_csv


def read(path):
    return json.loads(path.read_text(encoding="utf-8"))


def rate(metric):
    if not metric["total"]:
        return "Not applicable"
    percentage = (Decimal(metric['passed']) * 100 / Decimal(metric['total'])).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)
    return f'{metric["passed"]}/{metric["total"]} ({percentage}%)'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--directory", type=Path, required=True)
    p.add_argument("--capture", type=Path, required=True)
    p.add_argument("--freeze-commit", required=True)
    args = p.parse_args()
    out, capture = args.directory, args.capture
    report, protocol = read(out / "results.json"), read(out / "protocol.json")
    reliability = read(out / "reliability.json")
    cases = read(ROOT / protocol["dataset"])
    assert report["complete"] and len(report["results"]) == len(cases) == 240
    rows = {r["id"]: r for r in report["results"]}
    failed = [c for c in cases if not rows[c["id"]]["allChecksCorrect"]]
    write_csv(out / "failures.csv", failed, rows)
    run = dict(completedAtUtc=datetime.now(timezone.utc).isoformat(),
               freezeCommit=args.freeze_commit, productionCommit=protocol["productionCommit"],
               cases=len(cases), modelCalls=report["summary"]["modelCalls"],
               preflight=report["summary"]["preflight"],
               nativeSegments=len(list((capture / "native-logs").glob("native-*.log"))),
               interruptions=read(capture / "native-logs/native-interruptions.json") if (capture / "native-logs/native-interruptions.json").exists() else [],
               attemptsPerInput=1, retries=0,
               scope="Fresh first pass on frozen v4 inputs. Windows JVM CPU inference with the mobile model, prompt, text transport and validation. No tuning during the run.")
    for name in ("prepared", "raw", "validated"):
        run[name + "Sha256"] = hashlib.sha256((capture / (name + ".json")).read_bytes()).hexdigest()
    run["reportSha256"] = hashlib.sha256((out / "results.json").read_bytes()).hexdigest()
    (out / "run.json").write_text(json.dumps(run, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    summary = report["summary"]
    lines = ["# Mobile model evaluation: frozen v4 first pass", "",
             "Version 1.1.2 preserves specific event subjects and keeps a secondary explanation from replacing a transaction title. This report evaluates those changes with **240 newly authored inputs: Chinese 120 and English 120**, 30 per workflow per language.", "",
             f'Inputs, expected answers, production sources and scoring were [frozen at `{args.freeze_commit[:7]}`](https://github.com/lana0323/Lifehub-Plus/commit/{args.freeze_commit}) before inference. Production revision: `{protocol["productionCommit"]}`. Every input received one attempt, with no retries, manual answer repair or changes during the run.', "",
             "## Results", "", "| Metric | Chinese | English | Combined |", "|---|---|---|---|"]
    metrics = [("Routing after validation", "routing"), ("Required fields", "requiredFields"),
               ("All specified fields", "allSpecifiedFields"), ("Supported draft requests", "supportedDraft"),
               ("Expected clarifications", "clarification"), ("Date fields, including expected blanks", "dates"),
               ("Raw model routing, invoked inputs only", "rawModelRouting")]
    for name, key in metrics:
        lines.append(f'| {name} | {rate(report["byLanguage"]["zh"][key])} | {rate(report["byLanguage"]["en"][key])} | {rate(summary[key])} |')
    lines += ["", f'**{summary["modelCalls"]} model calls**, {summary["preflight"]} preflight clarifications, {summary["errors"]} processing errors, and {summary["unsafeDrafts"]} expected-clarification inputs returned as drafts. A draft still requires user confirmation; these counts do not describe automatic database writes.', "",
              "| Workflow group | All specified fields |", "|---|---|"]
    for g, r in report["byWorkflow"].items():
        lines.append(f'| {g.title()} | {rate(r["allSpecifiedFields"])} |')
    lines += ["", "## Findings and next priorities", "",
              "This first pass exposes substantial gaps outside the earlier regression wording. The old 99.2% seen-set score does not describe these new inputs. The first-pass evidence is preserved before further fixes.", "",
              "1. **Request boundaries and negation.** Only 4/16 multiple-action inputs and 1/6 negation inputs passed every check. Some balance/calendar queries, record edits and external-action requests became drafts. The four `ValueError` cases contain multiple JSON action objects instead of one clarification; three other outputs failed the model-output contract.",
              "2. **Health vocabulary.** Supported paraphrases such as application usage records and opening the existing Health page sometimes receive `health_unsupported` or enter another module. Conversely, a request about another person's usage may open this device's page. No third-party statistics are accessed.",
              "3. **Date and time grounding.** Sixteen annotated date checks failed. Bare weekdays, Chinese written-number dates/times, noon, uncertain dates and date words inside quoted names need broader handling.",
              "4. **Finance and titles.** Eight of 48 type checks and five category checks failed. Ambiguity about one field can incorrectly clear another known field. Six title checks still failed, including a jacket reduced to Shopping and a Portuguese class reduced to Class. The repaired umbrella-collection and croissant-receipt scenarios both passed in this new set.", "",
              "These counts describe the frozen first pass. No rule or prompt changes were made after inspecting these outputs. Generic title checks can still accept imperfect wording; use the raw examples for qualitative review."]
    lines += ["", "## What the figures measure", "",
              "- Application scores include model output, preflight checks and deterministic validation. Raw model routing is reported separately and excludes preflight-only inputs.",
              "- Required fields use a micro average. Missing or ambiguous values are correct only when the frozen answer specifies a blank. Optional memo dates/priorities and schedule times are included in all-specified-field checks.",
              "- Supported-draft success means all annotated fields matched for a reviewable draft. It does not measure successful UI entry or database persistence.",
              "- Titles use predeclared keyword alternatives. Passing that check does not guarantee an ideal title or full semantic equivalence.",
              "- The 180 draft requests and 60 clarification requests include colloquial wording, corrected typos, negation, mixed languages, invalid amounts, missing fields, quoted dates and calendar boundaries.",
              "- The set was authored internally by the development assistant. Chinese and English scenario families overlap. Lexical screening against three prior dataset files found no exact repeats or pairs above the declared 0.78 threshold; this does not establish semantic or statistical independence.",
              "- This is an untouched first pass for these inputs. If its failures guide further changes, subsequent runs on this set must be called regression tests. Earlier first-pass and regression artifacts remain unchanged.", "",
              "## Runtime and reproducibility", "",
              f'- Model: **{protocol["model"]}**, revision `{protocol["modelRevision"]}`.',
              f'- Model SHA-256: `{protocol["modelSha256"]}`.',
              f'- Runtime: {protocol["runtime"]}.',
              '- Machine: Intel Core i5-12400F, 32 GiB RAM, Windows 11. Inference uses CPU; no paid service or fallback.',
              '- Sampling: top-k 1, top-p 1.0, temperature 0, seed 42, context 4096. One engine, fresh conversation per input, concurrency 1.',
              '- Fixed per-input clocks and timezones are part of the dataset. These are host inference measurements, separate from Android integration and APK checks.',
              f'- Dataset SHA-256: `{protocol["datasetSha256"]}`. Source hashes are in [protocol.json](protocol.json).',
              '- No latency statistics are reported.', "",
              'Use the freeze revision and a fresh output directory:', "", '```powershell',
              f'git checkout {args.freeze_commit}',
              r'.\scripts\mobile-evaluation\run_frozen.ps1 -ModelPath "C:/models/mobile-qwen2.5.litertlm"',
              '```', "", 'The runner verifies the frozen source and dataset hashes before generation. Raw and validated outputs are retained locally. [results.json](results.json) contains every captured model answer, validation result, check and error. [run.json](run.json) records capture hashes.', "",
              '## Separate application checks', "", '| Check | Result | Scope |', '|---|---|---|']
    for key, name, scope in [('python', 'Python', 'Validation, title recovery, date rules and API contracts'),
                              ('androidUnit', 'Android/JVM unit', 'Serialization, money, dates, usage aggregation and utilities'),
                              ('androidReliability', 'Android integration', 'Packaged title repair, confirmation, duplicate prevention, cancellation, account isolation and database read-back')]:
        r = reliability[key]
        lines.append(f'| {name} | {r["passed"]}/{r["total"]} | {scope} |')
    lines += ["", 'Android checks use an isolated API 34 emulator and the separate QA package. The signed release APK is checked for signature, update installation and login launch. [Evidence and commands](reliability.json) are separate from model accuracy.', "",
              '## Earlier title failures', "",
              'The two previously observed title failures are fixed: “修鞋取件” retains the repair subject, and a sandwich purchase no longer becomes “Lost Receipt”. A validation-only replay of the previous 240 raw outputs passes 240/240 all-specified-field checks. This replay performs **no fresh inference** and is not included in the new holdout score. See [title-replay.json](title-replay.json).', "",
              f'## Failed inputs ({len(failed)})', "",
              'Failures remain in the denominator. The expected fields below were frozen before inference. Full raw outputs are available in the per-language CSVs and JSON.', ""]
    if not failed:
        lines.append('No annotated check failed in this run.')
    for c in failed:
        r = rows[c['id']]
        bad = ', '.join(k for k, v in r['checks'].items() if not v)
        lines += [f'### {c["id"]}', '', c['text'], '', f'Failed checks: **{bad}**.', '', '```json',
                  json.dumps(dict(expected=c['expected'], actual=r.get('actual'), error=r.get('error')), ensure_ascii=False, indent=2), '```', '']
    lines += ['## Files', '', '[Excel workbook](Lifehub-Plus-Holdout-v4.xlsx) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [All results](results.csv) · [Failures only](failures.csv) · [Frozen protocol](protocol.json)', '']
    (out / 'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')
    print(json.dumps(dict(cases=len(cases), failures=len(failed), summary=summary), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
