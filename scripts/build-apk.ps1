$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
foreach ($name in @('LIFEHUB_KEYSTORE','LIFEHUB_STORE_PASSWORD','LIFEHUB_KEY_ALIAS','LIFEHUB_KEY_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Set $name before building a signed release. See docs/ANDROID_INSTALL.md." }
}
Push-Location $projectRoot
try {
    & ./gradlew.bat :app:assembleRelease '-PisolatedTests=false' '-PmobileArmOnly=true' --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android release build failed.' }
    $output = Join-Path $projectRoot '.local/distribution'
    New-Item -ItemType Directory -Force $output | Out-Null
    $apk = Join-Path $output 'Lifehub-Plus-1.1.2.apk'
    Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $apk
    (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() + '  Lifehub-Plus-1.1.2.apk' | Set-Content -Encoding ascii (Join-Path $output 'SHA256SUMS.txt')
    Write-Host "Signed APK: $apk"
} finally { Pop-Location }
