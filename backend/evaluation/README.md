# Evaluation inputs

The current mobile-model regression uses **`holdout_v2.json`**: 240 inputs, split into 120 Chinese and 120 English, with 30 cases per workflow in each language. Its filename is retained for stable hashes. These cases have already been used in development and are no longer an unseen holdout.

- [Current model report](../../docs/evaluation-mobile/REPORT.md)
- [Frozen mobile configuration](../../docs/evaluation-mobile/protocol.json)
- [Refinement provenance](../../docs/evaluation-mobile/refinement.json)
- [Chinese results](../../docs/evaluation-mobile/chinese-120.csv) and [English results](../../docs/evaluation-mobile/english-120.csv)

`../eval_actions_fields.json` is the separate 40-case development set. Older input fixtures and their original locks remain for scorer compatibility; they are not current model results. Do not update a frozen lock or alter expected answers after inspecting outputs.

Routing checks exact status and destination module. Required-field accuracy counts the annotated field checks, including expected nulls. All-specified-field accuracy requires all annotated checks for an input to pass. Title checks use predeclared words and do not comprehensively judge unannotated meaning. Database and UI reliability are measured separately.

From the repository root, run `./scripts/mobile-evaluation/run.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm'`. A new independent evaluation requires a new frozen set before inference; rerunning the existing cases remains regression testing.
