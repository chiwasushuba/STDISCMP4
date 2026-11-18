$ErrorActionPreference = 'Stop'
$pidFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/local-processes.json'
if (Test-Path -LiteralPath $pidFile) {
    $records = Get-Content -LiteralPath $pidFile | ConvertFrom-Json
    foreach ($record in $records) {
        $process = Get-Process -Id $record.Pid -ErrorAction SilentlyContinue
        if ($process) {
            $recordedStart = [DateTimeOffset]::Parse($record.Started, [Globalization.CultureInfo]::InvariantCulture)
            $difference = [Math]::Abs(($process.StartTime.ToUniversalTime() - $recordedStart.UtcDateTime).TotalSeconds)
            if ($process.ProcessName -ne 'java' -or $difference -gt 2) {
                throw "PID $($record.Pid) no longer matches the recorded Java process. It was left untouched."
            }
            Stop-Process -Id $record.Pid
            $process.WaitForExit(15000) | Out-Null
        }
    }
    Remove-Item -LiteralPath $pidFile
    Write-Host 'Stopped the local Java nodes. Database containers and volumes remain available.'
}
