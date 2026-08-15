"""
국민체력100 체력측정 원자료를 공공데이터포털 API에서 받아 저장한다.

준비
  1. https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청 (무료, 대개 즉시 승인)
  2. 발급받은 **일반 인증키(Decoding)** 를 local.properties 에 추가

        DATA_GO_KR_KEY=여기에인증키

  3. 실행:  python tools/fetch_kspo.py

결과
  data/raw/kspo_measurements.csv 로 저장한다. 이 파일을 최서영에게 넘기면
  백분위 기준표(norms.csv)를 만들 수 있다. tools/build_norms.py 로 자동 생성도 가능하다.

주의
  이 스크립트는 응답 필드명을 미리 알지 못한다. 공단이 컬럼을 여러 번 바꿔 왔기 때문에,
  **처음 받은 한 건의 키를 그대로 헤더로 삼아** 저장한다. 실제 필드명이 무엇이든
  원본 그대로 남는다. 어떤 필드가 있었는지는 실행 후 화면에 출력된다.
"""

from __future__ import annotations

import csv
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "raw" / "kspo_measurements.csv"
ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_MESURE_ACTO_LIST/TODZ_MESURE_ACTO_LIST"
PAGE_SIZE = 1000
MAX_PAGES = 200


def read_key() -> str:
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("DATA_GO_KR_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return key
    sys.exit(
        "인증키가 없습니다.\n"
        "  1) https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청\n"
        "  2) local.properties 에 DATA_GO_KR_KEY=인증키 를 추가하세요"
    )


def fetch_page(key: str, page: int) -> dict:
    query = urllib.parse.urlencode({
        "serviceKey": key,
        "pageNo": page,
        "numOfRows": PAGE_SIZE,
        "resultType": "json",
    })
    req = urllib.request.Request(f"{ENDPOINT}?{query}", headers={"User-Agent": "fitbalance/0.1"})
    with urllib.request.urlopen(req, timeout=30) as res:
        raw = res.read().decode("utf-8")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # 인증 실패·미승인 시 XML 오류가 온다. 그대로 보여 주는 편이 진단에 낫다.
        sys.exit(f"JSON이 아닌 응답을 받았습니다. 인증키·승인 상태를 확인하세요.\n\n{raw[:600]}")


def extract_rows(payload: dict) -> list[dict]:
    """공단 응답은 감싸는 구조가 자주 바뀐다. 리스트를 찾을 때까지 파고든다."""
    stack = [payload]
    while stack:
        node = stack.pop()
        if isinstance(node, list) and node and isinstance(node[0], dict):
            return node
        if isinstance(node, dict):
            stack.extend(node.values())
    return []


def main() -> None:
    key = read_key()
    OUT.parent.mkdir(parents=True, exist_ok=True)

    all_rows: list[dict] = []
    fieldnames: list[str] | None = None

    for page in range(1, MAX_PAGES + 1):
        try:
            payload = fetch_page(key, page)
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"[중단] {page}쪽에서 통신 실패: {e}")
            break

        rows = extract_rows(payload)
        if not rows:
            break
        if fieldnames is None:
            fieldnames = list(rows[0].keys())
            print(f"응답 필드 {len(fieldnames)}개:")
            for name in fieldnames:
                print(f"  - {name}")
            print()
            # 성별 컬럼이 없으면 성별 구분 기준표를 만들 수 없다. 먼저 알아야 한다.
            if not any("SEX" in f.upper() or "GEND" in f.upper() for f in fieldnames):
                print("  ※ 경고: 성별로 보이는 컬럼이 없습니다.")
                print("     성별 구분 기준표를 만들 수 없으니 강인혁에게 알려 주세요.\n")

        all_rows.extend(rows)
        print(f"  {page}쪽 {len(rows)}건 (누적 {len(all_rows)}건)")
        if len(rows) < PAGE_SIZE:
            break

    if not all_rows:
        sys.exit("데이터를 한 건도 받지 못했습니다. 활용신청 승인 여부를 확인하세요.")

    with open(OUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames or list(all_rows[0].keys()))
        writer.writeheader()
        writer.writerows(all_rows)

    print(f"\n{OUT.relative_to(ROOT)} 에 {len(all_rows)}건 저장했습니다.")
    print("이 파일을 최서영에게 넘기거나, tools/build_norms.py 로 기준표를 만드세요.")


if __name__ == "__main__":
    main()
