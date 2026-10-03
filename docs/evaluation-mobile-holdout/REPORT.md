# New mobile-model holdout: first-pass results

**Model:** Qwen2.5-1.5B-Instruct int8 (ekv4096), LiteRT-LM 0.10.2. **Execution:** Windows CPU, Intel i5-12400F, JDK 21.0.7, Python 3.11.7, tzdata 2026.2. The model file, prompt and validation match the frozen mobile implementation. These are host inference measurements; Android UI/database checks remain separate.

## Freeze and scope

The 240 new inputs and expected answers were frozen at `2026-10-03T07:12:33.451147+00:00` and published in commit `1f8eb6b` before model inference. The evaluated production baseline is `eb798d5`. Each language has 120 inputs, with 30 per workflow group. In total, 180 inputs expect a reviewable draft and 60 expect clarification.

This is a **new internally authored holdout**, not a third-party benchmark or a user-study sample. The development assistant authored and checked the expectations before inference. Scenario families overlap between languages. Exact/lexical deduplication found no matches above the declared threshold against the earlier inputs; that does not prove semantic independence.

**No model or production-rule tuning was performed during the run.** Each input was attempted once, with no retry or manual correction. Failed cases remain in the denominator. These are first-pass results, not the earlier seen-set validation replay.

**Execution interruptions:** 4 native-process crashes were recorded as failed inputs. The original completed prefix was preserved each time. A new process continued with the next input; the interrupted input was not retried. This recovery changes the initial single-process execution plan, not the model settings, frozen expectations or scoring rules. [Run provenance](run.json) records the affected IDs and evidence hashes.

## Application-pipeline accuracy

| Metric | Chinese | English | Combined |
|---|---|---|---|
| Routing after validation | 92/120 (76.7%) | 93/120 (77.5%) | 185/240 (77.1%) |
| Required fields | 202/228 (88.6%) | 210/228 (92.1%) | 412/456 (90.4%) |
| All specified fields | 80/120 (66.7%) | 82/120 (68.3%) | 162/240 (67.5%) |
| Supported draft contract | 64/90 (71.1%) | 65/90 (72.2%) | 129/180 (71.7%) |
| Clarification outcome | 16/30 (53.3%) | 17/30 (56.7%) | 33/60 (55.0%) |
| Date fields, including expected nulls | 68/74 (91.9%) | 65/74 (87.8%) | 133/148 (89.9%) |

- Routing requires the correct response status and module. A clarification incorrectly turned into a draft is a failure, even if saving still requires confirmation.
- Required-field accuracy uses predeclared field annotations. Correctly leaving an ambiguous or missing field unset counts as correct; it does not mean the draft is ready to save.
- All-specified-field accuracy includes optional fields that were annotated, such as memo priority and schedule time. Titles use fixed keyword alternatives; unannotated semantics are not exhaustively checked.
- Supported draft contract includes only the 180 inputs expected to produce drafts. It does not measure opening an Android form or actually writing records.
- Date accuracy includes both exact expected calendar dates and explicitly expected null values. Memo deadlines in the past stay unset; historical Schedule dates remain reviewable.

## Before application corrections

| Metric | Chinese | English | Combined |
|---|---|---|---|
| Raw model status/module | 75/111 (67.6%) | 73/111 (65.8%) | 148/222 (66.7%) |

The model was invoked for **222** inputs. The app's frozen preflight handled **18** inputs without model generation. The raw-routing denominator contains only model-invoked cases, including failures; it is not directly comparable to the 240-input pipeline denominator. Raw routing reads only the model's emitted intent/module, before deterministic corrections.

On that same model-invoked subset, final routing passed **167/222**. Validation corrected **31** raw routing mistakes and changed **12** initially correct routes into failures. This paired diagnostic includes validation errors and is separate from the full 240-input primary score.

Pipeline errors: **11**. Inputs requiring clarification that instead received a draft: **23**. These are draft-level mistakes, not executed actions or automatic database writes.

## Workflow groups

| Group | Routing | Required fields | All specified fields |
|---|---|---|---|
| Memo | 41/60 (68.3%) | 42/48 (87.5%) | 37/60 (61.7%) |
| Finance | 53/60 (88.3%) | 281/312 (90.1%) | 40/60 (66.7%) |
| Schedule | 49/60 (81.7%) | 89/96 (92.7%) | 43/60 (71.7%) |
| Health | 42/60 (70.0%) | Not applicable | 42/60 (70.0%) |

## Field breakdown

| Field | Passed checks |
|---|---|
| account | 46/52 (88.5%) |
| amount | 48/52 (92.3%) |
| category | 47/52 (90.4%) |
| date | 133/148 (89.9%) |
| kind | 46/52 (88.5%) |
| module | 185/240 (77.1%) |
| priority | 33/48 (68.8%) |
| status | 200/240 (83.3%) |
| time | 44/48 (91.7%) |
| title | 137/148 (92.6%) |

