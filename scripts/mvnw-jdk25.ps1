[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)

$ErrorActionPreference = 'Stop'
$jdkHome = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot'
$javaExecutable = Join-Path $jdkHome 'bin\java.exe'
$mavenWrapper = Join-Path $PSScriptRoot '..\mvnw.cmd'

if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) {
    throw "JDK 25 was not found at $jdkHome"
}

$env:JAVA_HOME = $jdkHome
$env:Path = "$(Join-Path $jdkHome 'bin');$env:Path"

$ErrorActionPreference = 'Continue'
$javaVersionLines = @(& $javaExecutable -version 2>&1)
$javaExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
$javaVersion = $javaVersionLines -join [Environment]::NewLine
if ($javaExitCode -ne 0 -or
        $javaVersion -notmatch '(?m)^(?:openjdk|java) version "25(?:\.|\")') {
    throw "Java 25 is required. Detected:$([Environment]::NewLine)$javaVersion"
}

Write-Host "Using $($javaVersionLines[0])"

if (-not $MavenArguments -or $MavenArguments.Count -eq 0) {
    $MavenArguments = @('verify')
}

$ErrorActionPreference = 'Continue'
& $mavenWrapper @MavenArguments
$mavenExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($mavenExitCode -ne 0) {
    throw "Maven failed with exit code $mavenExitCode"
}
