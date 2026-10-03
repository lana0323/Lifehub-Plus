# Mobile-model regression results

Evaluated on October 2, 2026.

**Model:** Qwen2.5-1.5B-Instruct int8, ekv4096. **Execution:** Windows 11, Intel i5-12400F, CPU, JVM LiteRT-LM 0.10.2, JDK 21.0.7, Python 3.11.7. Scoring uses tzdata 2026.2, matching the APK. The model file and model prompt match the Android implementation. Android application checks are listed separately.

This is an **application-pipeline regression evaluation**: model extraction plus deterministic input grounding and validation. The 240 cases were used in previous evaluations, so these results are not an unseen holdout score. The input set and expected answers were not changed.

## Accuracy

| Metric | Chinese | English | Combined |
|---|---|---|---|
| Module routing | 120/120 (100.0%) | 120/120 (100.0%) | 240/240 (100.0%) |
| Required fields | 260/261 (99.6%) | 261/261 (100.0%) | 521/522 (99.8%) |
| All specified fields | 119/120 (99.2%) | 120/120 (100.0%) | 239/240 (99.6%) |
| Supported draft contract | 97/98 (99.0%) | 98/98 (100.0%) | 195/196 (99.5%) |

- Routing requires both the expected response status and destination module.
- Required-field accuracy counts individual annotated fields, including values deliberately left unset for manual completion. It does not mean every input is ready to save.
- All-specified-field accuracy requires every annotated check for an input to pass. Title checks use the fixed dataset's required words; unannotated details are not comprehensively judged.
- Supported draft contract excludes requests whose expected outcome is clarification. It checks the reviewable draft; it is not a count of records written to the database.

## Before and after validation refinements

The first pass used frozen configuration [protocol.json](protocol.json), source snapshot `4ac23d1`, one attempt per input and no retries. The captured native model outputs were then replayed through the final validation rules. No second model-generation pass or manual correction of responses was used to obtain the refined scores. Deterministic preflight checks also handle unsupported Health logging before model generation in the app.

| Metric | Frozen first pass | Final validation replay |
|---|---|---|
| Module routing | 219/240 (91.2%) | 240/240 (100.0%) |
| Required fields | 484/522 (92.7%) | 521/522 (99.8%) |
| All specified fields | 200/240 (83.3%) | 239/240 (99.6%) |
| Supported draft contract | 159/196 (81.1%) | 195/196 (99.5%) |

Changes address explicit task routing, negated transaction context, missing payment accounts, supported usage-query vocabulary, unsupported Health logging, grounded event metadata, and financial descriptions/categories. No rule looks up a case ID or expected answer.

## Workflow breakdown

| Workflow group | Routing | All specified fields | Supported draft contract |
|---|---|---|---|
| Finance | 60/60 (100.0%) | 60/60 (100.0%) | 52/52 (100.0%) |
| Health | 60/60 (100.0%) | 60/60 (100.0%) | 36/36 (100.0%) |
| Memo | 60/60 (100.0%) | 59/60 (98.3%) | 53/54 (98.1%) |
| Schedule | 60/60 (100.0%) | 60/60 (100.0%) | 54/54 (100.0%) |

Each workflow group contains 60 inputs, split evenly between Chinese and English. The groups include complete, incomplete, ambiguous and unsupported requests.

## Application checks

| Check | Passed | Environment / scope |
|---|---|---|
| Python validation | 83/83 | Offline unit checks |
| Android/JVM unit checks | 17/17 | Gradle testDebugUnitTest |
| Android reliability | 20/20 | API 34 isolated x86_64 emulator; persistence, account isolation, cancellation, review and packaged validation |
| Signed APK | Verified | ARM64, com.lifeHub, version 1.1.0; signature, installation and login-screen launch checked |

The packaged validation subset is included in the reliability count and is not counted twice. Model accuracy does not include user edits or UI/database-test outcomes. The 40-case development set remains for debugging: [development results](development-results.json).

## Remaining failures

| Case | Checks not satisfied |
|---|---|
| v2-memo-zh-24 | title |

A fresh independent dataset is needed to measure generalization after these refinements. Generated content remains editable and requires user confirmation before saving.

## Data and reproduction

- [Excel workbook](Lifehub-Plus-Mobile-Evaluation.xlsx)
- [Chinese 120-case CSV](chinese-120.csv) and [English 120-case CSV](english-120.csv)
- [Final outputs and per-field checks](results.json) and [frozen first-pass outputs](first-pass-results.json)
- [Protocol and source hashes](protocol.json), [refinement source hashes](refinement.json), [application-check evidence](reliability.json)
- [Unchanged 240-case input set](../../backend/evaluation/holdout_v2.json)

Use JDK 21, Python 3.11, and the [pinned mobile model](../ANDROID_INSTALL.md). The runner installs tzdata 2026.2 into its private output directory. From the repository root:

```powershell
.\scripts\mobile-evaluation\run.ps1 -ModelPath "C:/models/mobile-qwen2.5.litertlm"
```

The script downloads pinned JVM dependencies, creates a fresh conversation for each request and uses the app's current prompt/validation adapter. Output goes to `.local/mobile-reproduction`. For the historical first pass, check out source snapshot `4ac23d1`. The Android implementation creates an engine per request; the host runner reuses an engine with a fresh conversation per case.
