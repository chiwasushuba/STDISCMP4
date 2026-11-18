$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDirectory = Join-Path $projectRoot 'deliverables'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$archivePath = Join-Path $outputDirectory ('northstar-source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
$sourceRoots = @('.mvn', 'contracts', 'service-common', 'identity-service', 'course-service', 'grade-service', 'api-orchestrator', 'web-app', 'deployment', 'scripts', 'tests', 'docs')
$rootFiles = @('.gitignore', '.dockerignore', 'pom.xml', 'mvnw', 'mvnw.cmd', 'Dockerfile', 'compose.yaml', 'requirements.txt', 'README.md', 'package.json', 'package-lock.json', 'playwright.config.cjs')
$files = @()
foreach ($directory in $sourceRoots) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -File -Recurse | Where-Object { $_.FullName -notmatch '[\\/]target[\\/]' }
}
foreach ($name in $rootFiles) { $files += Get-Item -LiteralPath (Join-Path $projectRoot $name) }
$archive = [System.IO.Compression.ZipFile]::Open($archivePath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Output "Created source archive: $archivePath"
