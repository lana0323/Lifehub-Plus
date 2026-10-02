# AI action validation improvements

Lifehub Plus now recovers clear financial categories/accounts from bilingual input, keeps uncertain dates unset, and asks the user to split explicit multiple commands. Health requests are checked against its supported phone/app usage workflow. Unsupported Health requests have an actionable English/Chinese message and retain the user's editable input.

All records still require review and confirmation in the existing forms. No rule creates records automatically. Missing amounts stay missing; negative, zero and overprecise amounts are not converted to valid positive amounts. Unsupported foreign currencies receive clarification.

## Development results

The original frozen v1 results are preserved unchanged. **The 120 cases are now a development regression set**, since their outcomes informed these fixes. These results must not be presented as new unseen accuracy.

| Backend metric on the same 120 cases | Original first pass | Updated regression |
|---|---:|---:|
| Exact status/module routing | 107/120 | 120/120 |
| Required-field checks | 238/268 | 267/268 |
| All specified checks | 88/120 | 119/120 |
| Supported draft contract | 82/104 | 103/104 |
| Finance draft contract | 11/26 | 26/26 |
| Clarification handling | 6/16 | 16/16 |

These are backend checks, including expected nulls and frozen title keywords. They do not prove every draft is ready to save. The new 120 outputs were **not** all replayed through Android or inserted into databases. The previous report's Android workflow score must not be relabeled with these backend numbers.

The earlier 40-case development suite passed **35/40**, up from the previously recorded **28/40**. Its remaining failures are retained: `memo-05`, `memo-07`, `finance-07`, `finance-08`, `health-03`. They include routing, missing transaction type and ungrounded model date/time output. Grounding failures remain visible errors rather than silently inventing fields.

The remaining 120-case failure is `memo-en-09`: the model places the book-exchange idea in notes while returning a generic title. It fails the frozen title-keyword check; its content is not lost. No expectation was relaxed to make it pass.

## Verification

- Backend automated tests: **58/58 passed**, including ambiguous dates despite confident model output, equivalent Chinese/ISO dates, unrelated invented dates, category/account ambiguity, signed/overprecise amounts, punctuation, foreign currencies and multi-command boundaries.
- Android connected tests: **13/13 passed**, isolated package `com.lifeHub.qa`. Covers specific clarification messages, retained/editable input, cancellation, stale responses, recreation, account isolation, duplicate confirmations and persistence/read-back.
- Final real backend HTTP smoke: **4/4 passed** for comma-terminated amounts, ambiguous dates, multiple commands and unsupported Health logging.
- Normal Android debug APK built and installed over the existing application without clearing its data. The local backend was restarted with the updated implementation.

## Measurement history and limits

Two development iterations each ran one sequential pass over 120 + 40 inputs, with no per-case retry. The first candidate appended guidance to the model prompt and scored 116/120 and 33/40, but introduced ordinary-task routing regressions. Its reports are retained as `candidate-prompt-120.json` and `candidate-prompt-40.json`. The selected implementation retains the original prompt and adds bounded validation/recovery rules.

The recorded-output check passed 119/120 with no regressions among previously passing cases. This only isolates post-processing improvements; it is not fresh inference or Android testing.

After the full passes, one amount-boundary correction allowed a punctuation comma while still rejecting a truncated thousands group. The full suite of 58 backend tests and four real HTTP requests verified that final correction; the full 160 inputs were not repeated for it. Both full-pass and final source hashes are recorded in [environment.json](environment.json).

Runtime: local Ollama 0.35.0, Qwen3 4B Q4_K_M, temperature 0, seed 42, context 4096, generation cap 700, thinking disabled. Hardware is the same i5-12400F / RTX 3060 Ti / Windows machine documented in the original report. Models were warm for the selected pass; rule-only responses make fewer model calls than inputs. Gradle and emulator checks overlapped part of the run. P50/P95 are preserved in raw results, but **no latency improvement claim** is made.

The rules use bounded vocabularies, not general semantic understanding. Ambiguous categories/accounts remain editable, uncommon expressions may still need correction, and the current Health workflow is limited to device usage. A newly frozen unseen set is required for a future generalization claim.

## Reproduce

```sh
python -m unittest discover -s backend -p 'test_*.py'
python backend/evaluate_actions.py --live --cases backend/evaluation/holdout_v1.json --output regression-new-run.json
python backend/evaluate_actions.py --live --output development-new-run.json
```

Android: run `:app:connectedDebugAndroidTest` with `android.testInstrumentationRunnerArguments.class` set to `com.lifeHub.ai.AccountAndWaitingTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.IdempotentRecordsTest,com.lifeHub.ai.TaskConfirmationTest`. Connected tasks automatically use the isolated QA package.

Artifacts: [case comparison CSV](comparison.csv), [120-case live regression](regression-120.json), [40-case live regression](development-40.json), [recorded-output regression](recorded-output-regression.json), [Android checks](android-reliability.json), [HTTP smoke](http-smoke.json), [summary](summary.json). The [original frozen report](../evaluation/REPORT.md) remains the historical first pass.
