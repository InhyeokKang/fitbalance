# fitbalance 체험판 설치 (Windows)
#
# 안드로이드 폰이 없어도 앱을 볼 수 있게, 에뮬레이터부터 앱까지 한 번에 깐다.
#
#   1) JDK 17        - 안드로이드 SDK 관리 도구가 자바로 돌아간다
#   2) SDK 명령줄 도구 - 에뮬레이터와 adb를 받기 위한 도구
#   3) 에뮬레이터 + 시스템 이미지 (약 1.5GB)
#   4) 가상 기기(AVD) 생성 + 한국어 설정
#   5) 백엔드 서버 실행
#   6) 앱 설치 후 실행
#
# 전부 C:\dev 아래에만 설치한다. 기존 자바나 안드로이드 설정은 건드리지 않는다.
# 이미 있는 단계는 건너뛴다. 다시 실행해도 안전하다.
#
# 내려받는 용량 합계 약 2GB. 처음 한 번만 받는다.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root       = "C:\dev"
$JdkDir     = "$Root\jdk-17"
$SdkDir     = "$Root\android-sdk"
$AvdName    = "fitbalance"
$PackageId  = "com.fitbalance.app"
$Here       = Split-Path -Parent $MyInvocation.MyCommand.Path
$Apk        = Join-Path $Here "fitbalance.apk"
$ServerDir  = Join-Path $Here "server"

# API 30 + google_apis 조합이라야 ARM 변환이 켜진다.
# 카카오 지도 SDK가 ARM 라이브러리만 담고 있어 이 조합이 필요하다.
$SysImage = "system-images;android-30;google_apis;x86_64"

$JdkUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
$CmdUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

function Step($n, $text) { Write-Host "`n[$n] $text" -ForegroundColor Cyan }
function Info($text)     { Write-Host "     $text" -ForegroundColor DarkGray }
function Die($text) {
    Write-Host "`n$text" -ForegroundColor Red
    Write-Host "`n창을 닫으려면 아무 키나 누르세요."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 1
}

<#
.SYNOPSIS
파일을 내려받고 크기와 압축 무결성을 검증한다. 실패하면 다시 시도한다.

큰 파일은 중간에 끊겨도 오류 없이 저장되는 일이 있어(잘린 zip),
받은 크기를 Content-Length와 대조하고 zip을 열어 확인한 뒤에만 통과시킨다.
#>
function Get-VerifiedZip {
    param([Parameter(Mandatory)][string]$Url,
          [Parameter(Mandatory)][string]$OutFile,
          [int]$Retries = 3)

    Add-Type -AssemblyName System.Net.Http | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null

    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        try {
            Remove-Item $OutFile -Force -ErrorAction SilentlyContinue
            $client = New-Object System.Net.Http.HttpClient
            $client.Timeout = [TimeSpan]::FromMinutes(30)
            $resp = $client.GetAsync($Url, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
            $resp.EnsureSuccessStatusCode() | Out-Null
            $expected = $resp.Content.Headers.ContentLength
            $stream = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
            $file = [System.IO.File]::Create($OutFile)
            $stream.CopyTo($file)
            $file.Close(); $stream.Close(); $client.Dispose()

            $actual = (Get-Item $OutFile).Length
            if ($expected -and $actual -ne $expected) { throw "크기 불일치 ($actual / $expected)" }
            $zip = [System.IO.Compression.ZipFile]::OpenRead($OutFile); $zip.Dispose()
            return
        } catch {
            Info "받기 실패 ($attempt/$Retries): $($_.Exception.Message)"
            if ($attempt -eq $Retries) { throw }
            Start-Sleep -Seconds 3
        }
    }
}

Write-Host ""
Write-Host "  fitbalance 체험판 설치" -ForegroundColor Green
Write-Host "  --------------------------------------------------------"
Write-Host "  처음 실행하면 약 2GB를 내려받습니다. 10~20분 걸립니다."
Write-Host "  이미 설치된 부분은 건너뜁니다."

if (-not (Test-Path $Apk)) { Die "fitbalance.apk 가 없습니다. 압축을 푼 폴더에서 실행해 주세요." }

New-Item -ItemType Directory -Force $Root | Out-Null
$tmp = Join-Path $env:TEMP "fitbalance-install"
New-Item -ItemType Directory -Force $tmp | Out-Null

# 1) JDK ------------------------------------------------------------------
Step "1/6" "자바(JDK 17)"
if (Test-Path "$JdkDir\bin\java.exe") {
    Info "이미 설치됨"
} else {
    Info "내려받는 중 (약 190MB)..."
    $zip = "$tmp\jdk.zip"
    Get-VerifiedZip -Url $JdkUrl -OutFile $zip
    $extract = "$tmp\jdk-extract"
    Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
    Expand-Archive -Path $zip -DestinationPath $extract -Force
    $inner = Get-ChildItem $extract -Directory | Select-Object -First 1
    Remove-Item -Recurse -Force $JdkDir -ErrorAction SilentlyContinue
    Move-Item $inner.FullName $JdkDir
    Info "설치 완료"
}
$env:JAVA_HOME = $JdkDir
$env:PATH = "$JdkDir\bin;$env:PATH"

