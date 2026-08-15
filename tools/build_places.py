"""
전국 지역 색인(data/places.csv)을 만든다. 주소 입력창의 검색 대상이다.

왜 필요한가
  집·직장 위치를 가로 스크롤 칩으로 고르게 하면 전국을 담을 수 없다.
  주소를 입력받아야 하는데, 주소를 좌표로 바꾸려면 지오코딩이 필요하다.
  카카오 로컬 API를 쓰면 되지만 REST 키가 있어야 하고, 팀원 PC마다 키를 넣게
  할 수는 없다. 그래서 **우리가 이미 가진 공공체육시설 주소**로 지역 색인을 만든다.

  시설 34,727곳의 도로명주소에는 괄호 안에 법정동이 들어 있다.
  (예: "서울특별시 마포구 월드컵로25길 190 (망원동)")
  이걸 모아 시도·시군구·동별로 묶고 좌표는 그 동네 시설들의 중심점을 쓴다.
  아파트 동·호수까지는 못 찾지만, 동선 추천은 3km 단위라 동 수준이면 충분하다.

실행
    python tools/fetch_facilities.py --all
    python tools/build_places.py

결과
    data/places.csv   sido, sigungu, dong, lat, lng, facility_count
"""

from __future__ import annotations

import csv
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "raw" / "facilities.csv"
OUT = ROOT / "data" / "places.csv"

# 도로명주소 괄호 안의 법정동. "(망원동)" 또는 "(신당동, 남산타운)" 형태다.
DONG_RE = re.compile(r"\(([^)]+)\)")

# 이 개수보다 시설이 적은 동네는 좌표가 한쪽으로 치우칠 수 있어 뺀다.
MIN_FACILITIES = 1


def dong_of(address: str) -> str:
    """괄호 안 첫 조각을 법정동으로 본다. 없으면 빈 문자열."""
    m = DONG_RE.search(address)
    if not m:
        return ""
    first = m.group(1).split(",")[0].strip()
    # "1163-4" 같은 지번이 들어오는 경우가 있다. 동/읍/면/가로 끝나는 것만 쓴다.
    return first if first.endswith(("동", "읍", "면", "가", "리")) else ""


def main() -> None:
    if not SRC.exists():
        sys.exit(
            f"{SRC.relative_to(ROOT)} 가 없습니다.\n"
            "먼저 python tools/fetch_facilities.py --all 을 실행하세요."
        )

    with open(SRC, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    # (시도, 시군구, 동) -> 좌표 목록
    buckets: dict[tuple[str, str, str], list[tuple[float, float]]] = defaultdict(list)
    no_dong = 0
    for r in rows:
        sido = (r.get("시도") or "").strip()
        sigungu = (r.get("시군구") or "").strip()
        if not sido or not sigungu:
            continue
        try:
            lat, lng = float(r["위도"]), float(r["경도"])
        except (ValueError, KeyError):
            continue

        dong = dong_of(r.get("도로명주소") or "")
        if not dong:
            no_dong += 1
        buckets[(sido, sigungu, dong)].append((lat, lng))

    entries = []
    for (sido, sigungu, dong), coords in buckets.items():
        if len(coords) < MIN_FACILITIES:
            continue
        lat = sum(c[0] for c in coords) / len(coords)
        lng = sum(c[1] for c in coords) / len(coords)
        entries.append((sido, sigungu, dong, round(lat, 6), round(lng, 6), len(coords)))

    # 시설이 많은 곳을 앞에 둔다. 검색 결과 순서가 그대로 유용해진다.
    entries.sort(key=lambda e: (e[0], e[1], -e[5], e[2]))

    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["sido", "sigungu", "dong", "lat", "lng", "facility_count"])
        w.writerows(entries)

    with_dong = sum(1 for e in entries if e[2])
    sidos = len({e[0] for e in entries})
    print(f"시설 {len(rows):,}건에서 지역 {len(entries):,}곳을 만들었습니다.")
    print(f"  시도 {sidos}개 · 동까지 있는 곳 {with_dong:,} · 시군구까지만 {len(entries) - with_dong:,}")
    print(f"  (괄호 안 법정동을 못 찾은 시설 {no_dong:,}건은 시군구 대표 좌표로 묶었습니다)")
    print(f"\n{OUT.relative_to(ROOT)} 생성 완료")


if __name__ == "__main__":
    main()
