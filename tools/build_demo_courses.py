"""
데모용 data/courses.csv 를 실제 공공체육시설로 다시 만든다.

왜 필요한가
  기존 데모 강좌표는 시설명·좌표를 직접 지어낸 것이었다. 26곳 중 20곳은
  실재하지 않는 이름이라 지도에 찍으면 엉뚱한 자리를 가리켰다.
  이제 공공데이터로 실제 시설을 쓸 수 있으므로 교체한다.

실제 데이터인 것 / 표본인 것
    시설명·위도·경도·주소·종목   공공체육시설 상세 정보 (공공데이터포털 15107764)
    강좌명·요일·시작시각          **표본이다.** 공공데이터에 강좌 시간표가 없다.
                                  최서영이 실제 시간표로 교체한다.
  체력요인 태그는 종목에서 유도한 값이라 실제 강좌 내용에 따라 달라질 수 있다.

실행
    python tools/build_demo_courses.py
"""

from __future__ import annotations

import csv
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_courses import clean_name  # noqa: E402  시드와 같은 규칙으로 시설명을 다듬는다

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "data" / "courses_seed.csv"
ADDR = ROOT / "data" / "facility_addresses.csv"
# 시도는 원자료의 전용 컬럼에서 가져온다. 도로명주소 첫 토큰으로 뽑으면
# "강남구 ...", "충남 ..." 처럼 시도가 빠지거나 축약된 주소에서 어긋난다.
RAW = ROOT / "data" / "raw" / "facilities.csv"
OUT = ROOT / "data" / "courses.csv"

FACTORS = ["strength", "endurance", "flex", "cardio", "power"]

# 사용자는 주소 검색으로 아무 동네나 고른다. 그래서 시설이 많은 동네부터
# 골고루 깔아 두어야 어디를 골라도 추천이 비지 않는다.
# 대상은 data/places.csv (주소 검색이 쓰는 것과 같은 색인)의 상위 지역이다.
PLACES = ROOT / "data" / "places.csv"
TARGET_PLACES = 60
# 한 지역에서 이만큼 깐다.
PER_PLACE = 3
# 이보다 먼 시설은 그 지역의 강좌로 치지 않는다.
# 군 단위는 면적이 넓어 좁게 잡으면 아무것도 안 걸린다. 추천은 사용자가 정한
# 거리(기본 3km)로 다시 거르므로 여기서 넓게 깔아 두어도 해가 없다.
MAX_KM = 15.0


def distance_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """짧은 거리라 등거리 근사로 충분하다. 서버의 거리 계산과 같은 방식이다."""
    km_per_deg = 111.32
    dy = (lat1 - lat2) * km_per_deg
    dx = (lng1 - lng2) * km_per_deg * math.cos(math.radians((lat1 + lat2) / 2))
    return math.hypot(dx, dy)

# 종목별 강좌명 표본. 해당 시설 유형에서 흔히 열리는 강습 이름을 썼다.
# 시간대를 나타내는 말은 넣지 않는다. 요일·시각에서 자동으로 붙이므로
# "주말 ○○ 교실"이 금요일에 배정되는 식의 모순이 생기지 않는다.
SAMPLE_TITLES: dict[str, list[str]] = {
    "수영":     ["수영 초급반", "자유형 교정반", "수영 중급반"],
    "생활체육": ["요가교실", "필라테스", "코어 강화 교실", "생활체조 교실"],
    "테니스":   ["테니스 초급반", "테니스 강습"],
    "웨이트":   ["웨이트 트레이닝 강습", "근력 순환운동 교실"],
    "클라이밍": ["클라이밍 입문반"],
    "골프":     ["골프 레슨"],
    "빙상":     ["아이스스케이팅 강습"],
    "롤러":     ["인라인 스케이트 교실"],
    "파크골프": ["파크골프 교실"],
    "국궁":     ["국궁 입문반"],
    "양궁":     ["양궁 체험 강습"],
    "씨름":     ["씨름 교실"],
    "농구":     ["농구 교실"],
    "배드민턴": ["배드민턴 초급반", "배드민턴 강습"],
    "탁구":     ["탁구 교실"],
}


def prefix_for(weekday: str, start: str) -> str:
    """요일·시각에 맞는 수식어. 강좌명과 시간이 어긋나지 않게 한다."""
    if weekday in ("토", "일"):
        return "주말 "
    return "야간 " if start >= "18:00" else "직장인 "

# 요일·시각 표본. 퇴근 후(18:30 이후)와 주말 위주로 깐다.
SAMPLE_SLOTS = [
    ("월", "19:00"), ("화", "19:30"), ("수", "20:00"), ("목", "19:00"),
    ("금", "19:30"), ("토", "10:00"), ("토", "14:00"), ("일", "10:00"),
]

# 조명이 없는 야외 종목은 평일 밤에 강습이 열리지 않는다. 주말 낮에만 깐다.
OUTDOOR_ONLY = {"파크골프", "국궁", "양궁"}
DAYLIGHT_SLOTS = [("토", "10:00"), ("일", "10:00"), ("토", "14:00"), ("일", "14:00")]


