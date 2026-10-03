# Quality checks

The current AI configuration is Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. Version 1.1.1 fixes Unicode transport and drafting boundaries. Its [fresh post-fix regression](evaluation-mobile-regression/REPORT.md) is separate from the unchanged [original first pass](evaluation-mobile-holdout/REPORT.md).

| Check | Recorded result | Scope |
|---|---|---|
| Python validation | 99/99 | Schema validation, operation boundaries, input grounding, dates, scoring and server contracts |
| Android/JVM unit tests | 20/20 | Unicode serialization, dates, money, charts, passwords and usage aggregation |
| Android reliability | 22/22 | API 34 isolated emulator; account isolation, cancellation, review, persistence and packaged validation |
| Post-fix mobile regression | Chinese 120 + English 120 | Fresh generation on now-seen inputs; [results](evaluation-mobile-regression/REPORT.md) |
| New mobile holdout | Chinese 120 + English 120 | Frozen first pass; [results](evaluation-mobile-holdout/REPORT.md) |
| Earlier mobile regression | Chinese 120 + English 120 | Windows CPU, same model file and inference-library version; model plus deterministic validation |
| Signed APK | Signature, update installation and login launch checked | ARM64, version 1.1.1 |

See [current check evidence](evaluation-mobile-regression/reliability.json) and the [historical test commands](TEST_REPORT.md). Model-field scores are separate from database and UI checks. Both bilingual sets are now seen regression sets. The holdout's original first pass remains preserved as historical evidence. The 40-case development set is reported separately.

GitHub Actions runs Python checks and Android builds/unit tests. It does not download model weights or call model APIs. Instrumentation uses the separate `com.lifeHub.qa` package.

The original holdout preserves its native-process failures. The post-fix run reports fresh outputs and raw model routing separately from application corrections. The application checks above were rerun for 1.1.1 and are not counted as model accuracy.
