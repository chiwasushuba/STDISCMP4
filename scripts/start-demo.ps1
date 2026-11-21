$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Install and open Docker Desktop before starting the demo.'
    }
    & docker info --format '{{.ServerVersion}}' | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Start Docker Desktop and wait for its engine to finish starting, then retry.'
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Install JDK 21 or newer, then open a new PowerShell window and retry.'
    }

    & "$PSScriptRoot/setup.ps1"
    & docker compose up --build --wait --wait-timeout 180
    if ($LASTEXITCODE -ne 0) {
        throw 'AnimoRoll did not finish starting. Run docker compose ps -a and docker compose logs --tail 80 to see the cause.'
    }

    $webAddress = & docker compose port web-app 8080
    if ($LASTEXITCODE -ne 0) { throw 'The web port could not be found. Run docker compose ps -a.' }
    Write-Host "AnimoRoll is ready at http://$webAddress"
    Write-Host 'Sign in as student@animoroll.edu or faculty@animoroll.edu using DEMO_PASSWORD from .env.'
} finally {
    Pop-Location
}
