"""
공공체육시설 정보를 받아 강좌표의 시설·주소·좌표를 채운다.

준비
  아래 데이터셋에 **각각 활용신청**이 필요하다. 둘 다 자동승인이라 즉시 쓸 수 있다.
  (공공데이터포털은 데이터셋마다 신청을 따로 받는다. 국민체력100 키가 있어도
   이 API는 403이 난다.)

    공공체육시설 상세 정보  https://www.data.go.kr/data/15107764/openapi.do
    전국체육시설 정보      https://www.data.go.kr/data/15113986/openapi.do

  인증키는 국민체력100과 같은 것을 쓴다(계정당 하나). local.properties 의
  DATA_GO_KR_KEY 를 그대로 사용한다.

실행
    python tools/fetch_facilities.py              # 수도권만 (기본)
    python tools/fetch_facilities.py --all        # 전국

결과
    data/raw/facilities.csv   시설명·업종·유형·도로명주소·위경도·시도·시군구

이 파일로 courses.csv 의 facility / lat / lng 와 facility_addresses.csv 를 채운다.
강좌 시간표(요일·시작시각)는 이 데이터에 없다. 아래 '한계' 참고.

한계
  공공데이터에는 **강좌 시간표를 전국 단위로 제공하는 데이터셋이 없다.**
  - 스포츠강좌이용권 강좌정보(문화데이터광장 API)는 취약계층 유·청소년 대상이고
    시설명·요일·주소가 없어 우리 용도에 맞지 않는다.
  - 전국공공시설개방정보표준데이터에는 시설 운영시간은 있으나 강좌 시간표는 없다.
  따라서 요일·시작시각은 지자체·시설 홈페이지에서 수집해야 한다(최서영 트랙).
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
OUT = ROOT / "data" / "raw" / "facilities.csv"

# 두 데이터셋 중 승인된 쪽을 자동으로 쓴다. 컬럼 이름이 같아 그대로 처리된다.
ENDPOINTS = [
    ("공공체육시설 상세 정보",
     "https://apis.data.go.kr/B551014/SRVC_SFMS_FACIL_INFO/TODZ_SFMS_FACIL_INFO"),
    ("전국체육시설 정보",
     "https://apis.data.go.kr/B551014/SRVC_API_SFMS_FACI/TODZ_API_SFMS_FACI"),
]

PAGE_SIZE = 1000
MAX_PAGES = 60
CAPITAL_AREA = {"서울특별시", "경기도", "인천광역시", "서울", "경기", "인천"}

COLUMNS = [
    ("faci_nm", "시설명"),
    ("fcob_nm", "업종명"),
    ("ftype_nm", "시설유형명"),
    ("faci_road_addr", "도로명주소"),
    ("faci_lat", "위도"),
    ("faci_lot", "경도"),
    ("addr_ctpv_nm", "시도"),
    ("addr_cpb_nm", "시군구"),
    ("faci_homepage", "홈페이지"),
]


def read_key() -> str:
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("DATA_GO_KR_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return urllib.parse.unquote(key) if "%" in key else key
    sys.exit("local.properties 에 DATA_GO_KR_KEY 가 없습니다.")


def call(endpoint: str, key: str, page: int, rows: int) -> tuple[int, list[dict]]:
    query = urllib.parse.urlencode({
        "serviceKey": key, "pageNo": page, "numOfRows": rows, "resultType": "json",
    })
    req = urllib.request.Request(f"{endpoint}?{query}",
                                 headers={"User-Agent": "fitbalance/0.1"})
    with urllib.request.urlopen(req, timeout=60) as res:
        payload = json.loads(res.read().decode("utf-8"))
    body = payload.get("response", payload).get("body", {})
    items = (body.get("items") or {}).get("item") or []
    if isinstance(items, dict):
        items = [items]
    return int(body.get("totalCount") or 0), items


def pick_endpoint(key: str) -> tuple[str, str, int]:
    """승인된 데이터셋을 찾는다. 둘 다 막혀 있으면 무엇을 신청해야 하는지 알려준다."""
    for label, endpoint in ENDPOINTS:
        try:
            total, _ = call(endpoint, key, 1, 1)
            print(f"'{label}' 사용 (전체 {total:,}건)")
            return label, endpoint, total
        except urllib.error.HTTPError as e:
            print(f"  {label}: HTTP {e.code} — 활용신청이 안 된 것으로 보입니다")
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"  {label}: 통신 실패 {e}")
    sys.exit(
        "\n쓸 수 있는 시설 데이터가 없습니다. 아래 중 하나에 활용신청하세요(둘 다 자동승인).\n"
        "  https://www.data.go.kr/data/15107764/openapi.do  (공공체육시설 상세 정보)\n"
        "  https://www.data.go.kr/data/15113986/openapi.do  (전국체육시설 정보)"
    )


def main() -> None:
    only_capital = "--all" not in sys.argv
    key = read_key()
    OUT.parent.mkdir(parents=True, exist_ok=True)

    label, endpoint, total = pick_endpoint(key)
    last_page = min(MAX_PAGES, (total + PAGE_SIZE - 1) // PAGE_SIZE)

    rows: list[dict] = []
    for page in range(1, last_page + 1):
        try:
            _, got = call(endpoint, key, page, PAGE_SIZE)
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"  [건너뜀] {page}쪽: {e}")
            continue
        if not got:
            break
        rows.extend(got)
        if page % 10 == 0 or page == last_page:
            print(f"  {page}/{last_page}쪽  누적 {len(rows):,}건")
        time.sleep(0.2)

    if only_capital:
        before = len(rows)
        rows = [r for r in rows if (r.get("addr_ctpv_nm") or "") in CAPITAL_AREA]
        print(f"\n수도권만 남김: {before:,} -> {len(rows):,}건 (--all 로 전국)")

    # 폐업·삭제된 시설은 뺀다
    rows = [r for r in rows
            if (r.get("faci_stat_cd") or "00") == "00" and (r.get("del_yn") or "N") != "Y"]

    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow([ko for _, ko in COLUMNS])
        for r in rows:
            w.writerow([str(r.get(en, "")).strip() for en, _ in COLUMNS])

    with_coord = sum(1 for r in rows if r.get("faci_lat") and r.get("faci_lot"))
    print(f"\n{OUT.relative_to(ROOT)}: {len(rows):,}건 저장 (출처: {label})")
    print(f"좌표 보유: {with_coord:,}/{len(rows):,}")
    print("\n이 파일로 courses.csv 의 facility/lat/lng 와 facility_addresses.csv 를 채우세요.")
    print("요일·시작시각은 이 데이터에 없습니다. 시설 홈페이지에서 수집해야 합니다.")


if __name__ == "__main__":
    main()
