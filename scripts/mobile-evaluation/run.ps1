param(
    [Parameter(Mandatory=$true)][string]$ModelPath,
    [string]$Python = 'python',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/mobile-reproduction'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$previousPythonPath = $env:PYTHONPATH
$previousTzPath = $env:PYTHONTZPATH
Push-Location $root
try {
    if (-not $JavaHome) { throw 'Set JAVA_HOME to JDK 21.' }
    if ((Get-FileHash -LiteralPath $ModelPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9') { throw 'Use the pinned model from docs/ANDROID_INSTALL.md.' }
    $model = (Resolve-Path -LiteralPath $ModelPath).Path
    $output = [IO.Path]::GetFullPath((Join-Path $root $OutputDirectory))
    New-Item -ItemType Directory -Force $output | Out-Null
    $pythonLibraries = Join-Path $output 'python'
    & $Python -m pip install --disable-pip-version-check --no-deps --target $pythonLibraries tzdata==2026.2
    if ($LASTEXITCODE -ne 0) { throw 'Pinned timezone data installation failed' }
    $env:PYTHONPATH = $pythonLibraries
    $env:PYTHONTZPATH = Join-Path $pythonLibraries 'tzdata/zoneinfo'
    $libs = Join-Path $output 'libs'
    New-Item -ItemType Directory -Force $libs | Out-Null
    $jars = @(
        @('https://dl.google.com/dl/android/maven2', 'com/google/ai/edge/litertlm/litertlm-jvm/0.10.2/litertlm-jvm-0.10.2.jar'),
        @('https://repo.maven.apache.org/maven2', 'com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'),
        @('https://repo.maven.apache.org/maven2', 'org/jetbrains/kotlin/kotlin-stdlib/2.2.21/kotlin-stdlib-2.2.21.jar'),
        @('https://repo.maven.apache.org/maven2', 'org/jetbrains/kotlin/kotlin-reflect/2.2.21/kotlin-reflect-2.2.21.jar'),
        @('https://repo.maven.apache.org/maven2', 'org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.9.0/kotlinx-coroutines-core-jvm-1.9.0.jar'),
        @('https://repo.maven.apache.org/maven2', 'org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar')
    )
    foreach ($jar in $jars) {
        $target = Join-Path $libs (Split-Path $jar[1] -Leaf)
        if (-not (Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri ($jar[0]+'/'+$jar[1]) -OutFile $target }
    }
    & (Join-Path $JavaHome 'bin/javac.exe') -cp "$libs/*" -d $output scripts/mobile-evaluation/NativeRunner.java
    if ($LASTEXITCODE -ne 0) { throw 'Runner compilation failed' }
    $prepared = Join-Path $output 'prepared.json'
    $raw = Join-Path $output 'raw.json'
    if (Test-Path $raw) { throw 'Choose a fresh OutputDirectory to keep independent runs separate.' }
    & $Python scripts/mobile-evaluation/prepare_and_validate.py prepare backend/evaluation/holdout_v2.json $prepared
    if ($LASTEXITCODE -ne 0) { throw 'Preparation failed' }
    & (Join-Path $JavaHome 'bin/java.exe') -cp "$output;$libs/*" NativeRunner $model $prepared $raw $output
    if ($LASTEXITCODE -ne 0) { throw 'Native inference failed; captured rows remain available.' }
    $validated = Join-Path $output 'validated.json'
    & $Python scripts/mobile-evaluation/prepare_and_validate.py validate backend/evaluation/holdout_v2.json $validated --raw $raw
    if ($LASTEXITCODE -ne 0) { throw 'Validation failed' }
    & $Python scripts/summarize-mobile-evaluation.py --cases backend/evaluation/holdout_v2.json --results $validated --output (Join-Path $output 'results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Scoring failed' }
} finally {
    $env:PYTHONPATH = $previousPythonPath
    $env:PYTHONTZPATH = $previousTzPath
    Pop-Location
}