def main() -> None:
    if not SEED.exists():
        sys.exit(f"{SEED.relative_to(ROOT)} 가 없습니다. 먼저 tools/build_courses.py 를 실행하세요.")

    if not RAW.exists():
        sys.exit(f"{RAW.relative_to(ROOT)} 가 없습니다. 먼저 tools/fetch_facilities.py 를 실행하세요.")

    with open(SEED, encoding="utf-8-sig", newline="") as f:
        seed = list(csv.DictReader(f))
    with open(ADDR, encoding="utf-8-sig", newline="") as f:
        addresses = {r["facility"]: r["address"] for r in csv.DictReader(f)}

    # 주소가 없는 시설은 뺀다. 앱에 주소 없이 뜨는 강좌가 없어야 한다.
    pool = []
    for r in seed:
        if r["sport"] not in SAMPLE_TITLES:
            continue
        if not addresses.get(r["facility"], "").strip():
            continue
        try:
            pool.append((float(r["lat"]), float(r["lng"]), r))
        except ValueError:
            continue

    if not pool:
        sys.exit("쓸 수 있는 시설이 없습니다. tools/build_courses.py 를 먼저 실행하세요.")

    if not PLACES.exists():
        sys.exit(f"{PLACES.relative_to(ROOT)} 가 없습니다. tools/build_places.py 를 먼저 실행하세요.")
    with open(PLACES, encoding="utf-8-sig", newline="") as f:
        # 시설이 많은 지역 순. 시도마다 최소 두 곳은 들어가게 섞는다.
        all_places = list(csv.DictReader(f))
    by_sido: dict[str, list[dict]] = {}
    for p in all_places:
        by_sido.setdefault(p["sido"], []).append(p)
    for v in by_sido.values():
        v.sort(key=lambda p: -int(p["facility_count"]))

    targets: list[dict] = []
    round_no = 0
    while len(targets) < TARGET_PLACES:
        added = False
        for sido in sorted(by_sido, key=lambda s: -len(by_sido[s])):
            rows = by_sido[sido]
            if round_no < len(rows) and len(targets) < TARGET_PLACES:
                targets.append(rows[round_no])
                added = True
        if not added:
            break
        round_no += 1

    picked: list[dict] = []
    taken: set[str] = set()
    thin: list[str] = []
    per_place: list[tuple[str, int]] = []

    for t in targets:
        plat, plng = float(t["lat"]), float(t["lng"])
        name = " ".join(x for x in (t["sigungu"], t["dong"]) if x)
        near = sorted(
            (
                (distance_km(lat, lng, plat, plng), r)
                for lat, lng, r in pool
                if distance_km(lat, lng, plat, plng) <= MAX_KM
            ),
            key=lambda x: x[0],
        )

        # 한 종목에 쏠리지 않게 가까운 것부터 종목을 바꿔 가며 담는다
        got: list[dict] = []
        used_sports: set[str] = set()
        for _ in range(len(SAMPLE_TITLES)):
            for dist, r in near:
                if len(got) >= PER_PLACE:
                    break
                if r["facility"] in taken or r["sport"] in used_sports:
                    continue
                got.append(r)
                taken.add(r["facility"])
                used_sports.add(r["sport"])
            if len(got) >= PER_PLACE:
                break
            used_sports.clear()

        per_place.append((name, len(got)))
        if len(got) < PER_PLACE:
            thin.append(f"{name} {len(got)}/{PER_PLACE}")
        picked.extend(got)

    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["course_id", "title", "facility", "lat", "lng", "weekday",
                    "start_time", "sport"] + [f"tag_{x}" for x in FACTORS])
        title_seq: dict[str, int] = {}
        outdoor_seq = 0
        for i, r in enumerate(picked, start=1):
            sport = r["sport"]
            titles = SAMPLE_TITLES[sport]
            n = title_seq.get(sport, 0)
            title_seq[sport] = n + 1
            # course_id는 4자리로 둔다. 전국을 담으면 100줄을 넘는다.
            if sport in OUTDOOR_ONLY:
                weekday, start = DAYLIGHT_SLOTS[outdoor_seq % len(DAYLIGHT_SLOTS)]
                outdoor_seq += 1
            else:
                weekday, start = SAMPLE_SLOTS[(i - 1) % len(SAMPLE_SLOTS)]
            title = prefix_for(weekday, start) + titles[n % len(titles)]
            w.writerow([f"C{i:04d}", title, r["facility"],
                        r["lat"], r["lng"], weekday, start, sport]
                       + [r[f"tag_{x}"] for x in FACTORS])

    print(f"주소가 확인된 강습 시설 {len(pool):,}곳 중 {len(picked)}곳으로 데모 강좌표를 만들었습니다.")
    print(f"지역 {len(targets)}곳 주변에서 각 {PER_PLACE}곳씩 뽑았습니다.\n")

    line = []
    for name, n in per_place:
        line.append(f"{name} {n}")
        if len(line) == 4:
            print("  " + "   ".join(line))
            line = []
    if line:
        print("  " + "   ".join(line))

    if thin:
        print(f"\n주변에 강습 시설이 적은 지역: {', '.join(thin)}")
    print(f"\n{OUT.relative_to(ROOT)} 갱신 완료")
    print("시설명·좌표·주소·종목은 실제 공공데이터입니다.")
    print("강좌명·요일·시작시각은 표본이며 최서영이 실제 시간표로 교체합니다.")


if __name__ == "__main__":
    main()
