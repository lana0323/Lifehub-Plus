"""Summarize immutable first-pass model results and the Android replay results."""
from pathlib import Path
import json,csv,hashlib,collections
root=Path(__file__).resolve().parent.parent;out=root/'docs/evaluation'
model=json.loads((out/'holdout-v1-results.json').read_text(encoding='utf-8'))
ui=json.loads((out/'holdout-v1-ui.json').read_text(encoding='utf-8'))
cases=json.loads((root/'backend/evaluation/holdout_v1.json').read_text(encoding='utf-8'))
lock=json.loads((root/'backend/evaluation/holdout_v1.lock.json').read_text())
assert len(ui)==120 and len({x['id'] for x in ui})==120
assert hashlib.sha256((root/'backend/evaluation/holdout_v1.json').read_bytes()).hexdigest()==lock['datasetSha256']
assert all(hashlib.sha256((root/f).read_bytes()).hexdigest()==h for f,h in lock['sourceHashes'].items())
lookup={x['id']:x for x in ui};meta={c['id']:c for c in cases}
rel=json.loads((out/'reliability-results.json').read_text())
def ratio(rows):
 rows=list(rows);return {'passed':sum(x['success'] for x in rows),'total':len(rows),'rate':sum(x['success'] for x in rows)/len(rows) if rows else None}
def fmt(x):return f"{x['passed']}/{x['total']} ({100*x['rate']:.1f}%)" if x['rate'] is not None else 'N/A'
combined={'datasetSha256':lock['datasetSha256'],'firstPass':model['summary'],'androidWorkflowEntrySupported':ratio(x for x in ui if x['supported']), 'androidOutcomesOverall':ratio(ui),'androidClarifications':ratio(x for x in ui if not x['supported']),'reliability':{'passed':rel['passed'],'total':rel['total']},'byWorkflow':{},'byLanguage':{}}
for group in ['memo','finance','schedule','health']:
 combined['byWorkflow'][group]={**model['byWorkflow'][group],'androidWorkflowEntry':ratio(x for x in ui if meta[x['id']]['group']==group and x['supported'])}
for language in ['en','zh']:
 combined['byLanguage'][language]={**model['byLanguage'][language],'androidWorkflowEntry':ratio(x for x in ui if meta[x['id']]['language']==language and x['supported'])}
