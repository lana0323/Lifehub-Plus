# App preview captures

Captured on October 2, 2026 from the current application source, using the Medium Phone API 36.1 emulator (1080 × 2400, English locale). These are direct Android screenshots, not mockups. The PNG icon is copied unchanged from `app/src/main/res/drawable/life_hub_icon.png`.

The opt-in `ReadmePreviewTest` runs only with the isolated `com.lifeHub.qa` application ID. Its fictional Alex account contains three memos, five financial records and one scheduled event. The normal `com.lifeHub` installation and its account data are untouched. Health shows actual emulator usage, including periods with no available history; no usage totals are seeded or fabricated.

To refresh the six previews with an emulator connected (PowerShell):

```powershell
./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PisolatedTests=true
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell appops set com.lifeHub.qa GET_USAGE_STATS allow
adb shell cmd locale set-app-locales com.lifeHub.qa --user 0 --locales en
adb shell am instrument -w -e class com.lifeHub.ui.ReadmePreviewTest -e captureReadme true com.lifeHub.qa.test/com.lifeHub.LifeHubTestRunner
```

Screenshots are written to the isolated application's `files/readme-{home,ai,memo,finance,schedule,health}.png`. Export each with `adb exec-out run-as com.lifeHub.qa cat files/readme-NAME.png` through a binary-safe stream (for example, Python's `subprocess.run(..., capture_output=True).stdout`); older PowerShell redirection can corrupt PNG bytes. Inspect every image before replacing the public assets. Capture dates, content and usage totals will naturally change on a later run.

After export, uninstall only `com.lifeHub.qa.test` and `com.lifeHub.qa`. The screenshot fixture is opt-in and is not included in the normal app.
