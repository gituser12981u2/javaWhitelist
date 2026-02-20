$ErrorActionPreference = "Stop"

$Repo  = "gituser12981u2/javaWhitelist"
$Asset = "javaWhitelist.jar"

$Base    = Join-Path $env:LOCALAPPDATA "javaWhitelist"
$JarPath = Join-Path $Base $Asset
$CmdPath = Join-Path $Base "javaWhitelist.cmd"

New-Item -ItemType Directory -Force -Path $Base | Out-Null

$Url = "https://github.com/$Repo/releases/latest/download/$Asset"

Write-Host "Downloading $Asset from $Url..."
Invoke-WebRequest -Uri $Url -OutFile $JarPath

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
