$ErrorActionPreference = "Stop"

# Check for JDK 8
$javaFound = $false
try {
    $javaVersion = & java -version 2>&1 | Out-String
    if ($javaVersion -match '1\.8\.' -or $javaVersion -match '"1\.8') {
        $javaFound = $true
    }
} catch {}

if (-not $javaFound) {
    Write-Host "Error: JDK 8 is required but was not found."
    Write-Host "Please install JDK 8 and make sure 'java' is available in your PATH."
    exit 1
}

$linkMindDir = Join-Path $HOME "LinkMind"
$jarName = "LinkMind.jar"
$downloadUrl = "http://localhost:8000/LinkMind.jar"
$jarPath = Join-Path $linkMindDir $jarName

# 1. Ensure LinkMind directory exists
if (-not (Test-Path $linkMindDir)) {
    New-Item -ItemType Directory -Path $linkMindDir | Out-Null
    Write-Host "Created directory: $linkMindDir"
} else {
    Write-Host "Directory already exists: $linkMindDir"
}

# 2-3. Download jar to a temp file with progress, then move to target
$tempFile = Join-Path ([System.IO.Path]::GetTempPath()) "LinkMind_$([System.Guid]::NewGuid().ToString('N')).jar"

try {
    Write-Host "Downloading $downloadUrl ..."

    $webRequest = [System.Net.HttpWebRequest]::Create($downloadUrl)
    $webRequest.Timeout = 30000
    $response = $webRequest.GetResponse()
    $totalBytes = $response.ContentLength
    $responseStream = $response.GetResponseStream()
    $fileStream = [System.IO.File]::Create($tempFile)
    $buffer = New-Object byte[] 65536
    $bytesRead = 0
    $totalRead = 0
    $lastPercent = -1
    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    while (($bytesRead = $responseStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
        $fileStream.Write($buffer, 0, $bytesRead)
        $totalRead += $bytesRead

        if ($totalBytes -gt 0) {
            $percent = [math]::Floor($totalRead * 100 / $totalBytes)
            if ($percent -ne $lastPercent) {
                $lastPercent = $percent
                $elapsedSec = $sw.Elapsed.TotalSeconds
                $speed = if ($elapsedSec -gt 0) { $totalRead / $elapsedSec } else { 0 }
                $speedMB = [math]::Round($speed / 1MB, 2)
                $downloadedMB = [math]::Round($totalRead / 1MB, 2)
                $totalMB = [math]::Round($totalBytes / 1MB, 2)
                Write-Host -NoNewline ("`r  Progress: $percent%  ($downloadedMB / $totalMB MB)  Speed: $speedMB MB/s   ")
            }
        } else {
            $downloadedMB = [math]::Round($totalRead / 1MB, 2)
            Write-Host -NoNewline ("`r  Downloaded: $downloadedMB MB   ")
        }
    }

    Write-Host ""
    $fileStream.Close()
    $responseStream.Close()
    $response.Close()

    Copy-Item -Path $tempFile -Destination $jarPath -Force
    Write-Host "Download complete: $jarPath"
} catch {
    Write-Host "Error: Failed to download $downloadUrl"
    Write-Host $_.Exception.Message
    exit 1
} finally {
    if (Test-Path $tempFile) {
        Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
    }
}

# 4-5. Ask user questions and run InstallerUtil
function Read-YesNo($prompt) {
    $answer = Read-Host "$prompt (yes/no) [no]"
    $answer = $answer.Trim().ToLower()
    return ($answer -eq "yes" -or $answer -eq "y")
}

$exportToOpenClaw = Read-YesNo "Would you like to inject LinkMind into OpenClaw?"
$importFromOpenClaw = Read-YesNo "Would you like to import OpenClaw configurations into LinkMind?"

$exportVal = if ($exportToOpenClaw) { "true" } else { "false" }
$importVal = if ($importFromOpenClaw) { "true" } else { "false" }

Write-Host "Running installer..."
java -cp $jarPath ai.starter.InstallerUtil "--export-to-openclaw=$exportVal" "--import-from-openclaw=$importVal"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Installer exited with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

# 6. Success message
Write-Host ""
Write-Host "LinkMind installed successfully!"
Write-Host ""

# 7. Optionally start LinkMind
$startNow = Read-YesNo "Would you like to start LinkMind now?"
if ($startNow) {
    Set-Location $linkMindDir
    java -jar $jarName
} else {
    Write-Host "You can start LinkMind later by running:"
    Write-Host "  cd $linkMindDir && java -jar $jarName"
}
