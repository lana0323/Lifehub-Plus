param(
    [Parameter(Mandatory=$true)][string]$ModelPath,
    [string]$Python = 'python',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/mobile-regression-reproduction'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $root
try {
    $protocol = Get-Content -Raw -LiteralPath docs/evaluation-mobile-regression/protocol.json | ConvertFrom-Json
    foreach ($source in $protocol.sourceSha256.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath $source.Name -Algorithm SHA256).Hash.ToLowerInvariant() -ne $source.Value) { throw "Frozen source changed: $($source.Name)" }
    }
    if ((Get-FileHash -LiteralPath $protocol.dataset -Algorithm SHA256).Hash.ToLowerInvariant() -ne $protocol.datasetSha256) { throw 'Frozen dataset changed' }
    & "$PSScriptRoot/run.ps1" -ModelPath $ModelPath -Python $Python -JavaHome $JavaHome -OutputDirectory $OutputDirectory -Cases $protocol.dataset -RecordNativeCrashes
    if ($LASTEXITCODE -ne 0) { throw 'Regression inference failed' }
    & $Python "$PSScriptRoot/score_regression.py" --validated (Join-Path $OutputDirectory 'validated.json') --output (Join-Path $OutputDirectory 'regression/results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Regression scoring failed' }
} finally { Pop-Location }
