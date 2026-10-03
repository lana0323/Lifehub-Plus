param(
    [Parameter(Mandatory=$true)][string]$ModelPath,
    [string]$Protocol = 'docs/evaluation-mobile-holdout-v4/protocol.json',
    [string]$Python = 'python',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/mobile-holdout-v4-reproduction'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $root
try {
    $frozen = Get-Content -Raw -LiteralPath $Protocol | ConvertFrom-Json
    foreach ($source in $frozen.sourceSha256.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath $source.Name -Algorithm SHA256).Hash.ToLowerInvariant() -ne $source.Value) { throw "Frozen source changed: $($source.Name)" }
    }
    if ((Get-FileHash -LiteralPath $frozen.dataset -Algorithm SHA256).Hash.ToLowerInvariant() -ne $frozen.datasetSha256) { throw 'Frozen dataset changed' }
    & "$PSScriptRoot/run.ps1" -ModelPath $ModelPath -Python $Python -JavaHome $JavaHome -OutputDirectory $OutputDirectory -Cases $frozen.dataset -RecordNativeCrashes
    if ($LASTEXITCODE -ne 0) { throw 'Frozen inference failed' }
    & $Python "$PSScriptRoot/score_holdout.py" --validated (Join-Path $OutputDirectory 'validated.json') --protocol $Protocol --output (Join-Path $OutputDirectory 'scored/results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Frozen scoring failed' }
} finally { Pop-Location }
