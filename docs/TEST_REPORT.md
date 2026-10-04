# Earlier verification results

For version 1.1.3, see the [current model regression](evaluation-mobile-v4-regression/REPORT.md) and [application verification counts and commands](evaluation-mobile-v4-regression/reliability.json). The dated results below are preserved as earlier evidence.

Recorded on October 2, 2026. The current model is **Qwen2.5-1.5B-Instruct int8**, using **LiteRT-LM 0.10.2**.

| Check | Command / procedure | Recorded result |
|---|---|---|
| Python validation | `python -m unittest discover -s backend -p 'test_*.py'` | 83/83 |
| Android/JVM tests | `./gradlew :app:testDebugUnitTest` | 17/17 |
| Android reliability | Build isolated QA APKs, then run the six classes below | 20/20 |
| Signed APK | `apksigner verify --verbose --print-certs`, install the ARM64 APK and launch it | Verified signature; login screen opened |
| Bilingual model regression | `./scripts/mobile-evaluation/run.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm'` | 240 inputs; [full results](evaluation-mobile/REPORT.md) |

The recorded model generation ran on Windows CPU. Final validation scores replay the same captured outputs through improved deterministic rules. They do not represent another model-generation pass, a fresh holdout or Android inference measurements.

## Android reliability

| Class | Passed | Coverage |
|---|---|---|
| MobileAdapterTest | 3 | Packaged Python adapter, clarification and grounded fields |
| AccountAndWaitingTest | 7 | Cancellation, stale responses, recreation and account boundaries |
| IdempotentRecordsTest | 3 | Repeated saves, transaction behavior and read-back |
| RepeatedReviewTest | 2 | Repeated confirmation through the review flow |
| TaskConfirmationTest | 1 | Editing, confirmation and persisted fields |
| DatabaseRegressionTest | 4 | Migration, account storage and password upgrade |

Run against a separate test device or emulator:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest '-PisolatedTests=true'
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class 'com.lifeHub.ai.MobileAdapterTest,com.lifeHub.ai.AccountAndWaitingTest,com.lifeHub.ai.IdempotentRecordsTest,com.lifeHub.ai.RepeatedReviewTest,com.lifeHub.ai.TaskConfirmationTest,com.lifeHub.ai.DatabaseRegressionTest' com.lifeHub.qa.test/com.lifeHub.LifeHubTestRunner
```

The recorded run used an API 34 x86_64 emulator for these application checks. The ARM64 release installation was checked separately using its ARM compatibility layer. Neither check is included in the model's accuracy denominator.

## Data files

- [Excel workbook](evaluation-mobile/Lifehub-Plus-Mobile-Evaluation.xlsx)
- [Chinese 120-case CSV](evaluation-mobile/chinese-120.csv) and [English 120-case CSV](evaluation-mobile/english-120.csv)
- [Model outputs and checks](evaluation-mobile/results.json)
- [First-pass outputs](evaluation-mobile/first-pass-results.json) and [refinement provenance](evaluation-mobile/refinement.json)
- [Reliability and APK evidence](evaluation-mobile/reliability.json)

No user edits are counted toward model-field accuracy. Correct draft generation is not a claim that all 240 inputs were written to the database. Saving remains conditional on explicit user confirmation.
