param([string]$Model = 'qwen3:4b')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$portable = Join-Path $projectRoot '.local/ollama/ollama.exe'
$ollamaCommand = Get-Command ollama -ErrorAction SilentlyContinue
$ollamaExe = if (Test-Path $portable) { $portable } elseif ($ollamaCommand) { $ollamaCommand.Source } else { throw 'Install Ollama first: https://ollama.com/download/windows' }
$env:OLLAMA_NO_CLOUD = '1'
$env:OLLAMA_HOST = '127.0.0.1:11434'
$env:OLLAMA_MODELS = Join-Path $projectRoot '.local/models'
$env:OLLAMA_MODEL = $Model
$env:AI_PROVIDER = 'ollama'
$env:ENABLE_PAID_AI = 'false'
$env:HOST = '127.0.0.1'
$env:PORT = '8080'
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.local') | Out-Null
try {
    Invoke-RestMethod 'http://127.0.0.1:11434/api/version' -TimeoutSec 2 | Out-Null
    Write-Host 'Using an existing Ollama process. Ensure it was started with OLLAMA_NO_CLOUD=1.'
} catch {
    Start-Process -FilePath $ollamaExe -ArgumentList 'serve' -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $projectRoot '.local/ollama-out.log') `
        -RedirectStandardError (Join-Path $projectRoot '.local/ollama-error.log') | Out-Null
    Start-Sleep -Seconds 3
}
$tags = Invoke-RestMethod 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5
if ($Model -notin $tags.models.name) {
    throw "Local model $Model is missing. Download it with: & '$ollamaExe' pull '$Model' (set OLLAMA_MODELS to '$env:OLLAMA_MODELS' first)."
}
Write-Host "Starting LifeHub with local model $Model. API fee: zero. Keep this terminal open."
python (Join-Path $projectRoot 'backend/server.py')
