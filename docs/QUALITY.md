# Quality checks

The current AI configuration is Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. Version 1.1.2 improves title recovery. Its [new frozen v4 first pass](evaluation-mobile-holdout-v4/REPORT.md) is separate from preserved historical evaluations.

| Check | Recorded result | Scope |
|---|---|---|
| Python validation | 107/107 | Schema, title recovery, operation boundaries, dates, scoring and API contracts |
| Android/JVM unit tests | 20/20 | Unicode serialization, dates, money, charts, passwords and usage aggregation |
| Android reliability | 23/23 | API 34 isolated emulator; account isolation, cancellation, review, persistence and packaged title repair |
| Frozen v4 first pass | Chinese 120 + English 120 | New inputs, frozen expectations, no tuning during inference; [results](evaluation-mobile-holdout-v4/REPORT.md) |
| v3 title replay | 240/240 all fields | Revalidation of previously captured outputs, no fresh inference; [evidence](evaluation-mobile-holdout-v4/title-replay.json) |
| v3 post-fix regression | 238/240 all fields | Fresh generation on now-seen inputs; [results](evaluation-mobile-regression/REPORT.md) |
| v3 original first pass | 162/240 all fields | Preserved original results; [report](evaluation-mobile-holdout/REPORT.md) |
| Signed APK | Signature, update installation and login launch checked | ARM64, version 1.1.2 |

See [current evidence and commands](evaluation-mobile-holdout-v4/reliability.json). Model-field scores are separate from database and UI checks. Internally authored Chinese/English scenarios overlap, and title scoring uses declared keywords. The original 40-case development set remains separate.

GitHub Actions runs Python checks and Android builds/unit tests without downloading model weights or calling model APIs. Instrumentation uses the separate `com.lifeHub.qa` package.

The v4 protocol locks source/data hashes before inference. Failures stay in the first-pass denominator. If these results inform future changes, subsequent runs on v4 are regression tests. Original first-pass and regression artifacts are never overwritten.
