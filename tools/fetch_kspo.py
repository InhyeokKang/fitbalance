"""
국민체력100 체력측정 원자료를 공공데이터포털 API에서 받아 저장한다.

준비
  1. https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청 (무료, 자동승인)
  2. local.properties 에 인증키 추가 (Encoding/Decoding 어느 쪽이든 된다)

        DATA_GO_KR_KEY=여기에인증키

  3. 실행:  python tools/fetch_kspo.py

왜 전량을 받지 않는가
  전체가 294만 건이라 다 받으면 2,938번을 호출해야 하고 CSV가 수백 MB가 된다.
  백분위 기준표를 만드는 데는 그만큼 필요하지 않다. 성별×나이대(9구간) = 18칸에
  각각 수천 건만 있으면 백분위가 안정된다. 그래서 **전 구간에 걸쳐 균등 표본**을 뽑는다.
  (페이지를 일정 간격으로 건너뛰며 받는다. 데이터가 측정연월 순이라 시기 편향도 줄어든다.)

확인한 사실 (2026-08-15)
  - Base URL   : apis.data.go.kr/B551014/SRVC_NFA_TEST_RESULT
  - 오퍼레이션  : /TODZ_NFA_TEST_RESULT_NEW
  - 전체 건수   : 2,937,483
  - 수록 기간   : 2011-01 ~ 2026-07  (최신 자료까지 들어 있다)
  - 먹히는 필터 : test_sex, age_gbn      / 안 먹히는 필터: test_ym, age_degree
  - 개발계정 일일 트래픽 10,000건
"""

from __future__ import annotations

import csv
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW_OUT = ROOT / "data" / "raw" / "kspo_measurements.csv"
READABLE_OUT = ROOT / "data" / "raw" / "kspo_readable.csv"

ENDPOINT = "https://apis.data.go.kr/B551014/SRVC_NFA_TEST_RESULT/TODZ_NFA_TEST_RESULT_NEW"
PAGE_SIZE = 1000
SAMPLE_PAGES = 150          # 약 15만 건. 기준표를 만들기에 충분하다
REQUEST_GAP_SEC = 0.2       # 연속 호출 간 최소 간격

# 응답 필드 -> 우리가 쓰는 이름. 공단 명세(2026-08-15)에서 확인한 값이다.
FIELD_MAP = {
    "test_sex": "성별",
    "age_degree": "나이",
    "age_gbn": "연령구분",
    "item_f001": "신장_cm",
    "item_f002": "체중_kg",
    "item_f028": "상대악력_퍼센트",          # grip
    "item_f019": "교차윗몸일으키기_회",      # sit_up
    "item_f012": "앉아윗몸앞으로굽히기_cm",  # sit_reach
    "item_f020": "왕복오래달리기_회",        # shuttle_run
    "item_f022": "제자리멀리뛰기_cm",        # standing_jump
    "test_ym": "측정연월",
}


def read_key() -> str:
    """포털이 주는 Encoding/Decoding 키 어느 쪽을 넣어도 되도록 맞춘다."""
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("DATA_GO_KR_KEY="):
                key = line.split("=", 1)[1].strip()
                if not key:
                    break
                # urlencode가 다시 인코딩하므로 Encoding 키는 풀어서 넘긴다
                return urllib.parse.unquote(key) if "%" in key else key
    sys.exit(
        "인증키가 없습니다.\n"
        "  1) https://www.data.go.kr/data/15108938/openapi.do 에서 활용신청 (자동승인)\n"
        "  2) local.properties 에 DATA_GO_KR_KEY=인증키 를 추가하세요"
    )


def call(key: str, page: int, rows: int) -> tuple[int, list[dict]]:
    query = urllib.parse.urlencode({
        "serviceKey": key, "pageNo": page, "numOfRows": rows,
        "resultType": "json", "age_gbn": "성인",
    })
    req = urllib.request.Request(f"{ENDPOINT}?{query}",
                                 headers={"User-Agent": "fitbalance/0.1"})
    with urllib.request.urlopen(req, timeout=60) as res:
        raw = res.read().decode("utf-8")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError:
        sys.exit(f"JSON이 아닌 응답입니다. 인증키와 승인 상태를 확인하세요.\n\n{raw[:800]}")

    body = payload.get("response", payload).get("body", {})
    header = payload.get("response", payload).get("header", {})
    if header.get("resultCode") not in (None, "00", "0"):
        sys.exit(f"API 오류: {header.get('resultCode')} {header.get('resultMsg')}")

    items = (body.get("items") or {}).get("item") or []
    if isinstance(items, dict):
        items = [items]
    return int(body.get("totalCount") or 0), items


def main() -> None:
    key = read_key()
    RAW_OUT.parent.mkdir(parents=True, exist_ok=True)

    total, _ = call(key, 1, 1)
    last_page = (total + PAGE_SIZE - 1) // PAGE_SIZE
    step = max(1, last_page // SAMPLE_PAGES)
    pages = list(range(1, last_page + 1, step))[:SAMPLE_PAGES]

    print(f"성인 자료 {total:,}건 (총 {last_page:,}쪽)")
    print(f"{step}쪽 간격으로 {len(pages)}쪽을 뽑습니다. 예상 {len(pages) * PAGE_SIZE:,}건\n")

    rows: list[dict] = []
    fieldnames: list[str] | None = None

    for i, page in enumerate(pages, start=1):
        try:
            _, got = call(key, page, PAGE_SIZE)
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"  [건너뜀] {page}쪽: {e}")
            continue

        if fieldnames is None and got:
            fieldnames = list(got[0].keys())
            missing = [f for f in FIELD_MAP if f not in fieldnames]
            if missing:
                print(f"  ※ 기대한 필드가 없습니다: {missing}\n")

        rows.extend(got)
        if i % 20 == 0 or i == len(pages):
            print(f"  {i}/{len(pages)}쪽  누적 {len(rows):,}건")
        time.sleep(REQUEST_GAP_SEC)

    if not rows:
        sys.exit("데이터를 한 건도 받지 못했습니다.")

    with open(RAW_OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames or list(rows[0].keys()),
                           extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)

    available = [f for f in FIELD_MAP if f in (fieldnames or [])]
    with open(READABLE_OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow([FIELD_MAP[f] for f in available])
        for r in rows:
            w.writerow([str(r.get(f, "")).strip() for f in available])

    yms = sorted({str(r.get("test_ym", "")) for r in rows if r.get("test_ym")})
    print(f"\n원본   : {RAW_OUT.relative_to(ROOT)}  ({len(rows):,}건)")
    print(f"정리본 : {READABLE_OUT.relative_to(ROOT)}  (열 {len(available)}개)")
    if yms:
        print(f"수록 기간: {yms[0]} ~ {yms[-1]}")
    print("\n다음: python tools/build_norms.py  로 기준표를 만들 수 있습니다.")


if __name__ == "__main__":
    main()
