param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $pidFile = Join-Path $projectRoot '.tools/local-processes.json'
    if (Test-Path -LiteralPath $pidFile) {
        $existing = Get-Content -LiteralPath $pidFile | ConvertFrom-Json
        if ($existing | Where-Object { Get-Process -Id $_.Pid -ErrorAction SilentlyContinue }) {
            throw 'Local nodes are already running. Stop them with scripts/stop-local.ps1 first.'
        }
    }
    if (-not (Test-Path -LiteralPath '.env')) { throw 'Run scripts/setup.ps1 first.' }
    Get-Content -LiteralPath '.env' | ForEach-Object {
        if ($_ -match '^([A-Z_]+)=(.+)$') { Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2] }
    }
    if (-not $SkipBuild) {
        & ./mvnw.cmd -B -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
    }
    & docker compose up -d --wait identity-db course-db grade-db
    if ($LASTEXITCODE -ne 0) { throw 'Database startup failed.' }
    New-Item -ItemType Directory -Force .tools/logs | Out-Null
    $env:JWT_PUBLIC_KEY = 'file:secrets/public.pem'
    $env:JWT_PRIVATE_KEY = 'file:secrets/private.pem'
    $env:DEMO_ENABLED = 'true'
    $nodes = @(
        @{ Module='identity-service'; Name='identity'; Rpc=9091; Db=5541 },
        @{ Module='course-service'; Name='course'; Rpc=9092; Db=5542 },
        @{ Module='grade-service'; Name='grade'; Rpc=9093; Db=5543 },
        @{ Module='api-orchestrator'; Name='orchestrator'; Rpc=9090; Db=0 },
        @{ Module='web-app'; Name='web'; Rpc=0; Db=0 }
    )
    $processes = @()
    # Oracle's javapath launcher can spawn a second process and exit. Track the real JVM.
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $javaSettings = & java -XshowSettings:properties -version 2>&1
    $ErrorActionPreference = $previousErrorPreference
    $javaHomeLine = $javaSettings | Select-String '^\s*java.home = (.+)$'
    $javaHomePath = $javaHomeLine.Matches[0].Groups[1].Value.Trim()
    $javaExecutable = Join-Path $javaHomePath 'bin/java.exe'
    foreach ($node in $nodes) {
        $env:GRPC_PORT = [string]$node.Rpc
        if ($node.Db -gt 0) {
            $env:DB_URL = "jdbc:postgresql://localhost:$($node.Db)/$($node.Name)"
            $env:DB_USER = $node.Name
            $env:DB_PASSWORD = (Get-Item -Path "Env:$($node.Name.ToUpper())_DB_PASSWORD").Value
        }
        $jar = "$($node.Module)/target/$($node.Module)-1.0.0.jar"
        $process = Start-Process -FilePath $javaExecutable -ArgumentList @('-Xmx256m','-jar',$jar) -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput ".tools/logs/$($node.Name).log" -RedirectStandardError ".tools/logs/$($node.Name).err.log"
        $processes += @{ Name=$node.Name; Pid=$process.Id; Started=$process.StartTime.ToUniversalTime().ToString('o') }
    }
    $processes | ConvertTo-Json | Set-Content -LiteralPath $pidFile
    Write-Host 'Starting Northstar at http://localhost:8080. Logs: .tools/logs/'
} finally { Pop-Location }
