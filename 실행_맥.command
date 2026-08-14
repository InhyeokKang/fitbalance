#!/bin/bash
# 맥에서 더블클릭으로 실행한다.
# 처음 한 번은 "확인되지 않은 개발자" 경고가 뜰 수 있다 → 우클릭 → 열기 를 고르면 된다.

cd "$(dirname "$0")" || exit 1

find_python() {
  for candidate in python3.12 python3.11 python3 python; do
    if command -v "$candidate" >/dev/null 2>&1; then
      # 맥 기본 python3 는 Xcode 도구 설치 안내만 띄우는 껍데기일 수 있어 실제로 돌려본다.
      if "$candidate" -c "import sys; sys.exit(0 if sys.version_info >= (3,9) else 1)" >/dev/null 2>&1; then
        echo "$candidate"
        return 0
      fi
    fi
  done
  return 1
}

PY="$(find_python)"

if [ -z "$PY" ]; then
  echo ""
  echo "  ============================================================"
  echo "  파이썬이 설치되어 있지 않습니다."
  echo "  ============================================================"
  echo ""
  echo "  아래 둘 중 하나로 설치하세요."
  echo ""
  echo "  A. 터미널에 이 줄을 붙여넣기 (Xcode 명령줄 도구 설치)"
  echo "       xcode-select --install"
  echo ""
  echo "  B. https://www.python.org/downloads/ 에서 내려받아 설치"
  echo ""
  echo "  설치가 끝나면 이 파일을 다시 더블클릭하세요."
  echo ""
  read -r -p "  엔터를 누르면 창이 닫힙니다. " _
  exit 1
fi

"$PY" tools/launch.py
status=$?

if [ $status -ne 0 ]; then
  read -r -p "  엔터를 누르면 창이 닫힙니다. " _
fi
