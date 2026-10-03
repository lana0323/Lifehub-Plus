param(
    [Parameter(Mandatory=$true)][string]$ModelPath,
    [string]$Python = 'python',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory = '.local/mobile-holdout-reproduction'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Push-Location $root
try {
    $protocol = Get-Content -LiteralPath 'docs/evaluation-mobile-holdout/protocol.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($entry in $protocol.sourceSha256.PSObject.Properties) {
        if ((Get-FileHash -LiteralPath $entry.Name -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.Value) {
            throw "Frozen source changed: $($entry.Name). Use the evaluated snapshot for reproduction."
        }
    }
    if ((Get-FileHash -LiteralPath $protocol.dataset -Algorithm SHA256).Hash.ToLowerInvariant() -ne $protocol.datasetSha256) {
        throw 'Frozen dataset changed.'
    }
    & "$PSScriptRoot/run.ps1" -ModelPath $ModelPath -Python $Python -JavaHome $JavaHome -OutputDirectory $OutputDirectory -Cases $protocol.dataset -RecordNativeCrashes
    if ($LASTEXITCODE -ne 0) { throw 'Native run failed; keep captured outputs.' }
    & $Python "$PSScriptRoot/score_holdout.py" --validated (Join-Path $OutputDirectory 'validated.json') --output (Join-Path $OutputDirectory 'first-pass-results.json')
    if ($LASTEXITCODE -ne 0) { throw 'Frozen scoring failed.' }
} finally { Pop-Location }
