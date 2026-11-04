# Compile and run the Library Management System.
#   powershell -ExecutionPolicy Bypass -File run.ps1          (build + start)
#   powershell -ExecutionPolicy Bypass -File run.ps1 -Check   (build + run the no-database self-check)
param([switch]$Check)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

# settings: .env (KEY=VALUE lines) fills in anything not already set in the environment
if (Test-Path .env) {
  Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Z_]+)\s*=\s*(.*?)\s*$' -and -not [Environment]::GetEnvironmentVariable($Matches[1])) {
      [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2])
    }
  }
}

# find a JDK (javac): JAVA_HOME, PATH, then the portable JDK 17 left in %TEMP% by earlier projects
$javac = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { $javac = "$env:JAVA_HOME\bin\javac.exe" }
if (-not $javac) { $c = Get-Command javac -ErrorAction SilentlyContinue; if ($c) { $javac = $c.Source } }
if (-not $javac) { $p = Get-ChildItem "$env:TEMP\jdk17-portable" -Recurse -Filter javac.exe -ErrorAction SilentlyContinue | Select-Object -First 1; if ($p) { $javac = $p.FullName } }
if (-not $javac) { throw 'No JDK found. Install a JDK (8+) or set JAVA_HOME.' }
$java = Join-Path (Split-Path $javac) 'java.exe'

$jar = Get-ChildItem lib -Filter 'mysql-connector-j-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jar) { throw 'Put mysql-connector-j-*.jar in the lib folder (see README).' }

New-Item -ItemType Directory -Force out | Out-Null
$ver = & $javac -version 2>&1 | Out-String
$rel = if ($ver -match 'javac (1\.8|8)\b') { @() } else { @('--release', '8') }   # classes also run on a plain Java 8 JRE
& $javac -encoding UTF-8 @rel -d out (Get-ChildItem src -Recurse -Filter *.java).FullName
if ($LASTEXITCODE -ne 0) { throw 'Compile failed.' }

$cp = "out;$($jar.FullName)"
if ($Check) { & $java -cp $cp lms.SelfCheck } else { & $java -cp $cp lms.Main }
