# Mobile AI v4 post-fix regression

This is a **fresh model run on a seen regression set**, after development against v4 failures. It is not a new holdout or unseen-model accuracy. The [original first-pass results](../evaluation-mobile-holdout-v4/REPORT.md), inputs, expected answers and scoring functions remain unchanged.

## Method

- 120 Chinese and 120 English inputs, 30 per workflow per language; 180 draft requests and 60 clarification requests.
- Qwen2.5-1.5B-Instruct int8 ekv4096, LiteRT-LM 0.10.2, Windows CPU. Same model, prompt, asynchronous Unicode-safe transport and Python validation as the mobile implementation.
- One attempt per input, fresh conversation, no retry or manual answer repair. Preflight responses and raw model routing are counted separately. The source hashes and configuration were frozen before inference.
- These metrics evaluate reviewable drafts, not Android inference, UI completion or database writes. Titles are checked against predeclared keyword alternatives. Internally authored Chinese/English scenario families overlap; this is not an independent benchmark.
- No latency statistics are reported. See [protocol.json](protocol.json) and [separate application checks](reliability.json).

## Before and after

| Metric | Frozen first pass | Post-fix regression |
|---|---|---|
| Routing after validation | 188/240 (78.33%) | 240/240 (100.00%) |
| Required fields | 398/432 (92.13%) | 432/432 (100.00%) |
| All specified fields | 153/240 (63.75%) | 238/240 (99.17%) |
| Supported draft checks | 123/180 (68.33%) | 178/180 (98.89%) |
| Clarification handling | 30/60 (50.00%) | 60/60 (100.00%) |
| Dates, including expected blanks | 128/144 (88.89%) | 144/144 (100.00%) |

| Language | Routing | Required fields | All specified fields |
|---|---|---|---|
| Chinese | 120/120 (100.00%) | 216/216 (100.00%) | 119/120 (99.17%) |
| English | 120/120 (100.00%) | 216/216 (100.00%) | 119/120 (99.17%) |

| Workflow | Routing | All specified fields |
|---|---|---|
| Memo | 60/60 (100.00%) | 58/60 (96.67%) |
| Finance | 60/60 (100.00%) | 60/60 (100.00%) |
| Schedule | 60/60 (100.00%) | 60/60 (100.00%) |
| Health | 60/60 (100.00%) | 60/60 (100.00%) |

## Run integrity

- 192 model calls and 48 preflight responses; 0 pipeline errors. All inputs remain in the denominator.
- 0 expected-clarification inputs returned as drafts; drafts still require user confirmation.
- Raw model routing on the invoked subset: **161/192 (83.85%)**. Its denominator differs from the first pass because preflight now handles more inputs. Final pipeline accuracy includes deterministic application rules.

## Implementation changes

- Scope negated destinations to their own clause. Recognize mixed Chinese/English word boundaries, Health navigation and usage paraphrases.
- Detect separate actions, including repeated events and separately priced purchases; keep a purchase followed by a request to record it as one action. Tasks about sending/deleting remain supported, while direct execution requests require clarification.
- Keep explicit transaction direction when amount or account is unknown. Preserve deliberately blank categories and reject denied payments as evidence of spending.
- Share date/time parsing between Android and the backend: calendar-week weekdays, next-occurrence bare weekdays, Chinese number dates/clocks, noon and midnight. Quoted names are not date evidence; conflicting dates stay unset.
- Recover the subject of generic titles and content behind a meta title such as "Note for later". Multiple complete JSON objects produce a clarification; partial output, prose and unsupported keys remain invalid.

## Remaining failures

| Input | Failed checks |
|---|---|
| Add oiling the squeaky hinge to the list. It is not urgent at all. | priority |
| 待办：换浴室灯泡，不急，哪天都行。 | priority |

The frozen set interprets "not urgent / 不急" as normal priority. Production conservatively leaves priority unset because a non-urgent task can still be important. These disagreements are retained as failures; the expected answers were not changed.

This set informed development. A future untouched holdout is needed to assess generalization. The review screen remains the final place to correct fields before confirmation.

## Files and reproduction

[All results](results.csv) · [Chinese 120](chinese-120.csv) · [English 120](english-120.csv) · [Failures](failures.csv) · [Raw responses and checks](results.json) · [Frozen protocol](protocol.json)

```powershell
.\scripts\mobile-evaluation\run_v4_regression.ps1 -ModelPath C:/models/mobile-qwen2.5.litertlm -Python python -JavaHome $env:JAVA_HOME -OutputDirectory .local/reproduced-v4-regression
```

Use a fresh directory. The script checks source/data/model hashes and preserves failures without retrying. Historical reports require their historical source revision.
