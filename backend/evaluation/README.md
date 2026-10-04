# Evaluation inputs

## Current mobile holdout

`mobile_holdout_v4.json` contains 240 new inputs, Chinese 120 and English 120, frozen after the title-recovery change and before model inference. Each language has 30 cases per workflow. The 180 expected drafts and 60 expected clarifications cover colloquial wording, corrected typos, negation, mixed languages, missing fields, invalid amounts and dates.

See the [v4 first-pass report](../../docs/evaluation-mobile-holdout-v4/REPORT.md) and [protocol](../../docs/evaluation-mobile-holdout-v4/protocol.json). Run `./scripts/mobile-evaluation/run_frozen.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm'` at the report's freeze revision. This internally authored set is not an independent external benchmark. Subsequent changes informed by its results require a separate regression label.

## Preserved v3 first pass

`mobile_holdout_v3.json` contains 240 newly authored inputs: 120 Chinese and 120 English, with 30 per workflow per language. Inputs, expected answers and scoring were frozen before its first model run. There are 180 expected drafts and 60 expected clarifications. No production tuning or answer edits were made during the run.

See the [first-pass report](../../docs/evaluation-mobile-holdout/REPORT.md), [frozen protocol](../../docs/evaluation-mobile-holdout/protocol.json), and [run provenance](../../docs/evaluation-mobile-holdout/run.json). This is an internally authored set, not a third-party benchmark. Once its failures guide changes, future runs become regression tests.

Run `./scripts/mobile-evaluation/run_holdout.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm'` at the historical freeze revision named in that report. The runner verifies source/data hashes and records interrupted native calls as failures without retrying them. Use a fresh output directory for each independent run. The [v3 regression](../../docs/evaluation-mobile-regression/REPORT.md) is also preserved unchanged.

## Development and regression inputs

`holdout_v2.json` is the earlier 240-input seen regression set. Its stable filename and original answers remain unchanged; [results](../../docs/evaluation-mobile/REPORT.md) are kept separate from the new first pass. `../eval_actions_fields.json` is the 40-case development set.

Older input fixtures and original locks remain for scorer compatibility. They are not current model results. Do not change a frozen lock or rewrite expected answers after inspecting outputs.

Routing checks expected status/module. Required-field scoring includes annotated null values for missing or ambiguous fields. All-specified-field scoring requires all annotated checks to pass. Titles use predeclared words, not comprehensive semantic review. UI entry, persistence and reliability are measured separately.
