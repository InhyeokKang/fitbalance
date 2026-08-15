"""
에뮬레이터에 한글 입력을 넣는다.

왜 필요한가
  기본 안드로이드 에뮬레이터는 영어만 설정돼 있어 한글 자판이 없다.
  이 앱은 지역을 한글로 검색하므로 자판이 없으면 시연을 못 한다.
  (`adb shell input text` 도 비ASCII를 못 보낸다.)

하는 일
  1. 시스템 언어에 한국어를 추가한다
  2. 기본 로케일을 ko-KR 로 바꾼다 (root 필요, google_apis 이미지는 된다)
  3. 프레임워크를 재시작해 적용한다

실행
    python tools/emulator_korean.py

쓰는 법
  적용 후 앱에서 입력창을 누르면 자판 아래 지구본 키가 생긴다.
  그걸 누르면 영문 <-> 한글이 전환된다.

  Play 스토어 이미지(google_apis_playstore)는 adb root 가 막혀 있어
  이 스크립트가 실패한다. 그때는 에뮬레이터에서 직접
  설정 > 시스템 > 언어 및 입력 > 언어 에 한국어를 추가하면 된다.
"""

from __future__ import annotations

import re
import subprocess
import sys
import time

ADB_CANDIDATES = [
    r"C:\dev\android-sdk\platform-tools\adb.exe",
    "adb",
]


def find_adb() -> str:
    for candidate in ADB_CANDIDATES:
        try:
            subprocess.run([candidate, "version"], capture_output=True, check=True)
            return candidate
        except (OSError, subprocess.CalledProcessError):
            continue
    sys.exit("adb 를 찾지 못했습니다. Android SDK platform-tools 경로를 확인하세요.")


def sh(adb: str, serial: str, command: str) -> str:
    out = subprocess.run(
        [adb, "-s", serial, "shell", command], capture_output=True, text=True
    )
    return (out.stdout + out.stderr).strip()


def pick_device(adb: str) -> str:
    out = subprocess.run([adb, "devices"], capture_output=True, text=True).stdout
    devices = re.findall(r"^(\S+)\s+device$", out, re.MULTILINE)
    if not devices:
        sys.exit("실행 중인 에뮬레이터가 없습니다. 먼저 에뮬레이터를 켜 주세요.")
    return devices[0]


def main() -> None:
    adb = find_adb()
    serial = pick_device(adb)
    print(f"대상: {serial}")

    print("[1/3] 시스템 언어에 한국어 추가")
    sh(adb, serial, "settings put system system_locales ko-KR,en-US")

    print("[2/3] 기본 로케일을 ko-KR 로 변경")
    subprocess.run([adb, "-s", serial, "root"], capture_output=True, text=True)
    time.sleep(4)
    subprocess.run([adb, "-s", serial, "wait-for-device"], capture_output=True)
    result = sh(adb, serial, "setprop persist.sys.locale ko-KR")
    if "Failed" in result or "Must be root" in result:
        sys.exit(
            "\nroot 권한을 얻지 못했습니다. Play 스토어 이미지로 만든 에뮬레이터로 보입니다.\n"
            "에뮬레이터에서 직접 설정 > 시스템 > 언어 및 입력 > 언어 에 한국어를 추가하세요."
        )

    print("[3/3] 프레임워크 재시작 (1분 정도 걸립니다)")
    sh(adb, serial, "stop; start")
    time.sleep(60)
    subprocess.run([adb, "-s", serial, "wait-for-device"], capture_output=True)
    time.sleep(20)

    locale = sh(adb, serial, "getprop persist.sys.locale")
    if locale != "ko-KR":
        sys.exit(f"적용에 실패했습니다 (현재 로케일: {locale or '없음'})")

    print("\n한국어가 적용됐습니다.")
    print("앱에서 입력창을 누른 뒤 자판 아래 지구본 키로 한글/영문을 전환하세요.")


if __name__ == "__main__":
    main()
