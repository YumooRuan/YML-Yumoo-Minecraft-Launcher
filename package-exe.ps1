param(
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-26" }),
    [string]$Version = "1.0.0",
    [switch]$NoAdmin
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jlink = Join-Path $Jdk "bin\jlink.exe"
$jpackage = Join-Path $Jdk "bin\jpackage.exe"
if (-not (Test-Path $jlink)) { throw "jlink not found: $jlink" }
if (-not (Test-Path $jpackage)) { throw "jpackage not found: $jpackage" }

Write-Host "[exe] building launcher jar"
& (Join-Path $root "package.ps1")

$runtime = Join-Path $root "build\exe-runtime"
$modulePath = "$(Join-Path $root 'lib\javafx');$(Join-Path $Jdk 'jmods')"
Write-Host "[exe] jlink runtime"
Remove-Item $runtime -Recurse -Force -ErrorAction SilentlyContinue
& $jlink --module-path $modulePath `
    --add-modules javafx.controls,javafx.media,java.management,jdk.crypto.ec,java.naming,java.logging,jdk.unsupported,java.net.http `
    --output $runtime --strip-debug --no-header-files --no-man-pages
if (-not (Test-Path (Join-Path $runtime "bin\java.exe"))) { throw "jlink failed" }

$input = Join-Path $root "build\exe-input"
Remove-Item $input -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $input | Out-Null
Copy-Item (Join-Path $root "dist\YML\yml-launcher.jar") (Join-Path $input "yml-launcher.jar")

$out = Join-Path $root "dist\YML-Exe"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "[exe] jpackage app-image"
$jpackageArgs = @(
    "--type", "app-image", "--name", "YML", "--dest", $out, "--input", $input
)
if (-not $NoAdmin) {
    $adminProps = Join-Path $root "build\admin-launcher.properties"
    "arguments=--admin" | Set-Content -Path $adminProps -Encoding ASCII
    $jpackageArgs += @("--add-launcher", "YML-Admin=$adminProps")
}
$jpackageArgs += @(
    "--main-jar", "yml-launcher.jar", "--main-class", "dev.yml.Main",
    "--runtime-image", $runtime,
    "--java-options", "--add-modules=javafx.controls,javafx.media",
    "--icon", (Join-Path $root "assets\yml.ico"),
    "--app-version", $Version, "--vendor", "Yumoo",
    "--description", "Yumoo Minecraft Launcher"
)
& $jpackage @jpackageArgs

$readme = Join-Path $out "README.txt"
@"
Yumoo Minecraft Launcher (YML) - self-contained build

Double-click: YML\YML.exe
No Java installation required; the runtime and JavaFX modules are bundled.

Data directory: %APPDATA%\YML
"@ | Set-Content -Path $readme -Encoding UTF8

Write-Host "[exe] done -> $out\YML\YML.exe"
Get-ChildItem (Join-Path $out "YML") | Select-Object Name, Length | Format-Table -AutoSize
