# Unseen holdout dataset

This directory records the **first pass on 120 new inputs** for Lifehub Plus 1.1.3 and Qwen2.5-1.5B-Instruct int8. It complements the existing [240-case regression suite](../evaluation-mobile-v4-regression/REPORT.md). The two sets have separate purposes and separate denominators.

## Distribution

| Workflow | Chinese | English | Expected drafts | Expected clarifications |
|---|---:|---:|---:|---:|
| Memo | 15 | 15 | 24 | 6 |
| Finance | 15 | 15 | 24 | 6 |
| Schedule | 15 | 15 | 24 | 6 |
| Health | 15 | 15 | 24 | 6 |
| Total | 60 | 60 | 96 | 24 |

All inputs use automatic module selection. Cases cover ordinary requests, missing fields, vague or conflicting dates, local-date and year boundaries, invalid amounts and times, quoted names, negation, typing errors, mixed-language wording, multiple actions and unsupported operations. Health cases check this-device usage-query routing, not the accuracy of actual usage-duration statistics.

The [dataset](../../backend/evaluation/mobile_unseen_v5.json) contains each input, fixed reference time, IANA timezone, expected response, required-field list and scenario tag. Chinese inputs are intentionally retained in Chinese. Translating them would change the evaluated dataset.

## Freeze and first-pass procedure

1. Author the inputs and expected answers without running them through the application or model.
2. Check schema, date annotations and lexical overlap against 916 unique historical inputs. The final set has no normalized exact matches and no matches above the predeclared 0.78 character-similarity review threshold. This is not a semantic-independence guarantee.
3. Freeze the dataset, expected answers, scoring code, production source hashes and model configuration. The recorded freeze time is **2026-10-04 05:15:52 UTC**; the protocol retains full precision.
4. Run every input once in a seeded shuffled order. Use a fresh conversation per model invocation, with no retries, prompt edits, rule changes or manual output repair.
5. Validate and score all 120 outcomes. Keep errors and failed checks in the denominator. Examine failures only after the run completes.

The same development assistant authored and annotated the set. It was not used for application tuning before this run, but it is not an independently collected benchmark. “Unseen” refers to application development, not a claim that the text or its patterns were absent from model pretraining. If these failures inform later changes, subsequent evaluations on this set are regression runs.

## Scoring

| Metric | Definition |
|---|---|
| Final pipeline routing | Both response status and destination module match the frozen expectation. |
| Required-field accuracy | Micro average across 216 annotated required-field checks. A blank is correct only when annotated before the run. |
| All annotated checks | Every annotated check for an input passes, including status/module and optional fields where specified. |
| Supported draft checks | All checks pass on the 96 requests expected to yield reviewable drafts. Correctly incomplete drafts still require user input before saving. |
| Clarification handling | Expected clarification response on the 24 unsupported or multiple-action requests. |
| Raw model routing | Route inferred from the original model response, before application corrections. Includes 111 model-invoked inputs and excludes 9 preflight responses. |

Titles are checked against predeclared keyword alternatives. These checks do not establish perfect wording or complete semantic equivalence. Results concern routing and draft validation on Windows CPU, not Android execution, confirmation, database persistence or Health measurement accuracy. No latency statistics are reported.

## Files

- [Full report](REPORT.md) and [remaining issues](FINDINGS.md).
- [Frozen cases](../../backend/evaluation/mobile_unseen_v5.json), [all results](results.csv), [Chinese 60](chinese-60.csv), [English 60](english-60.csv) and [failed cases](failures.csv).
- [Original model responses and field checks](results.json), [input overlap audit](input-audit.json), [recorded protocol](protocol.json) and [pre-publication integrity check](run-integrity.json).
- [Frozen source snapshot](frozen-snapshot.zip), [snapshot checksum](frozen-snapshot.sha256) and [publication path/hash mapping](publication.json).

English documentation and CSV headers are presentation choices; the input text, expected answers and recorded outputs have not been translated or edited. The original protocol and integrity record retain their historical local paths and local-only status. Those describe the completed run before publication, not the current availability of these files. Local spreadsheet previews and model binaries are not included.

## Verify recorded scores

From the repository root with Python 3.11:

```powershell
python scripts/mobile-evaluation/verify_unseen_v5.py
```

This checks frozen artifact hashes and source provenance, recomputes all field checks and aggregates, and verifies the unchanged 240-case regression files. It audits saved outputs without invoking a model or creating another run.

For fresh generation, obtain the [pinned model](../ANDROID_INSTALL.md#model-provenance), install JDK 21, and use a new output directory:

```powershell
.\scripts\mobile-evaluation\run.ps1 -ModelPath C:/models/mobile-qwen2.5.litertlm -Python python -JavaHome $env:JAVA_HOME -Cases backend/evaluation/mobile_unseen_v5.json -OutputDirectory .local/unseen-v5-reproduction -RecordNativeCrashes
```

This runner downloads pinned dependencies and retains captured outputs. A reproduction does not replace the recorded first pass and must not be presented as another untouched holdout after development uses these inputs.
