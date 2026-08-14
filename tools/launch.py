"""
fitbalance 데모를 한 번에 띄운다. 윈도우·맥 공통.

하는 일
  1. 파이썬 가상환경(.venv)이 없으면 만든다
  2. 필요한 패키지를 설치한다 (이미 맞으면 건너뛴다)
  3. 샘플 데이터(csv)가 없으면 만든다
  4. 서버를 켜고, 준비되면 브라우저로 데모 화면을 연다
  5. 창을 닫거나 Ctrl+C 하면 서버도 같이 꺼진다

직접 실행할 일은 없다. 윈도우는 실행.bat, 맥은 실행.command 를 더블클릭하면 된다.
"""

from __future__ import annotations

import os
import platform
import subprocess
import sys
import time
import urllib.error
import urllib.request
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VENV = ROOT / ".venv"
SERVER_DIR = ROOT / "server"
DATA_DIR = ROOT / "data"
REQUIREMENTS = SERVER_DIR / "requirements.txt"
PORT = 8000
DEMO_URL = f"http://127.0.0.1:{PORT}/demo"
IS_WINDOWS = platform.system() == "Windows"


def say(message: str) -> None:
    print(f"  {message}", flush=True)


def fail(message: str) -> None:
    print("")
    print("  " + "=" * 56)
    print(f"  실행하지 못했습니다: {message}")
    print("  " + "=" * 56)
    print("")
    print("  이 화면을 통째로 캡처해서 강인혁에게 보내주세요.")
    input("\n  엔터를 누르면 창이 닫힙니다. ")
    sys.exit(1)


def venv_python() -> Path:
    return VENV / ("Scripts/python.exe" if IS_WINDOWS else "bin/python")


def ensure_venv() -> Path:
    """가상환경을 만들고 그 안의 파이썬 경로를 돌려준다."""
    python = venv_python()
    if python.exists():
        return python

    say("처음 실행이라 준비 작업을 합니다. 1~2분 걸립니다.")
    say("파이썬 환경 만드는 중...")
    result = subprocess.run(
        [sys.executable, "-m", "venv", str(VENV)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0 or not python.exists():
        fail(f"파이썬 환경을 만들지 못했습니다.\n  {result.stderr.strip()[:300]}")
    return python


def ensure_packages(python: Path) -> None:
    """필요한 패키지를 설치한다. 이미 맞으면 pip가 알아서 건너뛴다."""
    marker = VENV / ".installed"
    if marker.exists() and marker.stat().st_mtime >= REQUIREMENTS.stat().st_mtime:
        return

    say("필요한 프로그램 설치하는 중...")
    result = subprocess.run(
        [str(python), "-m", "pip", "install", "-q", "--disable-pip-version-check",
         "-r", str(REQUIREMENTS)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(f"설치에 실패했습니다.\n  {result.stderr.strip()[:400]}")
    marker.write_text("ok", encoding="utf-8")


def ensure_data(python: Path) -> None:
    """샘플 데이터가 없으면 만든다. 실데이터로 교체돼 있으면 건드리지 않는다."""
    if not (DATA_DIR / "norms.csv").exists() or not (DATA_DIR / "courses.csv").exists():
        say("샘플 데이터 만드는 중...")
        subprocess.run([str(python), str(DATA_DIR / "_make_sample.py")], cwd=ROOT)

    if not (DATA_DIR / "facility_addresses.csv").exists():
        subprocess.run([str(python), str(DATA_DIR / "_make_facility_addresses.py")], cwd=ROOT)


def wait_until_ready(timeout_sec: int = 60) -> bool:
    """서버가 응답할 때까지 기다린다."""
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(
                f"http://127.0.0.1:{PORT}/api/v1/health", timeout=2
            ) as response:
                if response.status == 200:
                    return True
        except (urllib.error.URLError, OSError):
            time.sleep(0.7)
    return False


def main() -> None:
    print("")
    print("  fitbalance 데모")
    print("  " + "-" * 56)

    python = ensure_venv()
    ensure_packages(python)
    ensure_data(python)

    say("서버 켜는 중...")
    server = subprocess.Popen(
        [str(python), "-m", "uvicorn", "main:app", "--host", "127.0.0.1", "--port", str(PORT)],
        cwd=SERVER_DIR,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE,
        text=True,
    )

    if not wait_until_ready():
        server.terminate()
        stderr = ""
        try:
            stderr = (server.stderr.read() or "")[-500:]
        except Exception:
            pass
        # 8000번을 이미 다른 프로그램이 쓰고 있는 경우가 가장 흔하다.
        fail(f"서버가 켜지지 않았습니다.\n  {stderr.strip()}")

    say(f"준비 완료. 브라우저를 엽니다: {DEMO_URL}")
    webbrowser.open(DEMO_URL)

    print("  " + "-" * 56)
    print("")
    print("  브라우저가 안 열리면 아래 주소를 직접 입력하세요.")
    print(f"      {DEMO_URL}")
    print("")
    print("  끝낼 때는 이 창을 닫거나 Ctrl+C 를 누르세요.")
    print("")

    try:
        server.wait()
    except KeyboardInterrupt:
        pass
    finally:
        server.terminate()
        try:
            server.wait(timeout=5)
        except subprocess.TimeoutExpired:
            server.kill()
        print("\n  서버를 껐습니다.")


if __name__ == "__main__":
    main()
