$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDirectory = Join-Path $projectRoot 'deliverables'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$archivePath = Join-Path $outputDirectory ('animoroll-source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
$sourceRoots = @('.mvn', 'contracts', 'service-common', 'identity-service', 'course-service', 'grade-service', 'api-orchestrator', 'web-app', 'frontend/src', 'deployment', 'scripts', 'tests', 'docs')
$sourceFiles = @('.gitignore', '.dockerignore', 'pom.xml', 'mvnw', 'mvnw.cmd', 'Dockerfile', 'compose.yaml', 'requirements.txt', 'README.md', 'package.json', 'package-lock.json', 'playwright.config.cjs', 'frontend/index.html', 'frontend/package.json', 'frontend/package-lock.json', 'frontend/vite.config.js')
$files = @()
foreach ($directory in $sourceRoots) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -File -Recurse | Where-Object {
        $relative = $_.FullName.Substring($projectRoot.Length + 1)
        $relative -notmatch '(^|[\\/])(target|node_modules|dist|secrets)[\\/]' -and
        $_.Name -notmatch '^(\.env($|\.)|\.npmrc$)'
    }
}
foreach ($name in $sourceFiles) { $files += Get-Item -LiteralPath (Join-Path $projectRoot $name) }
$archive = [System.IO.Compression.ZipFile]::Open($archivePath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $relative) | Out-Null
    }
} finally { $archive.Dispose() }
Write-Output "Created source archive: $archivePath"
