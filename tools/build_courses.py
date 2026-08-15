"""
공공체육시설 데이터로 강좌표 시드와 시설 주소표를 만든다.

실행 순서
    python tools/fetch_facilities.py    # 시설 원자료 받기
    python tools/build_courses.py       # 이 스크립트

만드는 것
    data/courses_seed.csv        최서영에게 넘길 강좌표 시드
    data/facility_addresses.csv  시설명 -> 도로명주소 (앱이 바로 쓴다)

채워지는 것과 아닌 것
  시설명·위도·경도·주소·종목은 공공데이터에서 그대로 온다.
  **요일·시작시각·강좌명은 빈칸으로 둔다.** 이 데이터에는 강좌 시간표가 없다.
  없는 값을 그럴듯하게 채우면 앱이 거짓말을 하게 되므로 비워서 넘긴다.

  체력요인 태그는 업종명에서 유도한 **잠정값**이다. 실제 강좌를 확인하면 덮어쓴다.
  (예: 체육관에서 열리는 강좌가 요가일 수도 배드민턴일 수도 있다.)

강좌가 열리지 않는 시설은 뺀다
  간이운동장·축구장·게이트볼장 같은 대관·개방 시설에는 정기 강좌가 없다.
  강습이 실제로 열리는 업종만 남긴다(아래 SPORT_MAP 에 있는 것).
"""

from __future__ import annotations

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "raw" / "facilities.csv"
COURSES_OUT = ROOT / "data" / "courses_seed.csv"
ADDR_OUT = ROOT / "data" / "facility_addresses.csv"

FACTORS = ["strength", "endurance", "flex", "cardio", "power"]

# 업종명 -> (종목, 잠정 태그)
# 태그 근거는 해당 종목의 주된 운동 부하다. 확정값이 아니라 출발점이다.
SPORT_MAP: dict[str, tuple[str, list[str]]] = {
    "체육관":                  ("생활체육", ["strength", "endurance"]),
    "생활체육관":              ("생활체육", ["strength", "endurance"]),
    "구기체육관":              ("구기", ["cardio", "power"]),
    "투기체육관":              ("무도", ["strength", "power"]),
    "수영장":                  ("수영", ["cardio", "endurance", "flex"]),
    "기타체육시설(체력단련장)": ("웨이트", ["strength", "endurance"]),
    "골프연습장":              ("골프", ["flex", "power"]),
    "인공암벽장업":            ("클라이밍", ["strength", "flex"]),
    "실내인공암벽장":          ("클라이밍", ["strength", "flex"]),
    "실외인공암벽장":          ("클라이밍", ["strength", "flex"]),
    "테니스장":                ("테니스", ["cardio", "power"]),
    "빙상장":                  ("빙상", ["cardio", "power"]),
    "롤러스케이트장":          ("롤러", ["cardio", "power"]),
    "파크골프장":              ("파크골프", ["flex"]),
    "국궁장":                  ("국궁", ["strength", "flex"]),
    "양궁장":                  ("양궁", ["strength", "flex"]),
    "씨름장":                  ("씨름", ["strength", "power"]),
}

# 업종명이 비어 있을 때 시설유형명으로 한 번 더 본다
FALLBACK_COLUMN = "시설유형명"

# 시설명에 종목이 드러나면 업종명보다 그쪽을 믿는다.
# 업종 등록이 실제와 다른 경우가 있다(예: "누림공원 농구장"이 체력단련장으로 등록).
# 순서가 중요하다. "파크골프"를 "골프"보다 먼저 본다.
NAME_SPORT = [
    ("파크골프", ("파크골프", ["flex"])),
    ("골프", ("골프", ["flex", "power"])),
    ("수영", ("수영", ["cardio", "endurance", "flex"])),
    ("테니스", ("테니스", ["cardio", "power"])),
    ("정구", ("테니스", ["cardio", "power"])),
    ("인라인", ("롤러", ["cardio", "power"])),
    ("롤러", ("롤러", ["cardio", "power"])),
    ("빙상", ("빙상", ["cardio", "power"])),
    ("아이스", ("빙상", ["cardio", "power"])),
    ("궁도", ("국궁", ["strength", "flex"])),
    ("국궁", ("국궁", ["strength", "flex"])),
    ("양궁", ("양궁", ["strength", "flex"])),
    ("씨름", ("씨름", ["strength", "power"])),
    ("클라이밍", ("클라이밍", ["strength", "flex"])),
    ("암벽", ("클라이밍", ["strength", "flex"])),
    ("농구", ("농구", ["cardio", "power"])),
    ("배드민턴", ("배드민턴", ["cardio", "power"])),
    ("탁구", ("탁구", ["cardio", "power"])),
    ("체력단련", ("웨이트", ["strength", "endurance"])),
    ("헬스", ("웨이트", ["strength", "endurance"])),
]

# 시설명이 이 종목을 가리키면 뺀다. 정기 강습이 아니라 대관·개방으로 쓰는 곳이다.
NAME_EXCLUDE = ("축구장", "풋살", "야구장", "게이트볼", "족구", "배구장", "하키")


