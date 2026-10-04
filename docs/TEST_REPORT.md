# Current verification results

Version **1.1.3**. Current model: **Qwen2.5-1.5B-Instruct int8**, **LiteRT-LM 0.10.2**.

| Check | Command / procedure | Recorded result |
|---|---|---|
| Python validation | `python -m unittest discover -s backend -p 'test_*.py'` | 125/125 |
| Android/JVM unit tests | `gradlew --no-daemon -PisolatedTests=true testDebugUnitTest assembleDebug assembleDebugAndroidTest` | 20/20 |
| Android integration | Isolated API 34 emulator; classes and command below | 25/25 |
| Signed APK | Verify signature, install the ARM64 update and launch | Signature verified; login screen opened |
| Model regression | `scripts/mobile-evaluation/run_v4_regression.ps1` with the pinned model | 240 inputs; 238/240 all specified fields |

Model inference ran on Windows CPU. The 240 inputs informed development; these are regression results, not unseen-model accuracy. No new inference was performed during the subsequent report cleanup. Raw outputs, expectations and metrics are unchanged.

## Android integration

| Class | Passed | Coverage |
|---|---|---|
| MobileAdapterTest | 8 | Packaged language rules, model setup failures and Unicode transport |
| AccountAndWaitingTest | 7 | Cancellation, stale responses, recreation and account boundaries |
| IdempotentRecordsTest | 3 | Repeated saves, transactions and read-back |
| RepeatedReviewTest | 2 | Repeated confirmation through the review flow |
| TaskConfirmationTest | 1 | Editing, confirmation and persisted fields |
| DatabaseRegressionTest | 4 | Migrations, account storage and password upgrade |

The complete instrumentation command, environment and APK checksum are in [reliability.json](evaluation-mobile-v4-regression/reliability.json).

## Results

[Report](evaluation-mobile-v4-regression/REPORT.md) · [Excel](evaluation-mobile-v4-regression/Lifehub-Plus-V4-Regression.xlsx) · [Chinese 120](evaluation-mobile-v4-regression/chinese-120.csv) · [English 120](evaluation-mobile-v4-regression/english-120.csv) · [Raw outputs and checks](evaluation-mobile-v4-regression/results.json)
