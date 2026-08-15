"""
수집한 평생학습 체육 강좌를 앱이 쓰는 data/courses.csv 로 바꾼다.

하는 일
  1. 요일을 쪼갠다            "화+목" -> 두 줄 (앱은 한 줄에 한 요일)
  2. 종목을 판정한다          강좌명에서 요가·수영·배드민턴 ... 를 찾는다
  3. 체력요인 태그를 붙인다    종목 -> 근력/근지구력/유연성/심폐/순발력
  4. 좌표를 붙인다            교육장도로명주소를 data/places.csv 와 맞춰 동 단위 좌표를 찾는다
  5. 주소표에 넣는다          data/facility_addresses.csv 에 시설명->주소를 추가한다

좌표를 왜 주소로 찾나
  이 데이터에는 위경도가 없다. 대신 도로명주소가 100% 채워져 있어서,
  이미 만들어 둔 지역 색인(places.csv, 전국 2,159곳)의 시도·시군구·동과 맞춘다.
  동 단위면 오차가 1km 안쪽이고, 추천은 3km 단위라 충분하다.

실행
    python tools/fetch_lessons.py            # 먼저 수집
    python tools/build_courses_from_lessons.py

    --keep-demo 를 주면 기존 표본 강좌를 지우지 않고 뒤에 덧붙인다.
"""

from __future__ import annotations

import csv
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "raw" / "lessons.csv"
PLACES = ROOT / "data" / "places.csv"
OUT = ROOT / "data" / "courses.csv"
ADDR = ROOT / "data" / "facility_addresses.csv"

FACTORS = ["strength", "endurance", "flex", "cardio", "power"]

# 종목 판정. 위에서부터 먼저 걸리는 것을 쓴다(파크골프가 골프보다 앞).
# 태그는 그 종목의 주된 운동 부하다. 한 강좌에 1~3개만 준다.
SPORT_RULES: list[tuple[str, str, list[str]]] = [
    ("파크골프", "파크골프", ["flex"]),
    ("아쿠아|수중", "아쿠아로빅", ["cardio", "endurance", "flex"]),
    ("수영|자유형|접영|평영|배영", "수영", ["cardio", "endurance", "flex"]),
    ("필라테스|필라", "필라테스", ["strength", "endurance", "flex"]),
    ("요가|하타|빈야사", "요가", ["flex", "endurance"]),
    ("배드민턴", "배드민턴", ["cardio", "power"]),
    ("탁구", "탁구", ["cardio", "power"]),
    ("테니스|정구", "테니스", ["cardio", "power"]),
    ("골프", "골프", ["flex", "power"]),
    ("볼링", "볼링", ["power"]),
    ("검도|태권도|합기도|주짓수|복싱|무에타이|킥복싱", "무도", ["strength", "power"]),
    ("클라이밍|암벽", "클라이밍", ["strength", "flex"]),
    ("스피닝|사이클|자전거", "스피닝", ["cardio", "endurance"]),
    ("에어로빅|줌바|라인댄스|밸리댄스|댄스", "댄스", ["cardio", "endurance"]),
    ("국학기공|기공|태극권|단전", "기공", ["flex", "endurance"]),
    ("등산|트레킹|걷기|워킹", "걷기", ["cardio", "endurance"]),
    ("헬스|웨이트|근력|코어", "웨이트", ["strength", "endurance"]),
    ("스트레칭|체조", "생활체조", ["flex", "endurance"]),
    ("축구|풋살", "축구", ["cardio", "endurance", "power"]),
    ("농구", "농구", ["cardio", "power"]),
    ("배구", "배구", ["cardio", "power"]),
    ("족구", "족구", ["cardio", "power"]),
    ("게이트볼", "게이트볼", ["flex"]),
    ("인라인|롤러", "롤러", ["cardio", "power"]),
    ("빙상|스케이트", "빙상", ["cardio", "power"]),
    ("승마", "승마", ["strength", "flex"]),
    ("카약|조정|요트", "수상", ["strength", "endurance"]),
    # 위에 안 걸리면 종목을 특정할 수 없다. 일반 체력으로 둔다.
    ("체력|운동|스포츠|체육", "생활체육", ["strength", "endurance"]),
]
SPORT_RULES = [(re.compile(p), name, tags) for p, name, tags in SPORT_RULES]

WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"]
# "화+목", "월,수,금", "매주 화요일", "화 목" 등을 모두 받는다.
WEEKDAY_RE = re.compile(r"[월화수목금토일]")

TIME_RE = re.compile(r"^(\d{1,2})[:시]?(\d{2})?")


def sport_of(name: str, content: str) -> tuple[str, list[str]] | None:
    text = f"{name} {content}"
    for pattern, sport, tags in SPORT_RULES:
        if pattern.search(text):
            return sport, tags
    return None


def weekdays_of(raw: str) -> list[str]:
    """운영요일 문자열에서 요일을 뽑는다. '매주'·'격주' 같은 말은 무시한다."""
    found = [d for d in WEEKDAY_RE.findall(raw) if d in WEEKDAYS]
    # 같은 요일이 두 번 적힌 경우가 있어 순서를 지키며 중복만 없앤다
    seen, out = set(), []
    for d in found:
        if d not in seen:
            seen.add(d)
            out.append(d)
    return out


def time_of(raw: str) -> str | None:
    """'10:00', '1000', '10시' 를 모두 HH:MM 으로."""
    raw = raw.strip()
    if not raw:
        return None
    m = TIME_RE.match(raw)
    if not m:
        return None
    hh = int(m.group(1))
    mm = int(m.group(2) or 0)
    if not (0 <= hh <= 23 and 0 <= mm <= 59):
        return None
    return f"{hh:02d}:{mm:02d}"


