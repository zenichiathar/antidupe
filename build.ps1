$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$tools = Join-Path $env:TEMP "opencode\maven"
$mvnHome = Join-Path $tools "apache-maven-3.9.9"
$mvn = Join-Path $mvnHome "bin\mvn.cmd"

if (-not (Test-Path $mvn)) {
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "maven.zip"
    Write-Host "Downloading Maven 3.9.9..."
    try {
        Invoke-WebRequest -Uri "https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $zip -UseBasicParsing
    } catch {
        Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip" -OutFile $zip -UseBasicParsing
    }
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Write-Host "Maven extracted to $tools"
}

$javaCmd = (Get-Command java -ErrorAction SilentlyContinue).Source
if ($javaCmd -and -not $env:JAVA_HOME) {
    $env:JAVA_HOME = Split-Path (Split-Path $javaCmd -Parent) -Parent
}

& $mvn -f (Join-Path $root "pom.xml") clean package "-DskipTests"

$jar = Get-ChildItem (Join-Path $root "target") -Filter "AntiDupe-*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($jar) {
    Copy-Item $jar.FullName (Join-Path $root "AntiDupe.jar") -Force
    Write-Host "Built: $($jar.FullName) -> AntiDupe.jar"
} else {
    Write-Error "Build produced no jar"
}