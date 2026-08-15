"""
국민체력100 체력측정 원자료를 공공데이터포털 API에서 받아 저장한다.

준비
  1. https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청 (무료, 자동승인)
  2. 발급받은 **일반 인증키(Decoding)** 를 local.properties 에 추가

        DATA_GO_KR_KEY=여기에인증키

  3. 실행:  python tools/fetch_kspo.py

결과
  - data/raw/kspo_measurements.csv  원본 그대로 (컬럼명이 item_f0xx 형태)
  - data/raw/kspo_readable.csv      우리가 쓰는 항목만 한글 이름으로 추린 것

  두 번째 파일을 최서영에게 넘기면 바로 백분위 작업을 시작할 수 있다.

명세 확인일: 2026-08-15
  Base URL : apis.data.go.kr/B551014/SRVC_NFA_TEST_RESULT
  오퍼레이션: /TODZ_NFA_TEST_RESULT_NEW
  개발계정 일일 트래픽 10,000건
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
RAW_OUT = ROOT / "data" / "raw" / "kspo_measurements.csv"
READABLE_OUT = ROOT / "data" / "raw" / "kspo_readable.csv"

ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_NFA_TEST_RESULT/TODZ_NFA_TEST_RESULT_NEW"
PAGE_SIZE = 1000
MAX_PAGES = 300

# 응답 필드 -> 우리가 쓰는 이름. 공단 명세(2026-08-15 기준)에서 확인한 값이다.
# 성인기 5개 항목 + 백분위를 나누는 데 필요한 성별·나이.
FIELD_MAP = {
    "test_sex": "성별",
    "age_degree": "나이",
    "age_class": "연령대",
    "item_f001": "신장_cm",
    "item_f002": "체중_kg",
    "item_f028": "상대악력_퍼센트",       # grip
    "item_f019": "교차윗몸일으키기_회",   # sit_up
    "item_f012": "앉아윗몸앞으로굽히기_cm",  # sit_reach
    "item_f020": "왕복오래달리기_회",     # shuttle_run
    "item_f022": "제자리멀리뛰기_cm",     # standing_jump
    "test_ym": "측정연월",
}


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
        "  1) https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청 (자동승인)\n"
        "  2) local.properties 에 DATA_GO_KR_KEY=인증키 를 추가하세요\n"
        "     ※ '일반 인증키(Decoding)' 를 쓰세요. Encoding 키를 넣으면 인증에 실패합니다."
    )


def fetch_page(key: str, page: int) -> dict:
    query = urllib.parse.urlencode({
        "serviceKey": key,
        "pageNo": page,
        "numOfRows": PAGE_SIZE,
        "resultType": "json",
    })
    req = urllib.request.Request(f"{ENDPOINT}?{query}",
                                 headers={"User-Agent": "fitbalance/0.1"})
    with urllib.request.urlopen(req, timeout=30) as res:
        raw = res.read().decode("utf-8")
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # 인증 실패·미승인이면 XML 오류가 온다. 원문을 보여주는 편이 진단에 빠르다.
        sys.exit(f"JSON이 아닌 응답입니다. 인증키와 승인 상태를 확인하세요.\n\n{raw[:800]}")


def extract_rows(payload: dict) -> list[dict]:
    """body.items.item 이 표준이지만, 감싸는 구조가 바뀌어도 리스트를 찾아낸다."""
    try:
        item = payload["body"]["items"]["item"]
        return item if isinstance(item, list) else [item]
    except (KeyError, TypeError):
        pass
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
    RAW_OUT.parent.mkdir(parents=True, exist_ok=True)

    rows: list[dict] = []
    fieldnames: list[str] | None = None
    total_count: str | None = None

    for page in range(1, MAX_PAGES + 1):
        try:
            payload = fetch_page(key, page)
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"[중단] {page}쪽에서 통신 실패: {e}")
            break

        header = payload.get("header") or {}
        if header.get("resultCode") not in (None, "00", "0"):
            sys.exit(f"API 오류: {header.get('resultCode')} {header.get('resultMsg')}")

        page_rows = extract_rows(payload)
        if not page_rows:
            break
        if fieldnames is None:
            fieldnames = list(page_rows[0].keys())
            total_count = ((payload.get("body") or {}).get("totalCount"))
            print(f"전체 {total_count}건, 응답 필드 {len(fieldnames)}개")
            missing = [f for f in FIELD_MAP if f not in fieldnames]
            if missing:
                print(f"  ※ 기대한 필드가 없습니다: {missing}")
                print("     공단이 컬럼을 바꿨을 수 있습니다. 강인혁에게 알려 주세요.\n")

        rows.extend(page_rows)
        print(f"  {page}쪽 {len(page_rows)}건 (누적 {len(rows)})")
        if len(page_rows) < PAGE_SIZE:
            break

    if not rows:
        sys.exit("데이터를 한 건도 받지 못했습니다. 활용신청 승인 여부를 확인하세요.")

    with open(RAW_OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames or list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # 최서영에게 넘길 읽기 쉬운 버전
    available = [f for f in FIELD_MAP if f in (fieldnames or [])]
    with open(READABLE_OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow([FIELD_MAP[f] for f in available])
        for r in rows:
            w.writerow([(r.get(f) or "").strip() for f in available])

    print(f"\n원본     : {RAW_OUT.relative_to(ROOT)}  ({len(rows)}건)")
    print(f"정리본   : {READABLE_OUT.relative_to(ROOT)}  (열 {len(available)}개)")
    print("\n정리본을 최서영에게 넘기면 됩니다. 열 구성:")
    for f in available:
        print(f"  {FIELD_MAP[f]:<24} (원본 {f})")


if __name__ == "__main__":
    main()
