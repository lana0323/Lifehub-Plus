# Quality checks

The current AI configuration is Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. Its new first-pass results are recorded in the [mobile holdout report](evaluation-mobile-holdout/REPORT.md); earlier seen-set results remain in the [regression report](evaluation-mobile/REPORT.md).

| Check | Recorded result | Scope |
|---|---|---|
| Python validation | 83/83 | Schema validation, input grounding, dates, scoring and server contracts |
| Android/JVM unit tests | 17/17 | Dates, money, charts, passwords and usage aggregation |
| Android reliability | 20/20 | API 34 isolated emulator; account isolation, cancellation, review, persistence and packaged validation |
| New mobile holdout | Chinese 120 + English 120 | Frozen first pass; [results](evaluation-mobile-holdout/REPORT.md) |
| Earlier mobile regression | Chinese 120 + English 120 | Windows CPU, same model file and inference-library version; model plus deterministic validation |
| Signed APK | Signature, installation and login launch checked | ARM64, version 1.1.0 |

See [recorded check evidence](evaluation-mobile/reliability.json) and the [test commands](TEST_REPORT.md). Model-field scores are separate from database and UI checks. The earlier bilingual cases are a seen regression set. The new internal holdout was frozen before its first pass and is reported separately. The 40-case development set is reported separately.

GitHub Actions runs Python checks and Android builds/unit tests. It does not download model weights or call model APIs. Instrumentation uses the separate `com.lifeHub.qa` package.

The new holdout preserves native-process failures and reports raw model routing separately from application corrections. The application-check counts above are prior evidence, not newly rerun checks in that evaluation.
