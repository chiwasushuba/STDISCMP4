$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & java scripts/GenerateKeys.java
    if ($LASTEXITCODE -ne 0) { throw 'Credential setup failed.' }
} finally { Pop-Location }
