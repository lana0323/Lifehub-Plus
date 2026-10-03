# Mobile AI post-fix regression

This is a **fresh model run on a seen regression set**, following analysis of the original holdout failures. It is not a new holdout or a claim of unseen model accuracy. The original [first-pass report](../evaluation-mobile-holdout/REPORT.md), raw responses, expected answers and failure records remain unchanged.

## Scope

- Qwen2.5-1.5B-Instruct int8 ekv4096, the same pinned 1.6 GB model file.
- Windows CPU, LiteRT-LM JVM 0.10.2; the APK's prompt, preflight, validation and asynchronous Unicode-safe message transport. Fresh conversation per input, one attempt, no manual answer repair.
- 120 Chinese and 120 English inputs, 30 per workflow in each language. Identical frozen expectations and scoring functions; SHA-256 recorded in [protocol.json](protocol.json).
- These scores measure routing and reviewable draft fields. They do not measure Android inference accuracy, UI completion or actual database writes. Title checks use predeclared keywords rather than full semantic grading.
- No latency statistics are reported. Account, confirmation and persistence checks are separate in [reliability.json](reliability.json).

## Before and after

| Metric | Original first pass | Post-fix regression |
|---|---|---|
| Routing after validation | 185/240 (77.1%) | 240/240 (100.0%) |
| Required fields | 412/456 (90.4%) | 454/456 (99.6%) |
| All specified fields | 162/240 (67.5%) | 238/240 (99.2%) |
| Supported draft checks | 129/180 (71.7%) | 178/180 (98.9%) |
| Clarification handling | 33/60 (55.0%) | 60/60 (100.0%) |
| Date checks (including expected blanks) | 133/148 (89.9%) | 148/148 (100.0%) |

| Language | Routing | Required fields | All specified fields |
|---|---|---|---|
| Chinese | 120/120 (100.0%) | 227/228 (99.6%) | 119/120 (99.2%) |
| English | 120/120 (100.0%) | 227/228 (99.6%) | 119/120 (99.2%) |

| Workflow | Routing | All specified fields |
|---|---|---|
| Memo | 60/60 (100.0%) | 60/60 (100.0%) |
| Finance | 60/60 (100.0%) | 59/60 (98.3%) |
| Schedule | 60/60 (100.0%) | 59/60 (98.3%) |
| Health | 60/60 (100.0%) | 60/60 (100.0%) |

## Reliability of this run

- 194 native model calls and 46 preflight responses; 0 pipeline errors.
- 0 unsupported inputs became drafts (a draft is not a database write).
- Raw model routing on the invoked subset: **152/194 (78.4%)**. Application rules contribute to the final scores. Its denominator differs from the original run because preflight now rejects more unsupported operations.
- The four emoji inputs that interrupted the original native run are included again; failures are never silently retried.

## Changes

- Escape the serialized JNI JSON to ASCII, preserving Unicode code points for the native parser. Emoji and supplementary CJK characters are not removed. This narrowly scoped compatibility shim is pinned to LiteRT-LM 0.10.2; an SDK upgrade requires verifying its conversation contract. [Upstream boundary](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.10.2/kotlin/java/com/google/ai/edge/litertlm/jni/litertlm.cc).
- Reject unsupported record mutations, account/calendar queries, app controls, other-device usage requests and message sending before generation. Explicit tasks or notes about those actions remain valid drafts.
- Ignore explicitly negated module requests during routing; recognize more Health navigation and usage expressions.
- Keep amount, date, time and account uncertainty separate; retain clear fields. Distinguish quoted names from date instructions, support hyphenated priorities and preserve AM/PM adjacent to Chinese text.
- Preserve grounded file/remark metadata as notes. Accept a single redundant closing brace, while rejecting multiple JSON actions, unknown commands and ungrounded metadata.

## Remaining failures

| Input | Failed checks |
|---|---|
| 选中日程模块后，填明天上午11点的修鞋取件。 | title |
| I bought a sandwich today with cash but lost the receipt and cannot remember the price. | title |

Results informed these fixes, so improvements must be confirmed on a future untouched holdout before being described as generalization. The review screen continues to allow corrections before confirmation.

## Files and reproduction

[All results](results.csv) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [Remaining failures](failures.csv) · [Machine-readable results](results.json) · [Frozen run protocol](protocol.json)

```powershell
.\scripts\mobile-evaluation\run_regression.ps1 -ModelPath C:/models/mobile-qwen2.5.litertlm -Python python -JavaHome $env:JAVA_HOME -OutputDirectory .local/reproduced-regression
```

Use a fresh output directory. The script checks source/data hashes before inference, records interrupted inputs as failures, and never overwrites the published run. To reproduce the historical first pass, check out its freeze commit from the original report rather than using the modified production rules.
