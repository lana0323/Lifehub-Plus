# Frozen evaluation v1

The original production source and first-pass report are preserved at commit `e107d7b` in Lifehub-Plus. Later parser improvements intentionally fail the v1 source-hash gate. To reproduce that historical evaluation, use a separate checkout of that commit; do not update the lock to match newer source. Updated development results live in `docs/evaluation-improvements/`.

`holdout_v1.json` contains 120 newly authored internal evaluation prompts: 30 per workflow, 15 English and 15 Chinese within each workflow. `holdout_v1.lock.json` records the pre-run SHA-256, production source hashes, scoring rules, run count and fixed shuffle order.

The earlier `../eval_actions_fields.json` remains a **development / regression set**. Do not combine its score with this evaluation.

The v1 dataset had not been used for tuning when its first pass was executed. This is an internally authored holdout, not an external independent benchmark: bilingual scenarios can be correlated, title checks use predefined keywords, and exact prompt deduplication cannot establish semantic independence. Once results are inspected, future runs on v1 are regression runs. Use a new frozen set for future claims of unseen evaluation.

## Scoring

- Routing: exact expected status and module. Supported requests and clarification requests are reported separately as well as together.
- Required-field accuracy: micro average over frozen field expectations needed by the workflow. Correctly preserving a missing value as null counts as correct; it does not mean the draft is ready to save. Missing JSON keys fail. Complete-input results are also reported separately.
- Workflow entry: all specified fields must match, then the real recorded response must pass the Android ViewModel and open the correct review form or Health page. Replay uses the real UI with an injected API response, not a second model attempt. It does not measure mobile network behavior or prove that every draft was saved.
- Clarification handling: reported separately; a clarification is not a successful supported action.
- Latency: one sequential inference plus backend-validation measurement per prompt, nearest-rank P50/P95, excluding one warmup, no retries. This is not full mobile interaction latency.
- Reliability: independent Android checks for duplicate saves, late responses, account isolation and confirmation/read-back, reported as passed/tested.

Run `python backend/evaluate_holdout.py --live` for a first pass. It refuses to overwrite existing first-pass results. The runner uses the same production parser, prompt, schema and local model options; no paid fallback. The fixed `now` and timezone in each case make relative dates reproducible without changing the model prompts.

Android replay is opt-in through `runHoldoutReplay=true`; its fixture is generated from the frozen case file and recorded model report. Raw outputs and failed cases remain in the report. Never edit v1 answers after observing results.
