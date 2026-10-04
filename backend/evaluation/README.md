# Evaluation inputs

`mobile_holdout_v4.json` is the current 240-input regression set: 120 Chinese and 120 English inputs, 30 per workflow in each language, with 180 expected drafts and 60 expected clarifications. The set covers colloquial wording, negation, mixed languages, missing fields, invalid amounts and dates.

Version 1.1.3 used these inputs during development. Its [current results](../../docs/evaluation-mobile-v4-regression/REPORT.md) are seen-set regression evidence. The inputs and expected answers are unchanged. Run `scripts/mobile-evaluation/run_v4_regression.ps1` with the pinned model to reproduce the current evaluation in a fresh output directory.

`../eval_actions_fields.json` is the separate 40-case development set. Additional input fixtures remain for development and scorer compatibility, not as published model results.

Routing checks response status/module. Required-field scoring includes predeclared null values for missing or ambiguous fields. All-specified-field scoring requires every annotated check to pass. Titles use predeclared words rather than full semantic review. UI entry, persistence and reliability are measured separately.
