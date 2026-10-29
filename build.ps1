# Yumoo Minecraft Launcher build script -- compiles sources with javac (no Maven/Gradle needed).
param(
    [switch]$Clean,
    [switch]$Jar
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src\main\java'
$res = Join-Path $root 'src\main\resources'
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$javafx = Join-Path $root 'lib\javafx'

# --- locate JDK -------------------------------------------------------------
$java = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
    if ($javacCmd) { $java = Join-Path (Split-Path $javacCmd.Source) 'java.exe' }
}
if (-not $java -or -not (Test-Path $java)) { throw "No JDK found. Set JAVA_HOME to a JDK 26+ installation." }
$javac = Join-Path (Split-Path $java) 'javac.exe'
if (-not (Test-Path $javac)) { throw "javac.exe not found next to java.exe: $javac" }

if ($Clean -and (Test-Path $build)) { Remove-Item $build -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null

# --- collect sources --------------------------------------------------------
$sources = Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) { throw "No java sources under $src" }
$argfile = Join-Path $build 'sources.txt'
$lines = $sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
[System.IO.File]::WriteAllLines($argfile, $lines, (New-Object System.Text.UTF8Encoding($false)))
try { chcp 65001 | Out-Null } catch { }

# --- compile ----------------------------------------------------------------
$fxJars = (Get-ChildItem -Path $javafx -Filter '*.jar' | Where-Object { $_.Length -gt 1000 } | ForEach-Object { $_.FullName }) -join ';'
Write-Host "[build] javac $($sources.Count) files" -ForegroundColor Cyan
& $javac -encoding UTF-8 -nowarn `
    --module-path $fxJars --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.media `
    -d $classes "@$argfile"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

# --- copy resources ---------------------------------------------------------
if (Test-Path $res) {
    Copy-Item -Path (Join-Path $res '*') -Destination $classes -Recurse -Force
}
Write-Host "[build] ok -> $classes" -ForegroundColor Green

# --- optional fat jar -------------------------------------------------------
if ($Jar) {
    $jarFile = Join-Path $build 'yml-launcher.jar'
    $jarExe = Join-Path (Split-Path $java) 'jar.exe'
    if (-not (Test-Path $jarExe)) {
        # javapath shims only contain java/javac; fall back to a real JDK install.
        foreach ($candidate in (Get-ChildItem 'C:\Program Files\Java' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue |
                                Sort-Object Name -Descending)) {
            $probe = Join-Path $candidate.FullName 'bin\jar.exe'
            if (Test-Path $probe) { $jarExe = $probe; break }
        }
    }
    if (-not (Test-Path $jarExe)) { throw "jar.exe not found; set JAVA_HOME to a JDK" }
    & $jarExe --create --file $jarFile --main-class dev.yml.Main -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "jar failed" }
    Write-Host "[build] jar -> $jarFile" -ForegroundColor Green
}