# 2) SDK 명령줄 도구 -------------------------------------------------------
Step "2/6" "안드로이드 SDK 도구"
$SdkManager = "$SdkDir\cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $SdkManager) {
    Info "이미 설치됨"
} else {
    Info "내려받는 중 (약 130MB)..."
    $zip = "$tmp\cmdline-tools.zip"
    Get-VerifiedZip -Url $CmdUrl -OutFile $zip
    $extract = "$tmp\cmd-extract"
    Remove-Item -Recurse -Force $extract -ErrorAction SilentlyContinue
    Expand-Archive -Path $zip -DestinationPath $extract -Force
    New-Item -ItemType Directory -Force "$SdkDir\cmdline-tools" | Out-Null
    Remove-Item -Recurse -Force "$SdkDir\cmdline-tools\latest" -ErrorAction SilentlyContinue
    Move-Item "$extract\cmdline-tools" "$SdkDir\cmdline-tools\latest"
    Info "설치 완료"
}
$env:ANDROID_HOME = $SdkDir
$env:ANDROID_SDK_ROOT = $SdkDir

# 3) 에뮬레이터와 시스템 이미지 --------------------------------------------
Step "3/6" "에뮬레이터와 안드로이드 이미지 (약 1.5GB, 가장 오래 걸립니다)"
$yes = 1..40 | ForEach-Object { "y" }
$yes | & $SdkManager --sdk_root="$SdkDir" --licenses 2>&1 | Out-Null
& $SdkManager --sdk_root="$SdkDir" "platform-tools" "emulator" $SysImage
if ($LASTEXITCODE -ne 0) { Die "안드로이드 이미지 설치에 실패했습니다. 인터넷 연결을 확인해 주세요." }

