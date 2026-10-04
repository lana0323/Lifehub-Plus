param(
    [Parameter(Mandatory=$true)][string]$ModelPath,
    [string]$Python = 'python',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/mobile-v4-regression-reproduction'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $root
try {
    $protocol = Get-Content -Raw -LiteralPath docs/evaluation-mobile-v4-regression/protocol.json | ConvertFrom-Json
    & $Python "$PSScriptRoot/verify_sources.py"
    if ($LASTEXITCODE -ne 0) { throw 'Frozen source verification failed' }
    & "$PSScriptRoot/run.ps1" -ModelPath $ModelPath -Python $Python -JavaHome $JavaHome -OutputDirectory $OutputDirectory -Cases $protocol.dataset -RecordNativeCrashes
    if ($LASTEXITCODE -ne 0) { throw 'Regression inference failed' }
    & $Python "$PSScriptRoot/score_v4_regression.py" --validated (Join-Path $OutputDirectory 'validated.json') --output (Join-Path $OutputDirectory 'regression/results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Regression scoring failed' }
} finally { Pop-Location }
