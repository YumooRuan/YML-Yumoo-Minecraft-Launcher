# Runs Yumoo Minecraft Launcher from the compiled classes directory (dev loop).
param(
    [string[]]$AppArgs = @(),
    [switch]$Console
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$classes = Join-Path $root 'build\classes'
$javafx = Join-Path $root 'lib\javafx'

if (-not (Test-Path $classes)) { throw "Not built yet. Run build.ps1 first." }

$java = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
}
if (-not $java) { throw "No java found." }

$javaw = Join-Path (Split-Path $java) 'javaw.exe'
$exe = if (-not $Console -and (Test-Path $javaw)) { $javaw } else { $java }

$fxJars = (Get-ChildItem -Path $javafx -Filter '*.jar' | Where-Object { $_.Length -gt 1000 } | ForEach-Object { $_.FullName }) -join ';'
$appArgs = @(
    '--module-path', $fxJars,
    '--add-modules', 'javafx.controls,javafx.graphics,javafx.base,javafx.media',
    '-Dfile.encoding=UTF-8',
    '-Dstdout.encoding=UTF-8',
    '-Dstderr.encoding=UTF-8',
    '-cp', $classes,
    'dev.yml.Main'
) + $AppArgs

if ($Console) {
    & $exe @appArgs
} else {
    Start-Process -FilePath $exe -ArgumentList $appArgs -WorkingDirectory $root | Out-Null
}
