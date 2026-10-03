# Quality checks

The current AI configuration is Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. Its results are recorded in the [mobile-model report](evaluation-mobile/REPORT.md).

| Check | Recorded result | Scope |
|---|---|---|
| Python validation | 83/83 | Schema validation, input grounding, dates, scoring and server contracts |
| Android/JVM unit tests | 17/17 | Dates, money, charts, passwords and usage aggregation |
| Android reliability | 20/20 | API 34 isolated emulator; account isolation, cancellation, review, persistence and packaged validation |
| Mobile-model regression | Chinese 120 + English 120 | Windows CPU, same model file and inference-library version; model plus deterministic validation |
| Signed APK | Signature, installation and login launch checked | ARM64, version 1.1.0 |

See [recorded check evidence](evaluation-mobile/reliability.json) and the [test commands](TEST_REPORT.md). Model-field scores are separate from database and UI checks. The bilingual cases are a seen regression set, not an independent holdout. The 40-case development set is reported separately.

GitHub Actions runs Python checks and Android builds/unit tests. It does not download model weights or call model APIs. Instrumentation uses the separate `com.lifeHub.qa` package.
