# Development guide

Lifehub Plus is a native Android application with Memo, Finance, Schedule and Health. The installed app uses **Qwen2.5-1.5B-Instruct int8 through LiteRT-LM 0.10.2** for offline AI. See [Android installation](ANDROID_INSTALL.md) for the signed APK and model setup.

## Local architecture

1. `AiChatViewModel` keeps input, cancellation and draft state consistent across navigation and recreation.
2. `MobileAiApi` runs a fresh local model conversation and sends the result to the packaged Python adapter.
3. `mobile_bridge.py` and `mobile_rules.py` validate and ground the fields using shared code from `backend/`. `action_rules.py` scopes negation and multi-action detection; `text_evidence.py` provides shared clock and number grammars. `mobile_titles.py` preserves an event subject or primary transaction when a generic model title loses it.
4. The user reviews the destination module and editable draft before saving.
5. Memo, Finance and Schedule save in account-specific databases with draft IDs, transactions and read-back checks. Health opens Android usage statistics.

The model cannot write to the databases. The contract allows one reviewable action per request, with missing fields completed by the user. Independent actions should clarify, while a purchase followed by a request to record it is one action. Finance currently uses CNY. Relative dates use the user's timezone. `this/next` weekdays use Monday-based calendar weeks; bare weekdays use the next occurrence, including today. A non-urgent task can still be important, so "not urgent" alone leaves priority unset.

## Build

Install JDK 21, Android SDK 34 and Python 3.11. The Gradle wrapper downloads the configured build dependencies. Android Studio is optional. Set `LIFEHUB_BUILD_PYTHON` if Python is not discoverable automatically.

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The default build includes ARM64 and x86_64; signed distribution builds use ARM64 only. See [release signing](ANDROID_INSTALL.md#build-a-signed-apk-developers). Do not commit signing keys, passwords or model weights.

## Model setup

`MobileModelStore` downloads or imports the pinned 1.6 GB `.litertlm` model. Both paths verify its exact size and SHA-256 before installing it into private storage. A model installation is shared by the app; business data and drafts remain account-specific. Model files are excluded from backups.

The AI screen uses local inference by default and has no automatic paid fallback. Manual entry works before model installation and when inference is unavailable. Unsupported native inference environments show an error instead of attempting to load an incompatible library.

Version 1.1.1 uses `UnicodeSafeMessages` to escape serialized JNI JSON to ASCII. Native JSON decoding restores the original Unicode before inference; the user's text is not stripped or transliterated. The text-only shim is coupled to the pinned LiteRT-LM 0.10.2 conversation handle and fails recoverably if that contract changes. Recheck synchronous/asynchronous supplementary-character inference before changing the SDK, and remove the shim when the upstream boundary supports it. Tool execution stays disabled.

## Tests and evaluation

```powershell
python -m unittest discover -s backend -p 'test_*.py'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
.\scripts\mobile-evaluation\run_v4_regression.ps1 -ModelPath 'C:/models/mobile-qwen2.5.litertlm'
```

The mobile runner uses the same pinned model, prompt, asynchronous text transport, fixed sampling, and packaged validation logic. It writes into `.local/mobile-v4-regression-reproduction`. The host runner reuses one engine with a fresh conversation per input; Android initializes an engine per request. The [current v4 regression](evaluation-mobile-v4-regression/REPORT.md) includes the execution environment, metric definitions, raw outputs and failures. Its 240 inputs informed these fixes, so this is seen-set evidence. Keep the separate 40-case development set out of its denominator.

Application checks are recorded in [Quality checks](QUALITY.md) and [Current verification results](TEST_REPORT.md). CI runs offline checks and builds without downloading models. Device tests use `com.lifeHub.qa`, keeping ordinary app data separate.

`scripts/prepare-holdout-replay.py` prepares an optional Android replay fixture from the current mobile results. It does not run inference or establish a new UI score. After building the test APK, `HoldoutWorkflowTest` can be explicitly enabled with `runHoldoutReplay=true` for a separate workflow-entry run.

## Optional development server

The repository retains the Python HTTP adapter and Ollama integration for development. They are not required by the installed app. `scripts/start-local.ps1` starts the local development service; `backend/.env.example` documents its configuration. Keep it on the development machine. Its model selection is independent of the mobile model, and its runs must not be reported as mobile-model results.

## Data and lifecycle boundaries

- Local login is not remote authentication. There is no cloud sync, cross-device session service or password recovery. Databases are not encrypted.
- Business storage, categories and drafts are account-specific. Pending work remains bound to its original account; late responses cannot populate another account's screen.
- Health describes the whole device and depends on Usage Access and Android event retention.
- Finance amounts use integer minor units. Migrations preserve old values and verify counts and totals before committing.
- Repeated confirmation of the same draft returns its existing record. Independently generated drafts are not deduplicated by content.
- Cancellation preserves input. Process restoration does not silently replay a request. Schedule still has some main-thread SQLite work worth moving behind a repository.

## Preserved first-pass evidence

The [v4 first-pass protocol](evaluation-mobile-holdout-v4/protocol.json) freezes 120 Chinese and 120 English inputs, source hashes and expectations. Use `run_frozen.ps1` on its historical freeze revision. Version 1.1.3 was developed using those failures and saved-output replay; its fresh model run is reported separately as regression evidence. A future untouched set is needed to assess generalization.

The [original v3 first pass](evaluation-mobile-holdout/REPORT.md) and [v3 post-fix regression](evaluation-mobile-regression/REPORT.md) remain unchanged. Their reproduction scripts intentionally reject the current title-modified sources: check out the historical revision named in each report before using `run_holdout.ps1` or `run_regression.ps1`. Every frozen runner verifies source/data hashes and retains interrupted native calls as failures.
