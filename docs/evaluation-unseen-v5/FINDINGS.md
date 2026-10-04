# First-pass findings

The 120 frozen inputs produced **104/120 correct final routes (86.67%)**, **197/216 correct required-field checks (91.20%)**, and **85/120 inputs with every annotated check correct (70.83%)**. Chinese all-check accuracy was 40/60 (66.67%); English was 45/60 (75.00%).

These are the untouched first-pass results. No application source, prompt, scoring rule or expected answer was changed after the freeze. All 35 inputs with at least one failed check remain in the results. The existing 240-case regression was preserved separately, not rerun or combined with these scores. The first pass was completed locally before publication was requested; publishing these results does not change the scores.

## What needs improvement

| Priority | Observed issue | Evidence from this run | Recommended direction |
|---|---|---|---|
| High | Unsupported operations accepted as new drafts | 10 of 24 expected-clarification inputs returned drafts. `U5-ZH-FINANCE-13` (bank balance query), `U5-EN-FINANCE-13` (correct an existing transaction), and `U5-ZH-FINANCE-15` (execute a transfer) all returned Finance drafts. | Decide create/query/update/external-action intent before extracting fields. Reject unsupported execution intents consistently instead of matching only a short list of phrases. |
| High | Calendar dates without a year disappear | `U5-EN-SCHEDULE-01` says “November 27”; the result has time 18:20 but no date. `U5-ZH-SCHEDULE-01` similarly loses “11月21日”. | Define a current-year policy explicitly and cover both Chinese month/day and English month-name forms, with user-timezone and past/future ambiguity handling. |
| High | Titles lose the real subject | `U5-EN-MEMO-01` becomes “Add to-do”; `U5-EN-MEMO-11` becomes “Task for tomorrow”; `U5-ZH-MEMO-03` becomes “周五之前”. | Preserve the requested activity and object, remove request boilerplate, and avoid falling back to a temporal phrase. Some Finance titles are generic while details survive in notes; these still fail the predeclared title checks. |
| High | Multiple outputs become a parse error instead of a clarification | `U5-EN-SCHEDULE-14` emits two JSON objects separated by a comma and raises `JSONDecodeError`. | Detect multiple records as a clarification condition. Keep strict parsing for incomplete or malformed responses; do not silently select the first action. |
| Medium | Negation erases or reverses a valid field | `U5-EN-FINANCE-11` loses Bank Card in “bank card, not cash”; `U5-ZH-FINANCE-10` loses expense in “不是收入”; `U5-EN-MEMO-10` incorrectly chooses important in “normal rather than high priority”. | Scope negation and contrast to the relevant option while preserving the explicitly affirmed field. |
| Medium | Quoted names still become dates | `U5-ZH-MEMO-08` turns the book title `《Tomorrow》` into a next-day deadline despite “没有截止时间”. | Extend quote handling to Chinese book-title brackets and give an explicit no-deadline instruction precedence. |
| Medium | Health paraphrases and device ownership are brittle | `U5-ZH-HEALTH-05` (“花了多少时间…手机…应用”) routes to Finance; `U5-ZH-HEALTH-14` accepts a request about a brother’s phone. `U5-EN-HEALTH-09` rejects the typo “screeen time”. | Separate spending money from spending time, normalize modest typing errors, and require this-device scope. |
| Medium | Unsupported extra schema fields cause an error | `U5-ZH-SCHEDULE-08` emits an extra `event` key and returns `invalid_model_output`. | Prefer schema-constrained generation where supported. Keep validation strict and provide a recoverable user-facing message when structure is invalid. |

The rows above overlap; their examples must not be added together as independent failure counts. Of 24 requests that should clarify, 13 clarified correctly, 10 produced drafts, and one produced a parse error. An incorrect draft is not evidence of a database write or executed transfer: this evaluation stops before user confirmation.

## Limits and next use

- This is an internally authored holdout, checked against 916 unique historical inputs for lexical overlap. It is new to application tuning before this run, not an independent external benchmark or a claim about the model's pretraining data.
- The model and application adapter ran on Windows CPU. These numbers measure routing and reviewable draft fields, not Android execution, database persistence, or actual screen-time measurements.
- Required-field checks use a micro average and include predeclared blank values. All-check accuracy includes status/module and the annotated optional fields. Titles use keyword checks, not complete semantic assessment.
- Future work may fix the failures using this set, but subsequent scores on it must be labelled regression. Preserve this first-pass record and use another untouched set for a later generalization claim.
- Full frozen inputs, expected answers, original model responses and scored outputs are in [`mobile_unseen_v5.json`](../../backend/evaluation/mobile_unseen_v5.json) and [`results.json`](results.json). The source snapshot and hash manifests provide a local audit trail; they are not external preregistration.