def load_places() -> list[tuple[str, str, str, float, float]]:
    with open(PLACES, encoding="utf-8-sig", newline="") as f:
        return [
            (r["sido"], r["sigungu"], r["dong"], float(r["lat"]), float(r["lng"]))
            for r in csv.DictReader(f)
        ]


def locate(address: str, places) -> tuple[float, float] | None:
    """도로명주소를 지역 색인과 맞춰 좌표를 찾는다. 동까지 맞으면 가장 정확하다."""
    if not address:
        return None
    # 시도 이름은 표기가 제각각이라 앞 두 글자로 비교한다(서울특별시/서울시/서울)
    best = None
    for sido, sigungu, dong, lat, lng in places:
        if not address.startswith(sido[:2]):
            continue
        if sigungu and sigungu not in address:
            continue
        if dong and dong in address:
            return lat, lng          # 동까지 맞으면 즉시 채택
        if best is None:
            best = (lat, lng)        # 시군구까지만 맞은 것은 예비로 둔다
    return best


def main() -> None:
    if not SRC.exists():
        sys.exit(f"{SRC.relative_to(ROOT)} 가 없습니다. 먼저 tools/fetch_lessons.py 를 실행하세요.")
    if not PLACES.exists():
        sys.exit("data/places.csv 가 없습니다. tools/build_places.py 를 먼저 실행하세요.")

    places = load_places()
    with open(SRC, encoding="utf-8-sig", newline="") as f:
        lessons = list(csv.DictReader(f))

    rows: list[list] = []
    addresses: dict[str, str] = {}
    dropped = {"종목 판정 실패": 0, "요일 없음": 0, "시각 없음": 0, "좌표 못 찾음": 0}
    dong_hit = 0

    for r in lessons:
        name = (r.get("강좌명") or "").strip()
        place = (r.get("교육장소") or "").strip() or (r.get("운영기관명") or "").strip()
        address = (r.get("교육장도로명주소") or "").strip()

        mapped = sport_of(name, r.get("강좌내용") or "")
        if mapped is None:
            dropped["종목 판정 실패"] += 1
            continue
        sport, tags = mapped

        days = weekdays_of(r.get("운영요일") or "")
        if not days:
            dropped["요일 없음"] += 1
            continue

        start = time_of(r.get("교육시작시각") or "")
        if start is None:
            dropped["시각 없음"] += 1
            continue

        coords = locate(address, places)
        if coords is None:
            dropped["좌표 못 찾음"] += 1
            continue
        lat, lng = coords
        if any(d and d in address for _, _, d, _, _ in places if d):
            dong_hit += 1

        if place and address:
            addresses[place] = address

        # 주 2회면 두 줄로 나눈다. 앱은 한 줄에 한 요일만 받는다.
        for day in days:
            rows.append([name, place, f"{lat:.6f}", f"{lng:.6f}", day, start, sport]
                        + [1 if f in tags else 0 for f in FACTORS])

    if not rows:
        sys.exit("변환된 강좌가 없습니다. data/raw/lessons.csv 를 확인하세요.")

    # 같은 시설·요일·시각·강좌명이 겹치면 한 번만 둔다
    seen, unique = set(), []
    for row in rows:
        key = (row[0], row[1], row[4], row[5])
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)

    keep_demo = "--keep-demo" in sys.argv
    existing: list[list] = []
    if keep_demo and OUT.exists():
        with open(OUT, encoding="utf-8-sig", newline="") as f:
            existing = [list(r.values()) for r in csv.DictReader(f)]

    header = (["course_id", "title", "facility", "lat", "lng", "weekday", "start_time", "sport"]
              + [f"tag_{f}" for f in FACTORS])
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(header)
        n = 0
        for row in existing:
            n += 1
            w.writerow([f"C{n:04d}"] + row[1:])
        for row in unique:
            n += 1
            w.writerow([f"C{n:04d}"] + row)

    # 주소표에 새 시설을 더한다. 기존 항목은 지우지 않는다.
    merged: dict[str, str] = {}
    if ADDR.exists():
        with open(ADDR, encoding="utf-8-sig", newline="") as f:
            merged = {r["facility"]: r["address"] for r in csv.DictReader(f)}
    added = sum(1 for k in addresses if k not in merged)
    merged.update(addresses)
    with open(ADDR, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["facility", "address"])
        for k in sorted(merged):
            w.writerow([k, merged[k]])

    total_out = len(existing) + len(unique)
    print(f"강좌 {len(lessons):,}건에서 {len(unique):,}줄을 만들었습니다.")
    print(f"  (주 2회 이상은 요일마다 한 줄로 나뉩니다)\n")
    print("제외된 것")
    for reason, count in sorted(dropped.items(), key=lambda kv: -kv[1]):
        if count:
            print(f"  {reason:<16} {count:>6,}")
    print(f"\n좌표 정확도: 동까지 맞은 것 {dong_hit:,} / 시군구까지만 {len(unique) - dong_hit:,}")
    print(f"{OUT.relative_to(ROOT)}            {total_out:,}줄"
          + (" (기존 표본 유지)" if keep_demo else ""))
    print(f"{ADDR.relative_to(ROOT)}  시설 주소 {added:,}곳 추가")
    print("\n다음: 서버를 다시 켜면 반영됩니다.")


if __name__ == "__main__":
    main()