# sdkmanager 는 없는 패키지를 만나도 경고만 하고 0 으로 끝나는 일이 있다.
# 폴더가 실제로 생겼는지 눈으로 확인해야 다음 단계가 엉뚱한 이미지로 흘러가지 않는다.
$ImageDir = Join-Path $SdkDir ($SysImage -replace ";", "\")
if (-not (Test-Path (Join-Path $ImageDir "build.prop"))) {
    Die "안드로이드 이미지가 설치되지 않았습니다.`n  기대한 경로: $ImageDir`n  인터넷 연결을 확인하고 다시 실행해 주세요."
}
Info "이미지 확인: $SysImage"

$Adb      = "$SdkDir\platform-tools\adb.exe"
$Emulator = "$SdkDir\emulator\emulator.exe"
$AvdManager = "$SdkDir\cmdline-tools\latest\bin\avdmanager.bat"

# 카카오 지도는 OpenGL ES 3가 필요하다. 에뮬레이터 기본값이 ES2라 켜 준다.
$featuresDir = "$env:USERPROFILE\.android"
New-Item -ItemType Directory -Force $featuresDir | Out-Null
$featuresFile = "$featuresDir\advancedFeatures.ini"
$existing = if (Test-Path $featuresFile) { Get-Content $featuresFile -Raw } else { "" }
if ($existing -notmatch "GLESDynamicVersion") {
    Add-Content $featuresFile "`nGLESDynamicVersion = on"
}

# 4) 가상 기기 -------------------------------------------------------------
Step "4/6" "가상 기기 만들기"

<#
.SYNOPSIS
AVD 가 실제로 어떤 시스템 이미지를 쓰는지 config.ini 에서 읽는다.

이름만 보고 "이미 있으니 넘어가자" 하면 안 된다. 같은 이름으로 다른 이미지를 쓰는
AVD 가 남아 있으면 그걸 그대로 띄우게 되고, 그 경우
  - default(AOSP) 이미지에는 Gboard 가 없어 한글을 못 친다
  - ARM 변환이 없는 이미지에서는 카카오 지도(ARM 전용)가 안 뜬다
겉보기에는 잘 켜지기 때문에 원인을 찾기 어렵다.
#>
function Get-AvdImage {
    param([Parameter(Mandatory)][string]$Name)
    $cfg = Join-Path $env:USERPROFILE ".android\avd\$Name.avd\config.ini"
    if (-not (Test-Path $cfg)) { return $null }
    $line = Select-String -Path $cfg -Pattern '^\s*image\.sysdir\.1\s*=' -ErrorAction SilentlyContinue
    if (-not $line) { return $null }
    # "system-images\android-30\google_apis\x86_64\" -> "system-images;android-30;google_apis;x86_64"
    ($line.Line -replace '^\s*image\.sysdir\.1\s*=\s*', '').Trim().TrimEnd('\', '/') -replace '[\\/]', ';'
}

$current = Get-AvdImage -Name $AvdName
if ($current -eq $SysImage) {
    Info "이미 있음: $AvdName"
} else {
    if ($null -ne $current) {
        Info "기존 $AvdName 이(가) 다른 이미지($current)를 씁니다. 다시 만듭니다."
        & $AvdManager delete avd -n $AvdName 2>&1 | Out-Null
    }
    "no" | & $AvdManager create avd -n $AvdName -k $SysImage -d pixel_6 --force 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Die "가상 기기를 만들지 못했습니다. 다시 실행해 주세요." }

    $made = Get-AvdImage -Name $AvdName
    if ($made -ne $SysImage) {
        Die "가상 기기가 엉뚱한 이미지로 만들어졌습니다.`n  기대: $SysImage`n  실제: $made"
    }
    Info "만들었습니다: $AvdName ($SysImage)"
}

# 5) 에뮬레이터 실행 -------------------------------------------------------
Step "5/6" "에뮬레이터 켜는 중 (처음에는 2~3분 걸립니다)"
$running = (& $Adb devices) -match "emulator-\S+\s+device"
if ($running) {
    Info "이미 켜져 있음"
} else {
    Start-Process -FilePath $Emulator `
        -ArgumentList @("-avd", $AvdName, "-gpu", "host", "-no-snapshot-load") `
        -WindowStyle Normal
    & $Adb wait-for-device | Out-Null
    # wait-for-device 는 부팅 완료까지 기다리지 않는다. 부팅 플래그를 직접 본다.
    for ($i = 0; $i -lt 180; $i++) {
        $boot = (& $Adb shell getprop sys.boot_completed 2>$null) -replace '\s',''
        if ($boot -eq "1") { break }
        Start-Sleep -Seconds 2
    }
    if ($boot -ne "1") { Die "에뮬레이터가 시간 안에 켜지지 않았습니다. 다시 실행해 주세요." }
    Info "켜졌습니다"
}

# 한국어 자판. 없으면 지역 검색을 못 한다.
Info "한국어 설정 중..."
& $Adb shell "settings put system system_locales ko-KR,en-US" 2>&1 | Out-Null
& $Adb root 2>&1 | Out-Null
Start-Sleep -Seconds 4
& $Adb wait-for-device 2>&1 | Out-Null
$localeSet = & $Adb shell "setprop persist.sys.locale ko-KR" 2>&1
if ($localeSet -notmatch "Failed|Must be root") {
    & $Adb shell "stop; start" 2>&1 | Out-Null
    Start-Sleep -Seconds 45
    & $Adb wait-for-device 2>&1 | Out-Null
    Start-Sleep -Seconds 20
    Info "한국어 적용됨 (자판 아래 지구본 키로 전환)"
} else {
    Info "한국어 자동 설정 실패. 설정 > 시스템 > 언어에서 한국어를 추가하세요."
}
& $Adb unroot 2>&1 | Out-Null

# 6) 서버와 앱 -------------------------------------------------------------
Step "6/6" "서버와 앱"

$python = $null
foreach ($cmd in @("python", "py")) {
    $found = Get-Command $cmd -ErrorAction SilentlyContinue
    if ($found) { $python = $found.Source; break }
}
if (-not $python) {
    Info "파이썬이 없어 서버를 켜지 못했습니다."
    Info "https://www.python.org/downloads/ 에서 설치한 뒤 이 파일을 다시 실행하세요."
} else {
    Info "서버 켜는 중..."
    & $python -m pip install --quiet -r (Join-Path $ServerDir "requirements.txt") 2>&1 | Out-Null
    Start-Process -FilePath $python `
        -ArgumentList @("-m", "uvicorn", "server.main:app", "--host", "0.0.0.0", "--port", "8000") `
        -WorkingDirectory $Here -WindowStyle Minimized
    Start-Sleep -Seconds 10
}

Info "앱 설치 중..."
# 카카오 지도 SDK가 ARM 전용이라 ARM 라이브러리를 골라 넣는다.
# google_apis 이미지는 abilist 에 arm64-v8a 가 있어 ARM 변환으로 돌아간다.
$abilist = (& $Adb shell getprop ro.product.cpu.abilist 2>$null) -replace '\s', ''
Info "기기 ABI: $abilist"
if ($abilist -match "arm64-v8a") {
    & $Adb install -r --abi arm64-v8a "$Apk"
} else {
    Info "이 이미지는 ARM 변환을 지원하지 않습니다. 지도 화면이 안 뜰 수 있습니다."
    & $Adb install -r "$Apk"
}
if ($LASTEXITCODE -ne 0) { Die "앱 설치에 실패했습니다." }

& $Adb shell monkey -p $PackageId -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null

Write-Host ""
Write-Host "  설치가 끝났습니다. 에뮬레이터에서 앱이 열립니다." -ForegroundColor Green
Write-Host "  --------------------------------------------------------"
Write-Host "  다음부터는 이 파일을 다시 실행하면 에뮬레이터와 서버만 켭니다."
Write-Host ""
Write-Host "  한글 입력: 입력창을 누른 뒤 자판 아래 지구본 키를 누르세요."
Write-Host "  서버 창(최소화됨)을 닫으면 앱의 진단·추천이 멈춥니다."
Write-Host ""
Write-Host "  창을 닫으려면 아무 키나 누르세요."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