(out/'summary.json').write_text(json.dumps(combined,indent=2)+'\n',encoding='utf-8')
with (out/'app-evaluation.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['case_id','workflow','language','input','route_correct','required_passed','required_total','all_fields_correct','android_outcome_success','failure','latency_ms'])
 for r in model['results']:
  c=meta[r['id']];u=lookup[r['id']]
  w.writerow([r['id'],c['group'],c['language'],c['text'],r['routeCorrect'],sum(r['checks'][k] for k in c['requiredFields']),len(c['requiredFields']),r['allChecksCorrect'],u['success'],u.get('reason',''),r['latencyMs']])
rows='\n'.join(f"| {g.title()} | {fmt(v['routingOverall'])} | {fmt(v['requiredFieldMicro'])} | {fmt(v['androidWorkflowEntry'])} | {v['latencyMs']['p50']/1000:.3f} / {v['latencyMs']['p95']/1000:.3f} s |" for g,v in combined['byWorkflow'].items())
languages='\n'.join(f"| {g} | {fmt(v['routingOverall'])} | {fmt(v['requiredFieldMicro'])} | {fmt(v['androidWorkflowEntry'])} |" for g,v in combined['byLanguage'].items())
s=model['summary'];e=model['environment'];ui_fail=[x for x in ui if not x['success'] and 'contract failed' not in x.get('reason','')]
report=f'''# Lifehub Plus App Evaluation — Frozen Holdout v1

![Evaluation overview](overview.png)

## Scope

120 newly authored internal held-out cases, 30 for each workflow; 60 English and 60 Chinese prompts. Cases and expected answers were frozen before the first model call. The earlier 40-case set remains a development/regression set and is not included here. No production prompt, parser or application changes were made during this evaluation; the frozen source hashes still match.

This is an **internally authored holdout**, not an externally independent benchmark. Some bilingual cases share scenarios and are correlated. Exact prompt deduplication against earlier evaluation files passed, but does not establish semantic independence. Once this report has been read, v1 should be treated as a regression set for future development.

## Results

| Metric | Result | Definition |
|---|---|---|
| Routing, all cases | {fmt(s['routingOverall'])} | Exact module and status, including the expected clarification outcome |
| Routing, supported requests | {fmt(s['routingSupported'])} | 104 supported requests, excluding 16 clarification cases |
| Required-field accuracy | {fmt(s['requiredFieldMicro'])} | Micro average of predeclared required-field checks, including expected nulls |
| Required fields, complete-input subset | {fmt(s['requiredFieldsOnCompleteInputs'])} | Excludes inputs intentionally missing required information |
| End-to-end workflow entry | {fmt(combined['androidWorkflowEntrySupported'])} | All frozen field checks pass AND the recorded response enters the correct actual Android workflow |
| Correct clarification outcome | {fmt(combined['androidClarifications'])} | Expected clarification is shown in the Android UI |
| All case outcomes | {fmt(combined['androidOutcomesOverall'])} | Workflow entry or clarification as appropriate, with failures retained |
| Backend latency P50 / P95 | {s['latencyMs']['p50']/1000:.3f} / {s['latencyMs']['p95']/1000:.3f} seconds | Nearest-rank, 120 sequential measurements; no warmup in denominator |
| Reliability | {rel['passed']}/{rel['total']} passed | Separate device regression checks, not an accuracy percentage |

**What end-to-end means here:** real local model response → production backend validation → actual Android ViewModel → module confirmation → review form / Health page. Android consumes the recorded first-pass response through a test API adapter, so there is no second inference or cherry-picked retry. Incorrect backend results already fail the end-to-end contract and are not counted as successful merely because a page can open. {len(ui_fail)} additional UI failures occurred among backend-correct cases.

This measures **entry into the correct review workflow**, not successful database insertion for every case or live mobile network behavior. A correctly preserved missing amount/date/priority can pass review-entry evaluation while still requiring user input before saving. Actual confirmation, duplicate prevention and read-back persistence are covered separately by reliability tests. Health success means reaching the usage page; it does not certify system usage data availability or measurement accuracy.

## By workflow

| Workflow | Routing, including clarifications | Required-field checks | Supported workflow entry | Latency P50 / P95 |
|---|---|---|---|---|
{rows}

Required fields are title/priority for Memo; title/amount/type/category/account/date for Finance where an explicit expectation is declared; and title/date for Schedule. Date/time and other declared optional checks still affect the stricter end-to-end contract. Health has no record-creation fields, so required-field accuracy is N/A. The micro average is weighted by field count, not equally by workflow. Titles use frozen keyword checks, not a full semantic assessment; details moved into notes or synonymous titles can be penalized.

## By language

| Language | Routing | Required-field checks | Supported workflow entry |
|---|---|---|---|
{languages}

These are descriptive results for this small, partially paired set; no statistical significance or general language superiority is claimed.

## Failure analysis

- **Financial categories:** several meals/purchases/income records returned null or an incorrect category. Required-field micro accuracy hides how often a complete transaction needs correction; Finance must also be judged by its stricter workflow-entry result.
- **Ambiguous dates:** `schedule-en-11`, `schedule-zh-11` and `memo-zh-12` filled a definite tomorrow date despite uncertainty language. No scoring exceptions were added after seeing these results.
- **Multi-action requests:** several prompts requesting two modules were routed to only one rather than asking the user to split the request.
- **Unsupported Health actions:** running/water records were not consistently rejected or clarified. Health currently supports device usage only.
- **Grounding checks:** {s['errors']} requests were rejected by backend grounding validation. These remain failures in the denominators rather than disappearing from the report. Rejection can protect data quality while still being an unsuccessful user interaction.
- **Strict title matching:** some semantically related titles failed a keyword check; conversely, keyword presence does not prove full semantic correctness. The raw outputs are available for manual review without changing the frozen primary score.

## Reliability checks

The separate suite ran AccountAndWaitingTest (6), RepeatedReviewTest (2), IdempotentRecordsTest (3) and TaskConfirmationTest (1): **{rel['passed']}/{rel['total']} passed**. Coverage includes duplicate confirmations, database reopen, rollback/retry, late responses after cancellation, Activity recreation, account-switch isolation, stale-session forms and save/read-back verification. It is a bounded suite, not proof that every possible race is absent.

Backend offline verification, including holdout scorer checks: **49/49 passed**. Android QA/test APKs built successfully. No production business data was modified.

## Fixed environment and measurement conditions

- CPU: Intel Core i5-12400F, 6 cores / 12 threads; RAM: {e['machine']['ramBytes']/1024**3:.1f} GiB usable physical memory.
- GPU: NVIDIA GeForce RTX 3060 Ti; Windows driver {e['machine']['gpu']['DriverVersion']}.
- OS: Windows 11, build {e['machine']['os']['BuildNumber']}; Python {e['python']}; Ollama {e['ollama']['version']}.
- Model: Qwen3 4B, GGUF Q4_K_M; digest `{e['models']['models'][0]['digest']}`.
- Runtime: temperature 0, seed 42, context 4096, max generated tokens 700, thinking disabled, keep_alive 10 minutes, concurrency 1.
- Repetitions: **one measured run per prompt**, 120 total, no retries; one separate warmup ({e['warmupMs']/1000:.3f} s). Accuracy and latency variability across repeated runs were not estimated. Many prompts contain explicit task/module cues, so this routing score should not be generalized to unconstrained conversation.
- Model timing includes local Ollama HTTP inference and backend validation; excludes Android interaction, user editing, saving and app-network round trips.
- Android: Medium_Phone_API_36.1 emulator, Android 16, SwiftShader rendering, audio disabled; isolated package `com.lifeHub.qa`.
- **Load caveat:** Gradle compilation and emulator reliability tests ran concurrently with part of the model pass. The numbers describe this development-machine workload, not an otherwise idle-machine benchmark; do not compare them directly to measurements under a different load.
- All inference was local; no paid cloud API was called. This does not imply zero hardware/electricity costs.

## Reproduce and audit

Frozen dataset SHA-256: `{model['datasetSha256']}`.

1. Read [the freeze protocol](../../backend/evaluation/holdout_v1.lock.json) and [dataset](../../backend/evaluation/holdout_v1.json).
2. Start local Ollama with qwen3:4b and cloud disabled. Run `python backend/evaluate_holdout.py --live`. The runner refuses to overwrite existing first-pass results; use a separate working copy for subsequent regression runs and preserve this report.
3. Run `python scripts/prepare-holdout-replay.py`, then build QA APKs using `-PisolatedTests=true`. Run HoldoutWorkflowTest with `-e runHoldoutReplay true` through Android instrumentation. Pull `files/holdout-ui-results.json` from the QA app into `holdout-v1-ui.json`.
4. Run `python scripts/summarize-holdout.py` to combine model and Android results.

Artifacts: [raw model results](holdout-v1-results.json), [Android replay outcomes](holdout-v1-ui.json), [reliability results](reliability-results.json), [summary JSON](summary.json), [all-case CSV](app-evaluation.csv), [detailed expected/actual CSV](holdout-v1-cases.csv).

## Interpretation

The application routes ordinary supported requests well and the selected reliability checks pass, but clarification handling and financial field completeness remain substantial weaknesses. The appropriate claim is a measured local AI application with review and recovery controls—not fully autonomous or universally accurate task execution. Further improvements should use the development set, followed by a newly frozen evaluation set.
'''
(out/'REPORT.md').write_text(report,encoding='utf-8')
print(json.dumps(combined,indent=2))
