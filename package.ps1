# Packages Yumoo Minecraft Launcher into dist/YML (jar + JavaFX + launcher script).
param([switch]$SkipBuild, [string]$FeedUrl = "")
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $root 'dist\YML'
$build = Join-Path $root 'build'

if (-not $SkipBuild) {
    & (Join-Path $root 'build.ps1') -Jar
    if ($LASTEXITCODE -ne 0) { throw "build failed" }
}

$jar = Join-Path $build 'yml-launcher.jar'
if (-not (Test-Path $jar)) { throw "missing $jar" }

if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'lib\javafx') | Out-Null
Copy-Item $jar (Join-Path $dist 'yml-launcher.jar') -Force

# Only the platform jars are real; the plain artifacts are 300 byte stubs.
Get-ChildItem (Join-Path $root 'lib\javafx') -Filter '*.jar' |
    Where-Object { $_.Length -gt 1000 } |
    ForEach-Object { Copy-Item $_.FullName (Join-Path $dist 'lib\javafx') -Force }

$bat = @'
@echo off
setlocal
cd /d "%~dp0"
set "JAVA=javaw.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
"%JAVA%" --module-path "%~dp0lib\javafx" --add-modules javafx.controls,javafx.media -cp "%~dp0yml-launcher.jar" dev.yml.Main
if errorlevel 1 (
  echo.
  echo Failed to start Yumoo Minecraft Launcher. Please install JDK 26 or newer and try again.
  pause
)
endlocal
'@
$batPath = Join-Path $dist 'YML.bat'
if ($FeedUrl) { $bat = $bat.Replace('dev.yml.Main', ('-Dyml.feed=' + $FeedUrl + ' dev.yml.Main')) }
[System.IO.File]::WriteAllText($batPath, $bat, (New-Object System.Text.UTF8Encoding($false)))

$adminBat = @'
@echo off
setlocal
cd /d "%~dp0"
set "JAVA=javaw.exe"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
"%JAVA%" --module-path "%~dp0lib\javafx" --add-modules javafx.controls,javafx.media -cp "%~dp0yml-launcher.jar" dev.yml.Main --admin
endlocal
'@
[System.IO.File]::WriteAllText((Join-Path $dist 'YML-Admin.bat'), $adminBat,
    (New-Object System.Text.UTF8Encoding($false)))

Copy-Item (Join-Path $root 'README.md') (Join-Path $dist 'README.md') -ErrorAction SilentlyContinue
Copy-Item (Join-Path $root 'LICENSE') (Join-Path $dist 'LICENSE') -ErrorAction SilentlyContinue

# Publish SHA-256 checksums for manual verification (no code signing).
$sums = @()
foreach ($name in @('yml-launcher.jar', 'YML.bat', 'YML-Admin.bat', 'README.md', 'LICENSE')) {
    $file = Join-Path $dist $name
    if (Test-Path $file) {
        $hash = (Get-FileHash -Algorithm SHA256 -Path $file).Hash.ToLowerInvariant()
        $sums += "$hash  $name"
    }
}
$sums | Set-Content -Path (Join-Path $dist 'SHA256SUMS.txt') -Encoding Ascii

Write-Host "[package] $dist" -ForegroundColor Green
Get-ChildItem $dist | Select-Object Name, Length | Format-Table -AutoSize | Out-String | Write-Host
