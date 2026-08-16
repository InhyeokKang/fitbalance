#!/bin/bash
# fitbalance 체험판 설치 (macOS)
#
# 안드로이드 폰이 없어도 앱을 볼 수 있게, 에뮬레이터부터 앱까지 한 번에 깐다.
#
#   1) JDK 17         - 안드로이드 SDK 관리 도구가 자바로 돌아간다
#   2) SDK 명령줄 도구
#   3) 에뮬레이터 + 안드로이드 이미지 (약 1.5GB)
#   4) 가상 기기(AVD) 생성 + 한국어 설정
#   5) 백엔드 서버 실행
#   6) 앱 설치 후 실행
#
# 전부 ~/fitbalance-tools 아래에만 설치한다. 기존 설정은 건드리지 않는다.
# 이미 있는 단계는 건너뛴다. 다시 실행해도 안전하다.
#
# Apple Silicon(M1 이상)은 arm64 이미지를 쓴다. 변환 없이 도는 만큼 훨씬 빠르고,
# 카카오 지도도 그대로 뜬다.

set -e
cd "$(dirname "$0")"
HERE="$(pwd)"

ROOT="$HOME/fitbalance-tools"
JDK_DIR="$ROOT/jdk-17"
SDK_DIR="$ROOT/android-sdk"
AVD_NAME="fitbalance"
PACKAGE="com.fitbalance.app"
APK="$HERE/fitbalance.apk"

# 칩에 맞는 이미지를 고른다. 애플 실리콘은 arm64가 네이티브다.
if [ "$(uname -m)" = "arm64" ]; then
  SYS_IMAGE="system-images;android-30;google_apis;arm64-v8a"
  JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse?project=jdk"
  ABI="arm64-v8a"
else
  SYS_IMAGE="system-images;android-30;google_apis;x86_64"
  JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/mac/x64/jdk/hotspot/normal/eclipse?project=jdk"
  ABI="arm64-v8a"
fi
CMD_URL="https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip"

step() { printf "\n\033[36m[%s] %s\033[0m\n" "$1" "$2"; }
info() { printf "     \033[90m%s\033[0m\n" "$1"; }
die()  { printf "\n\033[31m%s\033[0m\n\n" "$1"; read -r -p "엔터를 누르면 닫힙니다."; exit 1; }

# set -e 로 중간에 죽더라도 창이 그냥 사라지지 않게 한다.
# 창이 닫혀 버리면 무엇 때문에 멈췄는지 알 길이 없다.
on_error() {
  printf "\n\033[31m예상치 못한 오류로 멈췄습니다 (%s행).\033[0m\n" "$1"
  printf "이 내용을 그대로 알려 주세요.\n\n"
  read -r -p "엔터를 누르면 닫힙니다."
  exit 1
}
trap 'on_error "$LINENO"' ERR

# adb wait-for-device 는 시간 제한이 없어, 기기가 끝내 안 돌아오면
# 아무 출력 없이 영원히 멈춘다. 제한을 둔다.
wait_device() {
  local limit="${1:-180}" i=0
  while [ "$i" -lt "$limit" ]; do
    [ "$("$ADB" get-state 2>/dev/null | tr -d '\r\n')" = "device" ] && return 0
    sleep 1
    i=$((i + 1))
  done
  return 1
}

booted() {
  [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ]
}

echo ""
printf "\033[32m  fitbalance 체험판 설치\033[0m\n"
echo "  --------------------------------------------------------"
echo "  처음 실행하면 약 2GB를 내려받습니다. 10~20분 걸립니다."
echo "  이미 설치된 부분은 건너뜁니다."

[ -f "$APK" ] || die "fitbalance.apk 가 없습니다. 압축을 푼 폴더에서 실행해 주세요."

mkdir -p "$ROOT"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 1) JDK ------------------------------------------------------------------
step "1/6" "자바(JDK 17)"
JAVA_BIN="$JDK_DIR/Contents/Home/bin/java"
if [ -x "$JAVA_BIN" ]; then
  info "이미 설치됨"
