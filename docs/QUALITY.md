# Quality checks

The current AI configuration is Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. Version 1.1.3 improves negation, multi-action handling, mixed-language fields and shared date/time parsing. Its [fresh v4 regression](evaluation-mobile-v4-regression/REPORT.md) is separate from the preserved [first pass](evaluation-mobile-holdout-v4/REPORT.md).

| Check | Recorded result | Scope |
|---|---|---|
| Python validation | 125/125 | Schema, language counterexamples, title recovery, operation boundaries, dates, scoring and API contracts |
| Android/JVM unit tests | 20/20 | Unicode serialization, dates, money, charts, passwords and usage aggregation |
| Android reliability | 25/25 | API 34 isolated emulator; account isolation, cancellation, review, persistence and packaged language rules |
| Current v4 regression | Chinese 120 + English 120 | Seen inputs with fresh generation; unchanged expectations and scoring; [results](evaluation-mobile-v4-regression/REPORT.md) |
| Frozen v4 first pass | Chinese 120 + English 120 | New inputs, frozen expectations, no tuning during inference; [results](evaluation-mobile-holdout-v4/REPORT.md) |
| v3 title replay | 240/240 all fields | Revalidation of previously captured outputs, no fresh inference; [evidence](evaluation-mobile-holdout-v4/title-replay.json) |
| v3 post-fix regression | 238/240 all fields | Fresh generation on now-seen inputs; [results](evaluation-mobile-regression/REPORT.md) |
| v3 original first pass | 162/240 all fields | Preserved original results; [report](evaluation-mobile-holdout/REPORT.md) |
| Signed APK | Signature, update installation and login launch checked | ARM64, version 1.1.3 |

See [current evidence and commands](evaluation-mobile-v4-regression/reliability.json). Model-field scores are separate from database and UI checks. Internally authored Chinese/English scenarios overlap, and title scoring uses declared keywords. The original 40-case development set remains separate.

GitHub Actions runs Python checks and Android builds/unit tests without downloading model weights or calling model APIs. Instrumentation uses the separate `com.lifeHub.qa` package.

The current protocol locks source/data hashes before inference. Version 1.1.3 was developed using v4 failures and saved-output replay, so its later v4 run is regression evidence. Original first-pass and regression artifacts are never overwritten. The conservative interpretation of "not urgent" differs from two frozen expected priorities and remains counted as a failure.
