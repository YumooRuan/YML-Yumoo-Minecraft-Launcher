param(
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "C:\Program Files\Java\jdk-26" }),
    [string]$Version = "1.0.0",
    [switch]$NoAdmin
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { throw "csc.exe not found" }

Write-Host "[single] building app image"
& (Join-Path $root "package-exe.ps1") -Jdk $Jdk -Version $Version -NoAdmin:$NoAdmin

$appDir = Join-Path $root "dist\YML-Exe\YML"
$zip = Join-Path $root "build\yml-app.zip"
Remove-Item $zip -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $appDir "*") -DestinationPath $zip -CompressionLevel Optimal -Force

$bootstrap = Join-Path $root "tools\Bootstrap.cs"
$bootstrapText = [System.IO.File]::ReadAllText($bootstrap)
$bootstrapText = [regex]::Replace($bootstrapText, 'private const string AppVersion = "[^"]*";', 'private const string AppVersion = "' + $Version + '";')
[System.IO.File]::WriteAllText($bootstrap, $bootstrapText, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "[single] compiling bootstrap exes"
$refs = @(
    "/r:System.IO.Compression.dll",
    "/r:System.IO.Compression.FileSystem.dll",
    "/r:System.Windows.Forms.dll",
    "/r:System.Drawing.dll"
)
& $csc /nologo /target:winexe /optimize+ "/win32icon:$root\assets\yml.ico" "/out:$root\build\YML-single.exe" @refs `
    "/resource:$zip,app.zip" (Join-Path $root "tools\Bootstrap.cs")
if (-not $NoAdmin) {
    & $csc /nologo /target:winexe /optimize+ /win32icon:"$root\assets\yml.ico" /define:ADMIN "/out:$root\build\YML-Admin-single.exe" @refs `
        "/resource:$zip,app.zip" (Join-Path $root "tools\Bootstrap.cs")
}

$out = Join-Path $root "dist\YML-Single"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $out | Out-Null
Copy-Item (Join-Path $root "build\YML-single.exe") (Join-Path $out "YML.exe")
if (-not $NoAdmin) {
    Copy-Item (Join-Path $root "build\YML-Admin-single.exe") (Join-Path $out "YML-Admin.exe")
}
Copy-Item (Join-Path $root "README.md") (Join-Path $out "README.md")
Copy-Item (Join-Path $root "LICENSE") (Join-Path $out "LICENSE")

$sums = @()
foreach ($name in @("YML.exe", "YML-Admin.exe", "README.md", "LICENSE")) {
    $file = Join-Path $out $name
    if (Test-Path $file) {
        $hash = (Get-FileHash -Algorithm SHA256 -Path $file).Hash.ToLowerInvariant()
        $sums += "$hash  $name"
    }
}
$sums | Set-Content -Path (Join-Path $out "SHA256SUMS.txt") -Encoding Ascii

Write-Host "[single] done -> $out"
Get-ChildItem $out | Select-Object Name, Length | Format-Table -AutoSize
Get-Content (Join-Path $out "SHA256SUMS.txt")