else
  info "내려받는 중 (약 180MB)..."
  curl -fL --retry 3 -o "$TMP/jdk.tar.gz" "$JDK_URL" || die "JDK 내려받기에 실패했습니다."
  mkdir -p "$TMP/jdk" && tar -xzf "$TMP/jdk.tar.gz" -C "$TMP/jdk"
  rm -rf "$JDK_DIR"
  mv "$TMP/jdk"/*/ "$JDK_DIR"
  info "설치 완료"
fi
export JAVA_HOME="$JDK_DIR/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# 2) SDK 명령줄 도구 -------------------------------------------------------
step "2/6" "안드로이드 SDK 도구"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  info "이미 설치됨"
else
  info "내려받는 중 (약 130MB)..."
  curl -fL --retry 3 -o "$TMP/cmdline-tools.zip" "$CMD_URL" || die "SDK 도구 내려받기에 실패했습니다."
  mkdir -p "$TMP/cmd" && unzip -q "$TMP/cmdline-tools.zip" -d "$TMP/cmd"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$TMP/cmd/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  info "설치 완료"
fi
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"

# 3) 에뮬레이터와 시스템 이미지 --------------------------------------------
step "3/6" "에뮬레이터와 안드로이드 이미지 (약 1.5GB, 가장 오래 걸립니다)"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" "platform-tools" "emulator" "$SYS_IMAGE" \
  || die "안드로이드 이미지 설치에 실패했습니다. 인터넷 연결을 확인해 주세요."

# sdkmanager 는 없는 패키지를 만나도 경고만 하고 0 으로 끝나는 일이 있다.
# 폴더가 실제로 생겼는지 눈으로 확인해야 다음 단계가 엉뚱한 이미지로 흘러가지 않는다.
IMAGE_DIR="$SDK_DIR/$(echo "$SYS_IMAGE" | tr ';' '/')"
[ -f "$IMAGE_DIR/build.prop" ] \
  || die "안드로이드 이미지가 설치되지 않았습니다.
  기대한 경로: $IMAGE_DIR
  인터넷 연결을 확인하고 다시 실행해 주세요."
info "이미지 확인: $SYS_IMAGE"

ADB="$SDK_DIR/platform-tools/adb"
EMULATOR="$SDK_DIR/emulator/emulator"
AVDMANAGER="$SDK_DIR/cmdline-tools/latest/bin/avdmanager"

# 카카오 지도는 OpenGL ES 3가 필요하다. 에뮬레이터 기본값이 ES2라 켜 준다.
mkdir -p "$HOME/.android"
grep -q "GLESDynamicVersion" "$HOME/.android/advancedFeatures.ini" 2>/dev/null \
  || echo "GLESDynamicVersion = on" >> "$HOME/.android/advancedFeatures.ini"

# 4) 가상 기기 -------------------------------------------------------------
step "4/6" "가상 기기 만들기"

# AVD 가 실제로 어떤 이미지를 쓰는지 config.ini 에서 읽는다.
# 이름만 보고 "이미 있으니 넘어가자" 하면 안 된다. 같은 이름으로 다른 이미지를 쓰는
# AVD 가 남아 있으면 그걸 그대로 띄우게 되고, 그 경우
#   - default(AOSP) 이미지에는 Gboard 가 없어 한글을 못 친다
#   - ARM 변환이 없는 이미지에서는 카카오 지도(ARM 전용)가 안 뜬다
# 겉보기에는 잘 켜지기 때문에 원인을 찾기 어렵다.
avd_image() {
  local cfg="$HOME/.android/avd/$AVD_NAME.avd/config.ini"
  [ -f "$cfg" ] || return 0
  sed -n 's|^ *image\.sysdir\.1 *= *||p' "$cfg" \
    | head -n 1 | tr -d '\r' | sed 's|/*$||' | tr '/' ';'
}

CURRENT="$(avd_image)"
if [ "$CURRENT" = "$SYS_IMAGE" ]; then
  info "이미 있음: $AVD_NAME"
else
  if [ -n "$CURRENT" ]; then
    info "기존 $AVD_NAME 이(가) 다른 이미지($CURRENT)를 씁니다. 다시 만듭니다."
    "$AVDMANAGER" delete avd -n "$AVD_NAME" > /dev/null 2>&1 || true
  fi
  echo "no" | "$AVDMANAGER" create avd \
    -n "$AVD_NAME" -k "$SYS_IMAGE" -d pixel_6 --force > /dev/null \
    || die "가상 기기를 만들지 못했습니다. 다시 실행해 주세요."

  MADE="$(avd_image)"
  [ "$MADE" = "$SYS_IMAGE" ] || die "가상 기기가 엉뚱한 이미지로 만들어졌습니다.
  기대: $SYS_IMAGE
  실제: $MADE"
  info "만들었습니다: $AVD_NAME ($SYS_IMAGE)"
fi

# 5) 에뮬레이터 실행 -------------------------------------------------------
step "5/6" "에뮬레이터 켜는 중 (처음에는 2~3분 걸립니다)"
RUNNING=0
"$ADB" devices 2>/dev/null | grep -q "emulator-.*device" && RUNNING=1
if [ "$RUNNING" = "1" ] && booted; then
  info "이미 켜져 있음"
else
  if [ "$RUNNING" = "0" ]; then
    "$EMULATOR" -avd "$AVD_NAME" -gpu host -no-snapshot-load > /dev/null 2>&1 &
  fi
  wait_device 180 || die "에뮬레이터가 붙지 않았습니다. 다시 실행해 주세요."

  # 기기가 붙어도 부팅은 아직이다. 부팅 플래그를 직접 본다.
  BOOTED=0
  for i in $(seq 1 180); do
    if booted; then BOOTED=1; break; fi
    [ $((i % 30)) -eq 0 ] && info "부팅 대기 중... ($((i * 2))초)"
    sleep 2
  done
  [ "$BOOTED" = "1" ] || die "에뮬레이터가 시간 안에 켜지지 않았습니다. 다시 실행해 주세요."
  info "켜졌습니다"
fi

# 한국어 자판. 없으면 지역 검색을 못 한다.
# 이 구간은 프레임워크를 재시작하기 때문에 1~2분 걸린다.
info "한국어 설정 중... (1~2분, 화면이 한 번 꺼졌다 켜집니다)"
"$ADB" shell "settings put system system_locales ko-KR,en-US" > /dev/null 2>&1 || true
"$ADB" root > /dev/null 2>&1 || true
sleep 4
wait_device 60 || true
if "$ADB" shell "setprop persist.sys.locale ko-KR" 2>&1 | grep -qi "failed\|must be root\|error"; then
  info "한국어 자동 설정 실패. 설정 > 시스템 > 언어에서 한국어를 추가하세요."
else
  "$ADB" shell "stop; start" > /dev/null 2>&1 || true
  sleep 10
  if wait_device 120; then
    info "  화면 다시 켜지는 중..."
    for _ in $(seq 1 90); do
      if booted; then break; fi
      sleep 2
    done
    sleep 10
    info "한국어 적용됨 (자판 아래 지구본 키로 전환)"
  else
    info "재시작이 늦어집니다. 한국어는 설정 > 시스템 > 언어에서 추가하세요."
  fi
fi
"$ADB" unroot > /dev/null 2>&1 || true

# 6) 서버와 앱 -------------------------------------------------------------
step "6/6" "서버와 앱"

PYTHON=""
for c in python3 python; do
  command -v "$c" > /dev/null 2>&1 && { PYTHON="$c"; break; }
done
if [ -z "$PYTHON" ]; then
  info "파이썬이 없어 서버를 켜지 못했습니다."
  info "https://www.python.org/downloads/ 에서 설치한 뒤 이 파일을 다시 실행하세요."
else
  info "서버 준비 중... (처음에는 1~2분 걸립니다)"
  "$PYTHON" -m pip install --quiet -r "$HERE/server/requirements.txt" > /dev/null 2>&1 || true
  info "서버 켜는 중..."
  ( cd "$HERE" && "$PYTHON" -m uvicorn server.main:app --host 0.0.0.0 --port 8000 \
      > "$HERE/server.log" 2>&1 & )
  sleep 10
fi

info "앱 설치 중..."
# 카카오 지도 SDK가 ARM 전용이라 ARM 라이브러리를 골라 넣는다.
# google_apis 이미지는 abilist 에 arm64-v8a 가 있어 ARM 변환으로 돌아간다.
ABILIST="$("$ADB" shell getprop ro.product.cpu.abilist 2>/dev/null | tr -d '\r\n' || true)"
info "기기 ABI: $ABILIST"
info "  48MB 를 밀어 넣습니다. 30초~1분 걸립니다."
if echo "$ABILIST" | grep -q "arm64-v8a"; then
  "$ADB" install -r --abi "$ABI" "$APK" || die "앱 설치에 실패했습니다."
else
  info "이 이미지는 ARM 변환을 지원하지 않습니다. 지도 화면이 안 뜰 수 있습니다."
  "$ADB" install -r "$APK" || die "앱 설치에 실패했습니다."
fi
"$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || true

echo ""
printf "\033[32m  설치가 끝났습니다. 에뮬레이터에서 앱이 열립니다.\033[0m\n"
echo "  --------------------------------------------------------"
echo "  다음부터는 이 파일을 다시 실행하면 에뮬레이터와 서버만 켭니다."
echo ""
echo "  한글 입력: 입력창을 누른 뒤 자판 아래 지구본 키를 누르세요."
echo "  서버를 끄려면 터미널에서  pkill -f 'uvicorn server.main'"
echo ""
read -r -p "엔터를 누르면 닫힙니다."