## Improvement priorities from this run

1. **Native text handling.** Four inputs containing emoji ended in a Windows native C++ exception. Keep these failures separate from language-understanding errors. The exact runtime cause needs investigation before changing input handling.
2. **Unsupported operations.** Some requests to delete/update saved records, control apps or query unavailable data produced new drafts. Detect the requested operation as well as its destination module, and clarify unsupported operations.
3. **Overbroad corrections.** The validator changed a correctly extracted salary transaction into Schedule after seeing “not a calendar event”, and changed a usage query into Memo after seeing a negated reminder. Negation and clause context need to constrain module overrides.
4. **Ambiguity and paraphrases.** A clearly stated date can disappear when only the amount or appointment time is uncertain. Common Health phrasings such as “each application” or “每个软件” are also rejected despite correct model routing.
5. **Extraction format and priority.** The first pass includes five schema-validation failures and two JSON-decoding failures in addition to the four native crashes. Memo priority passed 33/48 annotated checks. These need targeted development tests without rewriting this first-pass score.


## Failure cases

All failed inputs, expected fields and actual responses are retained in [failures.csv](failures.csv). The table below lists counts by the predeclared scenario tag; the categories were not chosen after seeing the model outputs.

| Scenario tag | Inputs | Failed all-field checks |
|---|---|---|
| ambiguous_account | 2 | 1 |
| ambiguous_amount | 2 | 2 |
| ambiguous_date | 6 | 4 |
| ambiguous_priority | 2 | 1 |
| ambiguous_time | 2 | 2 |
| colloquial | 4 | 3 |
| complete | 30 | 4 |
| conflicting_dates | 2 | 1 |
| cross_module | 10 | 2 |
| date_paraphrase | 2 | 2 |
| explicit_module | 8 | 1 |
| extra_note | 2 | 1 |
| midnight_time | 2 | 1 |
| missing_amount | 2 | 1 |
| missing_category | 2 | 1 |
| missing_date | 6 | 1 |
| missing_kind | 2 | 2 |
| mixed_text | 2 | 2 |
| multiple_actions | 14 | 1 |
| negated_module | 2 | 2 |
| negated_reminder | 2 | 2 |
| negated_urgency | 2 | 2 |
| open_module_paraphrase | 2 | 2 |
| paraphrase | 2 | 2 |
| quoted_date | 4 | 3 |
| quoted_instruction | 2 | 1 |
| relative_date | 6 | 2 |
| terse_input | 4 | 1 |
| timezone_boundary | 6 | 1 |
| unsupported_access | 2 | 2 |
| unsupported_action | 2 | 2 |
| unsupported_control | 2 | 2 |
| unsupported_health | 14 | 2 |
| unsupported_mutation | 12 | 12 |
| unsupported_planning | 2 | 2 |
| unsupported_query | 4 | 4 |
| year_boundary | 4 | 1 |

## Reproduction and files

- [Frozen inputs and expectations](../../backend/evaluation/mobile_holdout_v3.json)
- [Protocol, source hashes and fixed settings](protocol.json)
- [Excel workbook](Lifehub-Plus-New-Holdout.xlsx)
- [Chinese 120 cases](chinese-120.csv), [English 120 cases](english-120.csv), [all outputs and checks](results.json)
- [Recorded run provenance](run.json)

Use the pinned model and runtime in the [installation guide](../ANDROID_INSTALL.md). From the repository root:

```powershell
.\scripts\mobile-evaluation\run_holdout.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm' -OutputDirectory '.local/new-holdout-reproduction'
```

The runner verifies frozen source/data hashes, refuses an existing raw output, and uses a fresh conversation per input. Its supervisor preserves native crashes as failures before restarting the engine for remaining inputs. Output goes to the supplied private directory. The app initializes an engine per request. For the exact evaluation snapshot, use the commits referenced above plus the recorded source hashes; changing production code requires a separately labeled run.

The frozen unsupported-action policy expects clarification for requests to send messages or manipulate existing records. Some bare imperative wording can also be read as a description of a future task; this boundary is a limitation of the internally authored labels. Primary scores retain the frozen expectations. Title keywords and manually chosen categories likewise do not substitute for independent human semantic review.

The [earlier 240-case seen-set regression](../evaluation-mobile/REPORT.md) is retained separately. Its optimized score is not a substitute for this first pass, and the two datasets differ in difficulty and outcome mix. Existing Android reliability results were not rerun as part of this model evaluation. After these failures inform future improvements, this set must also be treated as regression data; a fresh frozen set is needed for another unseen claim.
