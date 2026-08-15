"""
홈페이지에서 내려받을 배포 파일을 만든다.

만드는 것
    dist/fitbalance-windows.zip   설치.bat + 설치.ps1 + APK + 서버 + 데이터
    dist/fitbalance-mac.zip       설치.command + APK + 서버 + 데이터
    dist/fitbalance.apk           APK 단독 (안드로이드 폰이 있는 사람용)

왜 서버를 같이 넣나
  진단·추천·지역 검색이 전부 서버에 있다. 앱만 깔면 아무것도 안 된다.
  설치 스크립트가 압축 푼 자리에서 서버를 띄우므로 서버 코드와 data/*.csv가
  같이 들어가야 한다.

실행
    (먼저) gradlew assembleDebug
    python tools/build_release.py

만든 파일은 GitHub 릴리스에 올린다. 홈페이지 버튼이 릴리스를 가리킨다.
"""

from __future__ import annotations

import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
APK_SRC = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

# 서버를 돌리는 데 실제로 필요한 것만 담는다. 앱 소스와 원자료는 뺀다.
SERVER_FILES = [
    Path("server/main.py"),
    Path("server/requirements.txt"),
]
SERVER_DIRS = [Path("server/static")]
DATA_FILES = [
    Path("data/norms.csv"),
    Path("data/courses.csv"),
    Path("data/facility_addresses.csv"),
    Path("data/places.csv"),
    Path("data/centers.csv"),
    Path("data/courses_seed.csv"),
]

READ_ME = """fitbalance 체험판

설치 방법
{how}

무엇이 설치되나
  안드로이드 에뮬레이터와 앱, 그리고 추천 서버가 함께 설치됩니다.
  처음 실행하면 약 2GB를 내려받습니다(10~20분). 다음부터는 켜기만 합니다.
  설치되는 곳은 {where} 안뿐이고, 기존 설정은 건드리지 않습니다.

알아 둘 것
  - 서버 창을 닫으면 앱의 진단·추천·검색이 멈춥니다.
  - 한글 입력: 입력창을 누른 뒤 자판 아래 지구본 키를 누르세요.
  - 파이썬이 없으면 서버가 안 켜집니다. https://www.python.org/downloads/

문의 · 소스
  https://github.com/InhyeokKang/fitbalance
"""


def add_common(zf: zipfile.ZipFile) -> None:
    """서버·데이터·APK 등 두 운영체제가 똑같이 쓰는 것."""
    for rel in SERVER_FILES + DATA_FILES:
        src = ROOT / rel
        if not src.exists():
            sys.exit(f"{rel} 가 없습니다.")
        zf.write(src, rel.as_posix())

    for rel in SERVER_DIRS:
        src = ROOT / rel
        if not src.exists():
            continue
        for f in src.rglob("*"):
            if f.is_file():
                zf.write(f, f.relative_to(ROOT).as_posix())

    zf.write(APK_SRC, "fitbalance.apk")


def build_windows() -> Path:
    out = DIST / "fitbalance-windows.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.write(ROOT / "installer/windows/설치.bat", "설치.bat")
        zf.write(ROOT / "installer/windows/설치.ps1", "설치.ps1")
        zf.writestr("읽어주세요.txt", READ_ME.format(
            how="  1. 이 폴더의 압축을 풉니다\n  2. 설치.bat 을 두 번 누릅니다",
            where="C:\\dev",
        ))
        add_common(zf)
    return out


def build_mac() -> Path:
    out = DIST / "fitbalance-mac.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zf:
        script = ROOT / "installer/mac/설치.command"
        # 맥에서 두 번 눌러 실행하려면 실행 권한이 zip 안에 남아 있어야 한다.
        info = zipfile.ZipInfo("설치.command")
        info.external_attr = 0o100755 << 16
        info.compress_type = zipfile.ZIP_DEFLATED
        zf.writestr(info, script.read_bytes())

        zf.writestr("읽어주세요.txt", READ_ME.format(
            how=("  1. 이 폴더의 압축을 풉니다\n"
                 "  2. 설치.command 를 두 번 누릅니다\n"
                 "     (막히면 오른쪽 클릭 > 열기 를 고르세요)"),
            where="~/fitbalance-tools",
        ))
        add_common(zf)
    return out


def main() -> None:
    if not APK_SRC.exists():
        sys.exit("APK가 없습니다. 먼저 gradlew assembleDebug 를 실행하세요.")

    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir()

    made = [build_windows(), build_mac()]
    shutil.copy(APK_SRC, DIST / "fitbalance.apk")
    made.append(DIST / "fitbalance.apk")

    print("배포 파일을 만들었습니다.\n")
    for f in made:
        print(f"  {f.relative_to(ROOT).as_posix():<34} {f.stat().st_size / 1048576:>6.1f} MB")
    print("\n다음: GitHub 릴리스에 이 세 파일을 올리면 홈페이지 버튼이 연결됩니다.")


if __name__ == "__main__":
    main()
