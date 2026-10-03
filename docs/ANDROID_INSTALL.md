# Install Lifehub Plus on Android

Lifehub Plus is a native Android application. Android Studio and an emulator are development tools, not requirements for using an APK on a phone.

## Offline AI setup

The mobile implementation uses **Qwen2.5-1.5B-Instruct, int8, 4096-token context**, through **LiteRT-LM 0.10.2**. Inference and draft validation run inside the application. No Ollama server, API key or paid cloud fallback is used by the mobile AI screen.

1. Install the signed ARM64 APK on a compatible Android phone. If Android asks, allow installation from the browser or file manager used to open the APK.
2. Register a local account and open **AI**.
3. Choose **Download offline model**. The pinned model is 1,597,931,520 bytes (about 1.6 GB). Use Wi-Fi and allow at least 2 GB of free storage for installation.
4. Alternatively, transfer the exact model file to the phone and choose **Import downloaded model**. Both paths verify its size and SHA-256 before installation.
5. After setup, disconnect from the internet and generate a draft. Review, edit and confirm before saving. Without a model, all four modules remain available for manual use.

Downloads can be cancelled. Switching screens does not start another download. Interrupted downloads must be retried from the beginning. Model files are kept out of Android backups. A recent 64-bit Android phone with 8–12 GB RAM is the target configuration. Evaluation results identify their actual execution environment in the [model report](evaluation-mobile/REPORT.md).

### Model provenance

- [Model card and Apache 2.0 license](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct)
- Revision: `19edb84c69a0212f29a6ef17ba0d6f278b6a1614`
- File: `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm`
- SHA-256: `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`
- [Pinned download](https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm)

The model is downloaded separately and is not committed to Git. Network/data, storage and electricity costs still depend on the user's device and plan; there are no model API usage charges.

## Build a signed APK (developers)

Install JDK 21, Android SDK 34 and Python 3.11. Android Studio is optional if these build tools are already available. Gradle downloads the pinned Android inference and embedded validation runtimes. Set `LIFEHUB_BUILD_PYTHON` if Python is not discoverable automatically.

Supply your own signing keystore through these local environment variables, then run the build script:

```powershell
$env:LIFEHUB_KEYSTORE = 'C:/private/lifehub-release.jks'
$env:LIFEHUB_KEY_ALIAS = 'lifehub'
# Set LIFEHUB_STORE_PASSWORD and LIFEHUB_KEY_PASSWORD locally; never commit them.
.\scripts\build-apk.ps1
```

The output is `.local/distribution/Lifehub-Plus-1.1.0.apk` with `SHA256SUMS.txt`. Keep the keystore and passwords backed up privately: future updates must use the same signing identity. A release signed with a new key cannot update an existing debug-signed installation; preserve existing data before changing installation identity.

The historical desktop Qwen3 4B evaluation is not a score for this mobile model. New measurements must identify the model, runtime, dataset and actual execution device.
