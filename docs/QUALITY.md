# Quality checks

Lifehub Plus 1.1.3 uses Qwen2.5-1.5B-Instruct int8 with LiteRT-LM 0.10.2. This page presents the latest recorded results.

| Check | Result | Scope |
|---|---|---|
| Python validation | 125/125 | Schema, language boundaries, titles, dates, scoring and API contracts |
| Android/JVM unit tests | 20/20 | Unicode serialization, money, charts, passwords and usage aggregation |
| Android integration | 25/25 | Isolated API 34 emulator; account isolation, cancellation, confirmation, persistence and packaged language rules |
| Unseen model holdout | Chinese 60 + English 60 | First pass: routing 104/120; required fields 197/216; all checks 85/120 |
| Model regression | Chinese 120 + English 120 | Routing 240/240; required fields 432/432; all specified fields 238/240 |
| Signed APK | Verified | ARM64 version 1.1.3; matching update signature and login launch |

See the [unseen holdout report](evaluation-unseen-v5/REPORT.md), [regression evaluation](evaluation-mobile-v4-regression/REPORT.md) and [verification commands](evaluation-mobile-v4-regression/reliability.json). The model run used Windows CPU. These are application-pipeline scores, including deterministic validation. The 240-case set is seen regression; the 120-case set records a separate unseen first pass. Engineering checks listed above predate the holdout and were not rerun for it. Model-field checks are separate from database and UI tests. The regression retains two priority disagreements; the holdout retains all 35 cases with failed checks.

GitHub Actions runs Python checks and Android builds/unit tests without downloading models. Instrumentation uses `com.lifeHub.qa`, separate from ordinary app data. The 40-case development set is excluded from the model-evaluation denominator.
