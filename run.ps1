# CampusDesk Local Run Script (PowerShell)
$ErrorActionPreference = "Stop"

Write-Host "Compiling CampusDesk Java files..." -ForegroundColor Cyan
$sources = Get-ChildItem -Path . -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out $sources

$port = if ($args[0]) { $args[0] } elseif ($env:PORT) { $env:PORT } else { "8080" }

Write-Host "Starting CampusDesk on http://localhost:$port..." -ForegroundColor Green
Write-Host "Default login: admin / admin123" -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop the server." -ForegroundColor Gray

java -cp out college.registration.Main $port
