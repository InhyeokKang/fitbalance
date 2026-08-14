# Android 빌드 툴체인 설치 스크립트 (한 번만 실행)
#
# 이 PC에는 JDK 8만 있어 Android Gradle Plugin이 동작하지 않는다.
# 이 스크립트는 JDK 17과 Android SDK(명령줄 도구)를 C:\dev 아래에 설치하고
# local.properties에 sdk.dir를 기록한다. 기존 JDK 8은 건드리지 않는다.
#
# 실행:  powershell -ExecutionPolicy Bypass -File tools\setup_android_sdk.ps1
#
# 이미 Android Studio가 설치돼 있다면 이 스크립트 대신 Android Studio를 쓰는 편이 낫다.
# (Studio가 JDK와 SDK, 에뮬레이터를 함께 설치해 준다.)

$ErrorActionPreference = "Stop"

$Root      = "C:\dev"
$JdkDir    = "$Root\jdk-17"
$SdkDir    = "$Root\android-sdk"
$ProjectDir = Split-Path -Parent $PSScriptRoot

# 다운로드 용량 합계 약 350MB (JDK 약 190MB + 명령줄 도구 약 130MB),
# 이후 SDK 패키지 설치로 약 700MB가 추가로 내려온다.
$JdkUrl  = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
$CmdUrl  = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

New-Item -ItemType Directory -Force $Root | Out-Null
$tmp = Join-Path $env:TEMP "fitbalance-setup"
New-Item -ItemType Directory -Force $tmp | Out-Null

# 진행률 표시줄을 끄면 Windows PowerShell에서 내려받기가 훨씬 빨라진다.
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

<#
.SYNOPSIS
파일을 내려받고 크기와 압축 무결성을 검증한다. 실패하면 다시 시도한다.

큰 파일은 중간에 끊겨도 오류 없이 저장되는 일이 있어(잘린 zip),
받은 크기를 Content-Length와 대조하고 zip을 열어 확인한 뒤에만 통과시킨다.
#>
function Get-VerifiedZip {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$OutFile,
        [int]$Retries = 3
    )

    Add-Type -AssemblyName System.Net.Http | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        $client = $null
        $stream = $null
        try {
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue

            $client = New-Object System.Net.Http.HttpClient
            $client.Timeout = [TimeSpan]::FromMinutes(30)

            $resp = $client.GetAsync(
                $Url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
            ).GetAwaiter().GetResult()
            $resp.EnsureSuccessStatusCode() | Out-Null

            $expected = $resp.Content.Headers.ContentLength
            $stream = [System.IO.File]::Create($OutFile)
            $resp.Content.CopyToAsync($stream).GetAwaiter().GetResult()
            $stream.Close(); $stream = $null

            $actual = (Get-Item $OutFile).Length
            if ($expected -and $actual -ne $expected) {
                throw "받은 크기가 다릅니다 ($actual / $expected 바이트)"
            }

            # 잘린 zip은 여기서 걸러진다.
            $zip = [System.IO.Compression.ZipFile]::OpenRead($OutFile)
            $zip.Dispose()

            Write-Host ("      완료: {0:N1} MB" -f ($actual / 1MB))
            return
        }
        catch {
            Write-Host "      실패 ($attempt/$Retries): $($_.Exception.Message)"
            if ($attempt -eq $Retries) {
                throw "내려받기를 $Retries 번 시도했으나 실패했습니다: $Url"
            }
            Start-Sleep -Seconds 5
        }
        finally {
            if ($stream) { $stream.Dispose() }
            if ($client) { $client.Dispose() }
        }
    }
}

# 1) JDK 17 --------------------------------------------------------------
if (Test-Path "$JdkDir\bin\java.exe") {
    Write-Host "[1/4] JDK 17 이미 설치됨: $JdkDir"
} else {
    Write-Host "[1/4] JDK 17 내려받는 중 (약 190MB)..."
    $jdkZip = "$tmp\jdk17.zip"
    Get-VerifiedZip -Url $JdkUrl -OutFile $jdkZip
    Write-Host "      압축 푸는 중..."
    $extract = "$tmp\jdk-extract"
    Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
    Expand-Archive -Path $jdkZip -DestinationPath $extract -Force
    $inner = Get-ChildItem $extract -Directory | Select-Object -First 1
    Remove-Item -Recurse -Force $JdkDir -ErrorAction SilentlyContinue
    Move-Item $inner.FullName $JdkDir
    Write-Host "      설치 완료: $JdkDir"
}

$env:JAVA_HOME = $JdkDir
$env:PATH = "$JdkDir\bin;$env:PATH"

# 2) Android 명령줄 도구 --------------------------------------------------
$SdkManager = "$SdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $SdkManager) {
    Write-Host "[2/4] Android 명령줄 도구 이미 설치됨"
} else {
    Write-Host "[2/4] Android 명령줄 도구 내려받는 중 (약 130MB)..."
    $cmdZip = "$tmp\cmdline-tools.zip"
    Get-VerifiedZip -Url $CmdUrl -OutFile $cmdZip
    $extract = "$tmp\cmdline-extract"
    Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
    Expand-Archive -Path $cmdZip -DestinationPath $extract -Force
    New-Item -ItemType Directory -Force "$SdkDir\cmdline-tools" | Out-Null
    Remove-Item -Recurse -Force "$SdkDir\cmdline-tools\latest" -ErrorAction SilentlyContinue
    Move-Item "$extract\cmdline-tools" "$SdkDir\cmdline-tools\latest"
    Write-Host "      설치 완료: $SdkDir"
}

# 3) SDK 패키지 (라이선스 자동 동의) --------------------------------------
Write-Host "[3/4] SDK 패키지 설치 중 (약 700MB, 몇 분 걸립니다)..."
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir

$yes = @()
1..30 | ForEach-Object { $yes += "y" }
$yes | & $SdkManager --sdk_root="$SdkDir" --licenses | Out-Null
& $SdkManager --sdk_root="$SdkDir" "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 4) local.properties 기록 -----------------------------------------------
Write-Host "[4/4] local.properties 기록 중..."
$sdkEscaped = $SdkDir -replace '\\', '\\'
$lines = @(
    "# 이 파일은 커밋하지 않는다 (.gitignore에 포함됨)",
    "sdk.dir=$sdkEscaped",
    "",
    "# 서버 주소. 에뮬레이터에서 호스트 PC의 localhost는 10.0.2.2 이다.",
    "BASE_URL_DEBUG=http://10.0.2.2:8000/"
)
Set-Content -Path "$ProjectDir\local.properties" -Value $lines -Encoding UTF8

Write-Host ""
Write-Host "설치 완료. 이제 아래 순서로 빌드하세요:" -ForegroundColor Green
Write-Host ""
Write-Host "  `$env:JAVA_HOME = `"$JdkDir`""
Write-Host "  cd $ProjectDir"
Write-Host "  .\gradlew.bat assembleDebug"
Write-Host ""
Write-Host "APK 위치: app\build\outputs\apk\debug\app-debug.apk"
