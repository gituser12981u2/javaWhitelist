$ErrorActionPreference = "Stop"

$Repo = "YOUR_USER/javaWhitelist"
$Asset = "javaWhitelist.jar"

$Base = Join-Path $env:LOCALAPPDATA "javaWhitelist"
$JarPath = Join-Path $Base "javaWhitelist.jar"
$CmdPath = Join-Path $Base "javaWhitelist.cmd"

New-Item -ItemType Directory -Force -Path $Base | Out-Null

Write-Host "Downloading latest $Asset from $Repo..."

$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/latest"
$url = ($rel.assets | Where-Object { $_.name -eq $Asset } | Select-Object -First 1).browser_download_url

if (-not $url) {
  throw "Failed to locate release asset $Asset"
}

Invoke-WebRequest -Uri $url -OutFile $JarPath

@"
@echo off
java -jar "%LOCALAPPDATA%\javaWhitelist\javaWhitelist.jar" %*
"@ | Set-Content -Encoding ASCII -Path $CmdPath

# Add to user PATH if not already
$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")
if ($UserPath -notlike "*$Base*") {
  [Environment]::SetEnvironmentVariable("Path", "$UserPath;$Base", "User")
  Write-Host ""
  Write-Host "Added to your User PATH:"
  Write-Host "  $Base"
  Write-Host "Restart your terminal to use: javaWhitelist --help"
} else {
  Write-Host "Already on PATH. You can run: javaWhitelist --help"
}

Write-Host ""
Write-Host "Installed:"
Write-Host "  Jar: $JarPath"
Write-Host "  Cmd: $CmdPath"