def sport_from_name(name: str) -> tuple[str, list[str]] | None:
    if any(k in name for k in NAME_EXCLUDE):
        return None
    for keyword, mapped in NAME_SPORT:
        if keyword in name:
            return mapped
    return None

# 원자료 시설명에 공단 내부 표기가 섞여 있다. 사용자에게 보여줄 이름이 아니라 뗀다.
# 이름이 겹쳐 붙은 경우도 있어(예: "(취약시설)(취약시설)○○") 반복해서 없앤다.
NAME_NOISE = "(취약시설)"


def clean_name(name: str) -> str:
    name = name.strip()
    while name.startswith(NAME_NOISE):
        name = name[len(NAME_NOISE):].strip()
    return name


def main() -> None:
    if not SRC.exists():
        sys.exit(f"{SRC.relative_to(ROOT)} 가 없습니다. 먼저 tools/fetch_facilities.py 를 실행하세요.")

    with open(SRC, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    seen: set[str] = set()          # 시설명 중복 제거
    picked: list[dict] = []
    skipped_kind = 0
    skipped_coord = 0

    for r in rows:
        name = clean_name(r.get("시설명") or "")
        if not name:
            continue

        # 시설명이 종목을 말해 주면 그것을 따른다. 없을 때만 업종명을 본다.
        if any(k in name for k in NAME_EXCLUDE):
            skipped_kind += 1
            continue
        kind = (r.get("업종명") or "").strip()
        mapped = (
            sport_from_name(name)
            or SPORT_MAP.get(kind)
            or SPORT_MAP.get((r.get(FALLBACK_COLUMN) or "").strip())
        )
        if mapped is None:
            skipped_kind += 1
            continue

        # 소수점 6자리면 약 0.1m 정밀도라 충분하다. 원자료는 자릿수가 들쭉날쭉하다.
        try:
            lat = f"{float(r.get('위도') or ''):.6f}"
            lng = f"{float(r.get('경도') or ''):.6f}"
        except ValueError:
            skipped_coord += 1
            continue

        # 같은 시설에 업종이 여러 개 등록된 경우가 있다. 첫 건만 쓴다.
        key = f"{name}|{r.get('시군구','')}"
        if key in seen:
            continue
        seen.add(key)

        sport, tags = mapped
        picked.append({
            "시설명": name, "종목": sport, "태그": tags,
            "위도": lat, "경도": lng,
            "주소": (r.get("도로명주소") or "").strip(),
            "시도": (r.get("시도") or "").strip(),
            "시군구": (r.get("시군구") or "").strip(),
        })

    picked.sort(key=lambda x: (x["시도"], x["시군구"], x["시설명"]))

    # 강좌표 시드 — courses.csv 와 열 구성이 같다. 요일·시작시각·강좌명만 비어 있다.
    with open(COURSES_OUT, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["course_id", "title", "facility", "lat", "lng", "weekday",
                    "start_time", "sport"] + [f"tag_{x}" for x in FACTORS])
        for i, p in enumerate(picked, start=1):
            w.writerow([f"C{i:04d}", "", p["시설명"], p["위도"], p["경도"], "", "",
                        p["종목"]] + [1 if x in p["태그"] else 0 for x in FACTORS])

    # 주소표 — 기존 항목을 지우지 않고 합친다.
    # 데모 강좌가 쓰는 시설이 공공데이터에 없을 수 있으므로 덮어쓰면 안 된다.
    # 겹치는 시설은 공공데이터의 도로명주소가 더 정확하므로 그쪽을 쓴다.
    existing: dict[str, str] = {}
    if ADDR_OUT.exists():
        with open(ADDR_OUT, encoding="utf-8-sig", newline="") as f:
            existing = {r["facility"]: r["address"] for r in csv.DictReader(f)}

    upgraded = sum(1 for p in picked if p["주소"] and p["시설명"] in existing)
    merged = dict(existing)
    merged.update({p["시설명"]: p["주소"] for p in picked if p["주소"]})

    with open(ADDR_OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["facility", "address"])
        for facility in sorted(merged):
            w.writerow([facility, merged[facility]])

    with_addr = len(merged)
    print(f"원자료 {len(rows):,}건에서 강습 가능 시설 {len(picked):,}곳을 골랐습니다.")
    print(f"  강습 업종 아님으로 제외 {skipped_kind:,}  좌표 없음으로 제외 {skipped_coord:,}\n")

    by_sport: dict[str, int] = {}
    for p in picked:
        by_sport[p["종목"]] = by_sport.get(p["종목"], 0) + 1
    for sport, n in sorted(by_sport.items(), key=lambda kv: -kv[1]):
        print(f"  {n:5,}  {sport}")

    print(f"\n{COURSES_OUT.relative_to(ROOT)}      {len(picked):,}행 (요일·시작시각·강좌명 비어 있음)")
    print(f"{ADDR_OUT.relative_to(ROOT)}  {with_addr:,}행 "
          f"(기존 {len(existing):,}행 유지, 그중 {upgraded:,}행은 도로명주소로 교체)")
    print("\n최서영은 이 시드에서 수도권 야간·주말 강좌가 있는 시설을 골라")
    print("강좌명·요일·시작시각을 채우고 태그를 실제 강좌에 맞게 고치면 됩니다.")


if __name__ == "__main__":
    main()
