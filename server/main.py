"""직장인 신체 불균형 진단 + 공공 체육 강좌 추천 백엔드.

contracts/design.md 명세를 구현한다. 기동 시 data/*.csv를 SQLite로 적재한다.
"""
from __future__ import annotations

import csv
import math
import sqlite3
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Literal

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
DB_PATH = DATA_DIR / "app.db"
KST = timezone(timedelta(hours=9))

# ---------------------------------------------------------------- 도메인 상수

# 측정 항목 정의: 코드 -> (한글명, 단위, 입력 허용 최소, 최대)
#
# 국민체력100 '성인기(만 19~64세)' 인증 측정항목을 따른다.
# 평형성(눈감고외발서기)은 어르신기 항목이라 여기서 쓰지 않는다. 대신 순발력을 둔다.
# 출처: https://nfa.kspo.or.kr/reserve/0/selectMeasureGradeItemListByAgeSe.kspo
ITEM_META = {
    "grip": ("상대악력", "%", 10.0, 150.0),
    "sit_up": ("교차윗몸일으키기", "회", 0.0, 100.0),
    "sit_reach": ("앉아윗몸앞으로굽히기", "cm", -30.0, 40.0),
    "shuttle_run": ("왕복오래달리기", "회", 0.0, 120.0),
    "standing_jump": ("제자리멀리뛰기", "cm", 30.0, 350.0),
}

# 체력요인도 공단 분류를 따른다. 교차윗몸일으키기는 근력이 아니라 근지구력이다.
FACTOR_LABEL = {
    "strength": "근력",
    "endurance": "근지구력",
    "flex": "유연성",
    "cardio": "심폐지구력",
    "power": "순발력",
}
FACTOR_ORDER = ["strength", "endurance", "flex", "cardio", "power"]

# 측정 항목 -> 체력요인
ITEM_TO_FACTOR = {
    "grip": "strength",
    "sit_up": "endurance",
    "sit_reach": "flex",
    "shuttle_run": "cardio",
    "standing_jump": "power",
}

# 행정구역 통합으로 시도명에서 사라진 이름을 검색으로 살린다.
# 광주광역시는 전남광주통합특별시에 흡수돼 시군구 목록에 '광주'가 없다.
# 그대로 두면 "광주"를 친 사용자에게 순천·여수나 경기도 광주시가 나온다.
_GWANGJU_GU = ["동구", "서구", "남구", "북구", "광산구"]
PLACE_ALIAS: dict[str, dict] = {
    "광주": {"sido": "전남광주", "sigungu": _GWANGJU_GU},
    "광주광역시": {"sido": "전남광주", "sigungu": _GWANGJU_GU},
}

# 지역 목록을 낼 때 쓰는 시도 순서. 인구가 많은 곳을 앞에 둔다.
# 검색어 없이 열었을 때 보여 줄 순서이므로 사용자가 찾을 확률이 높은 쪽이 위여야 한다.
# 광주·전남은 전남광주통합특별시로 하나다.
SIDO_ORDER = [
    "서울", "경기", "부산", "인천", "대구", "대전", "전남광주", "울산",
    "충남", "충북", "전북", "경남", "경북", "강원", "제주", "세종",
]

# 집에서 잴 수 없는 항목과 그 이유. 앱이 "왜 센터에 가야 하는지"를 이 문구로 설명한다.
CENTER_ONLY_ITEMS = ["grip", "shuttle_run"]
CENTER_ONLY_REASON = {
    "grip": "악력계가 있어야 합니다",
    "shuttle_run": "20m 직선 구간과 신호음이 있어야 합니다",
}

# 약점 요인 조합(2개) -> 불균형 유형명과 설명. 5개 요인이므로 10가지 조합이 나온다.
IMBALANCE_TYPES = {
    frozenset(["strength", "endurance"]): (
        "코어 약화형",
        "근력과 근지구력이 함께 낮아 몸통을 버티는 힘이 약합니다. 오래 앉아 있으면 허리 부담이 커지기 쉽습니다.",
    ),
    frozenset(["strength", "flex"]): (
        "근력·유연성 동반 저하형",
        "근력과 유연성이 함께 낮아 부상 위험이 큽니다. 저강도 근력 운동부터 시작하는 것이 좋습니다.",
    ),
    frozenset(["strength", "cardio"]): (
        "체력 저하 번아웃형",
        "근력과 심폐지구력이 모두 낮습니다. 만성 피로로 이어지기 쉬운 상태입니다.",
    ),
    frozenset(["strength", "power"]): (
        "근파워 저하형",
        "힘을 내는 능력과 순간적으로 폭발시키는 능력이 함께 낮습니다. 계단이나 급한 걸음에서 먼저 느껴집니다.",
    ),
    frozenset(["endurance", "flex"]): (
        "유연성 저하 좌식형",
        "몸통 지구력과 유연성이 뒤처져, 장시간 앉은 자세로 인한 전형적 불균형 패턴입니다.",
    ),
    frozenset(["endurance", "cardio"]): (
        "지구력 부족형",
        "근지구력과 심폐지구력이 모두 낮아 활동을 오래 이어가기 어렵습니다.",
    ),
    frozenset(["endurance", "power"]): (
        "하체 기능 저하형",
        "버티는 힘과 튀어 오르는 힘이 함께 낮습니다. 앉아 있는 시간이 길수록 두드러집니다.",
    ),
    frozenset(["flex", "cardio"]): (
        "경직 저활동형",
        "몸이 굳어 있고 심폐지구력도 낮아, 활동량 자체가 부족한 상태입니다.",
    ),
    frozenset(["flex", "power"]): (
        "경직 둔화형",
        "관절 가동 범위가 좁고 순발력도 낮습니다. 갑작스러운 움직임에서 다치기 쉽습니다.",
    ),
    frozenset(["cardio", "power"]): (
        "활동량 부족형",
        "심폐지구력과 순발력이 낮습니다. 평소 움직임의 강도가 전반적으로 낮은 상태입니다.",
    ),
}

WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"]

# ---------------------------------------------------------------- DB 적재


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def load_csv_to_sqlite() -> tuple[int, int]:
    """data/*.csv를 SQLite로 적재한다. 파일 교체 후 재기동만으로 반영된다."""
    DATA_DIR.mkdir(exist_ok=True)
    conn = _connect()
    cur = conn.cursor()
    cur.executescript(
        """
        DROP TABLE IF EXISTS norms;
        DROP TABLE IF EXISTS courses;
        CREATE TABLE norms (
            gender TEXT, age_band TEXT, item TEXT, percentile REAL, value REAL
        );
        CREATE TABLE courses (
            course_id TEXT PRIMARY KEY, title TEXT, facility TEXT,
            lat REAL, lng REAL, weekday TEXT, start_time TEXT, sport TEXT,
            tag_strength INTEGER, tag_endurance INTEGER, tag_flex INTEGER,
            tag_cardio INTEGER, tag_power INTEGER
        );
        DROP TABLE IF EXISTS centers;
        CREATE TABLE centers (
            center_code TEXT PRIMARY KEY, sido TEXT, sigungu TEXT,
            center_name TEXT, address TEXT, tel TEXT
        );
        DROP TABLE IF EXISTS facility_addresses;
        CREATE TABLE facility_addresses (
            facility TEXT PRIMARY KEY, address TEXT
        );
        DROP TABLE IF EXISTS places;
        CREATE TABLE places (
            sido TEXT, sigungu TEXT, dong TEXT, lat REAL, lng REAL, facility_count INTEGER
        );
        DROP TABLE IF EXISTS facilities;
        CREATE TABLE facilities (
            facility TEXT PRIMARY KEY, lat REAL, lng REAL, sport TEXT,
            tag_strength INTEGER, tag_endurance INTEGER, tag_flex INTEGER,
            tag_cardio INTEGER, tag_power INTEGER
        );
        CREATE TABLE IF NOT EXISTS diagnoses (
            diagnosis_id TEXT PRIMARY KEY, device_id TEXT, measured_at TEXT,
            gender TEXT, age_band TEXT, payload TEXT, profile TEXT DEFAULT 'improve'
        );
        CREATE TABLE IF NOT EXISTS match_fail_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT, logged_at TEXT,
            weak_factors TEXT, work_lat REAL, work_lng REAL, leave_time TEXT
        );
        """
    )

    # diagnoses 는 사용자 기록이라 지우지 않는다. 이전 버전 DB에는 profile 열이 없다.
    if "profile" not in {c[1] for c in cur.execute("PRAGMA table_info(diagnoses)")}:
        cur.execute("ALTER TABLE diagnoses ADD COLUMN profile TEXT DEFAULT 'improve'")

    norms_path = DATA_DIR / "norms.csv"
    courses_path = DATA_DIR / "courses.csv"
    if not norms_path.exists() or not courses_path.exists():
        raise RuntimeError("data/norms.csv 또는 data/courses.csv가 없습니다. data/_make_sample.py를 먼저 실행하세요.")

    with open(norms_path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        expected = ["gender", "age_band", "item", "percentile", "value"]
        if reader.fieldnames != expected:
            raise RuntimeError(f"norms.csv 열 구성이 계약과 다릅니다: {reader.fieldnames} (기대: {expected})")
        norms_rows = [
            (r["gender"], r["age_band"], r["item"], float(r["percentile"]), float(r["value"]))
            for r in reader
        ]
    cur.executemany("INSERT INTO norms VALUES (?,?,?,?,?)", norms_rows)

    with open(courses_path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        expected = [
            "course_id", "title", "facility", "lat", "lng", "weekday", "start_time",
            "sport", "tag_strength", "tag_endurance", "tag_flex", "tag_cardio", "tag_power",
        ]
        if reader.fieldnames != expected:
            raise RuntimeError(f"courses.csv 열 구성이 계약과 다릅니다: {reader.fieldnames} (기대: {expected})")
        course_rows = [
            (
                r["course_id"], r["title"], r["facility"], float(r["lat"]), float(r["lng"]),
                r["weekday"], r["start_time"], r["sport"],
                int(r["tag_strength"]), int(r["tag_endurance"]), int(r["tag_flex"]),
                int(r["tag_cardio"]), int(r["tag_power"]),
            )
            for r in reader
        ]

    # 좌표가 남한 밖이면 적재를 거부한다.
    # 예전에 지역 색인에 0,0 과 서해 좌표가 섞여 강좌가 바다에 찍힌 적이 있다.
    # 지도에 띄우기 전까지 아무도 모르기 때문에, 여기서 잡고 멈춘다.
    outside = [
        (c[0], c[3], c[4]) for c in course_rows
        if not (33.0 <= c[3] <= 38.7 and 125.5 <= c[4] <= 131.0)
    ]
    if outside:
        sample = ", ".join(f"{cid}({lat},{lng})" for cid, lat, lng in outside[:5])
        raise RuntimeError(
            f"courses.csv 의 좌표 {len(outside)}건이 남한 범위 밖입니다: {sample}"
        )

    cur.executemany("INSERT INTO courses VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", course_rows)

    # 시설 주소 참조표. courses.csv 스키마가 계약으로 고정돼 있어 별도 파일로 둔다.
    # 없거나 일부 시설이 빠져 있어도 동작해야 한다(그 시설은 주소 없이 표시).
    address_path = DATA_DIR / "facility_addresses.csv"
    if address_path.exists():
        with open(address_path, encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            expected = ["facility", "address"]
            if reader.fieldnames != expected:
                raise RuntimeError(
                    f"facility_addresses.csv 열 구성이 다릅니다: {reader.fieldnames} (기대: {expected})"
                )
            address_rows = [
                (r["facility"], r["address"].strip())
                for r in reader
                if r["address"].strip()
            ]
        cur.executemany("INSERT OR REPLACE INTO facility_addresses VALUES (?,?)", address_rows)
        missing = {c[2] for c in course_rows} - {a[0] for a in address_rows}
        if missing:
            print(f"[주소 없음] {len(missing)}개 시설: {', '.join(sorted(missing))}")

    # 지역 색인. 주소 입력창의 검색 대상이다. 없으면 검색이 빈 결과를 준다.
    places_path = DATA_DIR / "places.csv"
    if places_path.exists():
        with open(places_path, encoding="utf-8-sig", newline="") as f:
            place_rows = [
                (r["sido"], r["sigungu"], r["dong"], float(r["lat"]), float(r["lng"]),
                 int(r["facility_count"]))
                for r in csv.DictReader(f)
            ]
        cur.executemany("INSERT INTO places VALUES (?,?,?,?,?,?)", place_rows)

    # 공공체육시설 목록(공공데이터포털 15107764에서 받아 만든 시드).
    # 강좌 시간표가 없는 시설도 담는다. "강좌 말고 그냥 이용할 곳"을 안내하는 데 쓴다.
    seed_path = DATA_DIR / "courses_seed.csv"
    if seed_path.exists():
        with open(seed_path, encoding="utf-8-sig", newline="") as f:
            facility_rows = []
            for r in csv.DictReader(f):
                try:
                    lat, lng = float(r["lat"]), float(r["lng"])
                except (ValueError, KeyError):
                    continue
                facility_rows.append((
                    r["facility"], lat, lng, r["sport"],
                    int(r["tag_strength"]), int(r["tag_endurance"]), int(r["tag_flex"]),
                    int(r["tag_cardio"]), int(r["tag_power"]),
                ))
        cur.executemany("INSERT OR REPLACE INTO facilities VALUES (?,?,?,?,?,?,?,?,?)", facility_rows)

    # 체력인증센터 목록. 없어도 서버는 뜬다(그 화면만 비어 보인다).
    centers_path = DATA_DIR / "centers.csv"
    if centers_path.exists():
        with open(centers_path, encoding="utf-8-sig", newline="") as f:
            reader = csv.DictReader(f)
            expected = ["center_code", "sido", "sigungu", "center_name", "address", "tel"]
            if reader.fieldnames != expected:
                raise RuntimeError(
                    f"centers.csv 열 구성이 다릅니다: {reader.fieldnames} (기대: {expected})"
                )
            center_rows = [
                (r["center_code"], r["sido"], r["sigungu"],
                 r["center_name"], r["address"], r["tel"])
                for r in reader
            ]
        cur.executemany("INSERT OR REPLACE INTO centers VALUES (?,?,?,?,?,?)", center_rows)

    conn.commit()
    conn.close()
    return len(norms_rows), len(course_rows)


# ---------------------------------------------------------------- 계산 로직


# 국민체력100 인증기준은 5세 단위로 나뉜다. 10세로 묶으면 기준이 뭉개진다.
AGE_BANDS = [
    (19, 24, "19-24"), (25, 29, "25-29"), (30, 34, "30-34"), (35, 39, "35-39"),
    (40, 44, "40-44"), (45, 49, "45-49"), (50, 54, "50-54"), (55, 59, "55-59"),
    (60, 64, "60-64"),
]


def age_to_band(age: int) -> str:
    for low, high, name in AGE_BANDS:
        if low <= age <= high:
            return name
    # 성인기 범위 밖은 가장 가까운 구간으로 붙인다(입력 검증에서 이미 19~64로 막혀 있다).
    return AGE_BANDS[0][2] if age < 19 else AGE_BANDS[-1][2]


def band_label(band: str) -> str:
    """화면에 보여줄 나이대 표기. '30-34' -> '30~34세'"""
    return band.replace("-", "~") + "세"


def percentile_of(conn: sqlite3.Connection, gender: str, band: str, item: str, value: float) -> float:
    """기준표의 (percentile, value) 점들 사이에서 선형 보간해 백분위를 구한다."""
    rows = conn.execute(
        "SELECT percentile, value FROM norms WHERE gender=? AND age_band=? AND item=? ORDER BY percentile",
        (gender, band, item),
    ).fetchall()
    if not rows:
        raise HTTPException(status_code=500, detail=f"기준표에 {gender}/{band}/{item} 데이터가 없습니다")

    points = [(float(r["value"]), float(r["percentile"])) for r in rows]
    points.sort()  # 값이 클수록 우수한 항목 기준

    # 기준표는 10·25·50·75·90 백분위 다섯 점뿐이다. 그 바깥을 곧장 1 또는 99로
    # 잘라 버리면 경계에서 순위가 절벽처럼 튄다(p10 값에서 조금만 낮아도 90등 -> 99등).
    # 인구의 20%가 이 바깥에 있으므로, 가장 바깥 구간의 기울기를 그대로 이어 붙인다.
    def extend(near, far, value: float) -> float:
        v0, p0 = near
        v1, p1 = far
        if v1 == v0:
            return p0
        p = p0 + (value - v0) * (p1 - p0) / (v1 - v0)
        return round(min(99.0, max(1.0, p)), 1)

    if value <= points[0][0]:
        return extend(points[0], points[1], value)
    if value >= points[-1][0]:
        return extend(points[-1], points[-2], value)
    for (v0, p0), (v1, p1) in zip(points, points[1:]):
        if v0 <= value <= v1:
            if v1 == v0:
                return p1
            ratio = (value - v0) / (v1 - v0)
            return round(p0 + ratio * (p1 - p0), 1)
    return 50.0


def grade_of(percentile: float) -> str:
    if percentile >= 80:
        return "우수"
    if percentile >= 60:
        return "양호"
    if percentile >= 40:
        return "보통"
    if percentile >= 20:
        return "미흡"
    return "노력 필요"


def bmi_category(bmi: float) -> tuple[str, bool]:
    if bmi < 18.5:
        return "저체중", False
    if bmi < 23.0:
        return "정상", True
    if bmi < 25.0:
        return "과체중", False
    return "비만", False


def haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def distance_to_route_km(
    lat: float, lng: float, wlat: float, wlng: float, hlat: float, hlng: float
) -> float:
    """직장→집 직선(퇴근 동선)과 강좌 좌표 사이의 최단거리."""
    # 위경도를 km 평면으로 근사 (서울 위도 기준)
    def to_xy(la: float, ln: float) -> tuple[float, float]:
        return ln * 111.0 * math.cos(math.radians(37.5)), la * 111.0

    px, py = to_xy(lat, lng)
    ax, ay = to_xy(wlat, wlng)
    bx, by = to_xy(hlat, hlng)
    dx, dy = bx - ax, by - ay
    seg_len2 = dx * dx + dy * dy
    if seg_len2 == 0:
        return haversine_km(lat, lng, wlat, wlng)
    t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / seg_len2))
    cx, cy = ax + t * dx, ay + t * dy
    return math.hypot(px - cx, py - cy)


def cosine(a: list[float], b: list[float]) -> float:
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return 0.0
    return sum(x * y for x, y in zip(a, b)) / (na * nb)


def parse_hhmm(text: str) -> int:
    """HH:MM을 분 단위 정수로 변환한다."""
    try:
        hh, mm = text.split(":")
        h, m = int(hh), int(mm)
        if not (0 <= h <= 23 and 0 <= m <= 59):
            raise ValueError
        return h * 60 + m
    except Exception:
        raise HTTPException(status_code=422, detail=f"시각 형식이 잘못되었습니다: {text} (HH:MM)")


def score_items(
    conn: sqlite3.Connection, gender: str, band: str, raw_values: dict[str, float]
) -> tuple[list[dict], dict[str, float]]:
    """측정값을 기준표와 대조해 항목별 점수와 요인별 백분위를 낸다.

    정밀 진단(5항목)과 집 측정(3항목)이 같은 계산을 쓰도록 떼어 놓았다.
    """
    items = []
    factor_pct = {}
    for item, value in raw_values.items():
        label, unit, lo, hi = ITEM_META[item]
        if not (lo <= value <= hi):
            raise HTTPException(
                status_code=422,
                detail=f"{label} 값이 허용 범위({lo}~{hi}{unit})를 벗어났습니다: {value}",
            )
        p = percentile_of(conn, gender, band, item, value)
        # 항목 하나가 요인 하나에 대응한다(공단 분류와 동일).
        factor_pct[ITEM_TO_FACTOR[item]] = p
        items.append({
            "item": item, "label": label, "value": value, "unit": unit,
            "percentile": round(p), "grade": grade_of(p),
        })
    return items, factor_pct


# 모든 요인이 이 백분위 이상이면 보완이 아니라 유지가 목표다.
# grade_of 에서 "양호"가 시작되는 지점과 같은 값을 쓴다.
MAINTAIN_THRESHOLD = 60.0


def profile_of(factor_pct: dict[str, float]) -> str:
    """
    추천의 목표를 정한다.

    'improve'  약한 요인을 끌어올리는 강좌를 우선한다
    'maintain' 이미 다 양호하다. 가장 낮은 요인도 또래 상위권이므로 그것을
               '약점'이라 부르면 사실이 아니다. 지금 수준을 이어 갈 곳을 찾는다.
    """
    return "maintain" if all(v >= MAINTAIN_THRESHOLD for v in factor_pct.values()) else "improve"


def imbalance_of(weak: list[str], factor_pct: dict[str, float]) -> tuple[str, str]:
    if all(v >= 60 for v in factor_pct.values()):
        # 집 측정은 3개 요인만 잰다. 잰 개수를 그대로 말한다.
        n = len(factor_pct)
        return "균형 양호형", f"측정한 {n}가지 체력요인이 모두 또래 상위권입니다. 현재 활동량을 유지하세요."
    found = IMBALANCE_TYPES.get(frozenset(weak))
    if found:
        return found
    label = FACTOR_LABEL[weak[0]]
    return f"{label} 편중 저하형", f"{label}이(가) 다른 요인에 비해 특히 뒤처져 있습니다."


# ---------------------------------------------------------------- 스키마


class DiagnoseRequest(BaseModel):
    device_id: str
    gender: Literal["M", "F"]
    age: int = Field(ge=19, le=64)
    height_cm: float = Field(ge=100, le=250)
    weight_kg: float = Field(ge=30, le=200)
    grip_kg: float = Field(ge=5, le=120)
    sit_up: int = Field(ge=0, le=100)
    sit_reach_cm: float = Field(ge=-30, le=40)
    shuttle_run: int = Field(ge=0, le=120)
    standing_jump_cm: float = Field(ge=30, le=350)


class HomeDiagnoseRequest(BaseModel):
    """
    집에서 직접 잴 수 있는 항목만 받는다.

    공단 성인기 측정항목 5개 중 3개다. 나머지 둘은 집에서 잴 방법이 없다.
      - 상대악력       : 악력계가 있어야 한다
      - 왕복오래달리기 : 20m 직선 구간과 신호음이 있어야 한다
    이 둘은 체력인증센터에서 무료로 잰다. 추정치로 메우지 않고 미측정으로 남긴다.
    """

    device_id: str
    gender: Literal["M", "F"]
    age: int = Field(ge=19, le=64)
    height_cm: float = Field(ge=100, le=250)
    weight_kg: float = Field(ge=30, le=200)
    sit_up: int = Field(ge=0, le=100)
    sit_reach_cm: float = Field(ge=-30, le=40)
    standing_jump_cm: float = Field(ge=30, le=350)


class SelfCheckRequest(BaseModel):
    """도구 없이 답하는 간편 자가진단. 각 문항은 0~3점이며 클수록 좋다."""

    device_id: str
    gender: Literal["M", "F"]
    age: int = Field(ge=19, le=64)
    strength: int = Field(ge=0, le=3)
    endurance: int = Field(ge=0, le=3)
    flex: int = Field(ge=0, le=3)
    cardio: int = Field(ge=0, le=3)
    power: int = Field(ge=0, le=3)
    activity: int = Field(ge=0, le=3)


class RecommendRequest(BaseModel):
    device_id: str
    diagnosis_id: str | None = None
    weak_factors: list[str] | None = None
    work_lat: float
    work_lng: float
    home_lat: float
    home_lng: float
    leave_time: str = "18:30"
    max_distance_km: float = Field(default=2.0, gt=0, le=50)
    limit: int = Field(default=10, ge=1, le=50)


# ---------------------------------------------------------------- 앱


@asynccontextmanager
async def lifespan(app: FastAPI):
    n, c = load_csv_to_sqlite()
    app.state.norms_rows = n
    app.state.courses_rows = c
    print(f"[기동] 기준표 {n}행, 강좌 {c}행 적재 완료")
    yield


app = FastAPI(title="FitBalance API", version="0.1.0", lifespan=lifespan)


@app.get("/demo")
def demo_page():
    """개발용 브라우저 미리보기. 앱과 같은 API를 호출한다(안드로이드 빌드 대체물 아님)."""
    return FileResponse(Path(__file__).parent / "static" / "demo.html")


@app.get("/api/v1/health")
def health():
    return {
        "status": "ok",
        "norms_rows": getattr(app.state, "norms_rows", 0),
        "courses_rows": getattr(app.state, "courses_rows", 0),
    }


@app.post("/api/v1/diagnose")
def diagnose(req: DiagnoseRequest):
    conn = _connect()
    try:
        band = age_to_band(req.age)
        height_m = req.height_cm / 100.0
        bmi = round(req.weight_kg / (height_m * height_m), 1)
        relative_grip = round(req.grip_kg / req.weight_kg * 100.0, 1)

        raw_values = {
            "grip": relative_grip,
            "sit_up": float(req.sit_up),
            "sit_reach": req.sit_reach_cm,
            "shuttle_run": float(req.shuttle_run),
            "standing_jump": req.standing_jump_cm,
        }

        items, factor_pct = score_items(conn, req.gender, band, raw_values)
        factors = [
            {
                "factor": f, "label": FACTOR_LABEL[f],
                "percentile": round(factor_pct[f]), "grade": grade_of(factor_pct[f]),
            }
            for f in FACTOR_ORDER
        ]
        weak = [f for f, _ in sorted(factor_pct.items(), key=lambda kv: kv[1])[:2]]
        type_name, type_desc = imbalance_of(weak, factor_pct)
        cat, in_range = bmi_category(bmi)
        profile = profile_of(factor_pct)

        now = datetime.now(KST)
        diagnosis_id = f"d_{now:%Y%m%d}_{uuid.uuid4().hex[:8]}"
        result = {
            "diagnosis_id": diagnosis_id,
            "measured_at": now.isoformat(timespec="seconds"),
            "age_band": band,
            "age_band_label": band_label(band),
            "gender": req.gender,
            "estimated": False,
            "profile": profile,
            "total_score": round(sum(factor_pct.values()) / len(FACTOR_ORDER)),
            "imbalance_type": type_name,
            "imbalance_desc": type_desc,
            "factors": factors,
            "weak_factors": weak,
            "items": items,
            "bmi": {"value": bmi, "category": cat, "in_normal_range": in_range},
        }

        conn.execute(
            "INSERT INTO diagnoses VALUES (?,?,?,?,?,?,?)",
            (diagnosis_id, req.device_id, now.isoformat(), req.gender, band,
             ",".join(weak), profile),
        )
        conn.commit()
        return result
    finally:
        conn.close()


@app.post("/api/v1/diagnose/home")
def diagnose_home(req: HomeDiagnoseRequest):
    """
    집에서 잰 3항목으로 진단한다.

    정밀 진단과 계산 방식이 같다. 국민체력100 기준표와 실제로 대조하므로
    추정치가 아니다(estimated=False). 다만 근력·심폐지구력은 잴 수 없어
    미측정으로 남고, 그 자리가 센터 방문의 이유가 된다.
    """
    conn = _connect()
    try:
        band = age_to_band(req.age)
        height_m = req.height_cm / 100.0
        bmi = round(req.weight_kg / (height_m * height_m), 1)

        raw_values = {
            "sit_up": float(req.sit_up),
            "sit_reach": req.sit_reach_cm,
            "standing_jump": req.standing_jump_cm,
        }
        items, factor_pct = score_items(conn, req.gender, band, raw_values)

        # 잰 요인만 담는다. 미측정 요인을 0으로 채우면 약점이 왜곡된다.
        measured = [f for f in FACTOR_ORDER if f in factor_pct]
        factors = [
            {
                "factor": f, "label": FACTOR_LABEL[f],
                "percentile": round(factor_pct[f]), "grade": grade_of(factor_pct[f]),
            }
            for f in measured
        ]
        weak = [f for f, _ in sorted(factor_pct.items(), key=lambda kv: kv[1])[:2]]
        type_name, type_desc = imbalance_of(weak, factor_pct)
        cat, in_range = bmi_category(bmi)
        profile = profile_of(factor_pct)

        unmeasured = [
            {
                "factor": ITEM_TO_FACTOR[item],
                "label": FACTOR_LABEL[ITEM_TO_FACTOR[item]],
                "item": ITEM_META[item][0],
                "reason": CENTER_ONLY_REASON[item],
            }
            for item in CENTER_ONLY_ITEMS
        ]
        missing = "·".join(u["label"] for u in unmeasured)

        now = datetime.now(KST)
        diagnosis_id = f"h_{now:%Y%m%d}_{uuid.uuid4().hex[:8]}"
        result = {
            "diagnosis_id": diagnosis_id,
            "measured_at": now.isoformat(timespec="seconds"),
            "age_band": band,
            "age_band_label": band_label(band),
            "gender": req.gender,
            "estimated": False,
            "profile": profile,
            "notice": f"집에서 잴 수 있는 3가지로 진단했습니다. "
                      f"{missing}은 체력인증센터에서 무료로 잴 수 있습니다.",
            "total_score": round(sum(factor_pct.values()) / len(factor_pct)),
            "imbalance_type": type_name,
            "imbalance_desc": type_desc,
            "factors": factors,
            "weak_factors": weak,
            "unmeasured_factors": unmeasured,
            "items": items,
            "bmi": {"value": bmi, "category": cat, "in_normal_range": in_range},
        }

        conn.execute(
            "INSERT INTO diagnoses VALUES (?,?,?,?,?,?,?)",
            (diagnosis_id, req.device_id, now.isoformat(), req.gender, band,
             ",".join(weak), profile),
        )
        conn.commit()
        return result
    finally:
        conn.close()


@app.post("/api/v1/selfcheck")
def selfcheck(req: SelfCheckRequest):
    """
    도구 없이 하는 간편 자가진단.

    측정 장비가 없는 사용자를 위한 진입로다. 문항 점수(0~3)를 백분위로 환산하는데,
    이 값은 기준표와 대조한 결과가 아니라 **추정치**다. 응답의 estimated=true 로
    화면에서 '참고용'임을 반드시 밝힌다.
    """
    band = age_to_band(req.age)

    # 0~3점을 백분위 대표값으로 환산. 문항이 4지선다라 구간 중앙값을 쓴다.
    SCORE_TO_PCT = {0: 15.0, 1: 38.0, 2: 62.0, 3: 85.0}
    answers = {
        "strength": req.strength,
        "endurance": req.endurance,
        "flex": req.flex,
        "cardio": req.cardio,
        "power": req.power,
    }
    # 주간 활동량은 전체를 조금 끌어올리거나 내린다. 한 문항이 결과를 뒤집지 않도록 폭을 좁게 둔다.
    activity_shift = (req.activity - 1.5) * 4.0

    factor_pct = {
        f: max(1.0, min(99.0, SCORE_TO_PCT[score] + activity_shift))
        for f, score in answers.items()
    }
    factors = [
        {
            "factor": f, "label": FACTOR_LABEL[f],
            "percentile": round(factor_pct[f]), "grade": grade_of(factor_pct[f]),
        }
        for f in FACTOR_ORDER
    ]
    weak = [f for f, _ in sorted(factor_pct.items(), key=lambda kv: kv[1])[:2]]
    type_name, type_desc = imbalance_of(weak, factor_pct)
    profile = profile_of(factor_pct)

    now = datetime.now(KST)
    diagnosis_id = f"s_{now:%Y%m%d}_{uuid.uuid4().hex[:8]}"
    result = {
        "diagnosis_id": diagnosis_id,
        "measured_at": now.isoformat(timespec="seconds"),
        "age_band": band,
        "age_band_label": band_label(band),
        "gender": req.gender,
        "estimated": True,
        "profile": profile,
        "notice": "설문으로 추정한 참고용 결과입니다. 정확한 진단은 무료 체력인증센터 측정을 권합니다.",
        "total_score": round(sum(factor_pct.values()) / len(FACTOR_ORDER)),
        "imbalance_type": type_name,
        "imbalance_desc": type_desc,
        "factors": factors,
        "weak_factors": weak,
        "items": [],
        "bmi": None,
    }

    conn = _connect()
    try:
        conn.execute(
            "INSERT INTO diagnoses VALUES (?,?,?,?,?,?,?)",
            (diagnosis_id, req.device_id, now.isoformat(), req.gender, band,
             ",".join(weak), profile),
        )
        conn.commit()
    finally:
        conn.close()
    return result


@app.get("/api/v1/centers")
def centers(
    sido: str | None = Query(default=None, description="시도명. 주면 그 지역을 먼저 보여준다"),
    limit: int = Query(default=100, ge=1, le=200),
):
    """
    국민체력100 체력인증센터 목록. 전국 무료이며, 앱은 여기서 측정을 안내한다.

    좌표가 없어 거리순 정렬은 못 한다. [sido]가 오면 그 지역을 앞에 두고,
    나머지를 뒤에 붙여 돌려준다.
    """
    conn = _connect()
    try:
        rows = conn.execute(
            "SELECT * FROM centers ORDER BY sido, sigungu, center_name"
        ).fetchall()
    finally:
        conn.close()

    def to_dict(r: sqlite3.Row) -> dict:
        return {
            "center_code": r["center_code"], "sido": r["sido"], "sigungu": r["sigungu"],
            "center_name": r["center_name"], "address": r["address"], "tel": r["tel"],
            # 앱에서 지도 앱으로 넘길 검색어
            "map_query": f"{r['center_name']} 체력인증센터 {r['address']}",
        }

    items = [to_dict(r) for r in rows]
    nearby: list[dict] = []
    if sido:
        # 센터 목록은 "서울", 주소 색인은 "서울특별시"로 쓴다. 어느 쪽이 와도 맞도록
        # 서로 포함 관계만 본다. ("전남광주"와 "전남광주통합특별시"도 이렇게 맞는다)
        def same(a: str, b: str) -> bool:
            return a.startswith(b) or b.startswith(a)

        nearby = [c for c in items if same(c["sido"], sido)]
        items = nearby + [c for c in items if not same(c["sido"], sido)]

    return {
        "total": len(items),
        "nearby_count": len(nearby),
        "sido": sido,
        "reserve_url": "https://nfa.kspo.or.kr/reserve/0/selectMeasureReserveList.kspo",
        "notice": "국민체력100 체력인증센터는 만 13세 이상 누구나 무료로 이용할 수 있습니다.",
        "items": items[:limit],
    }


def _course_dict(r: sqlite3.Row) -> dict:
    keys = r.keys()
    return {
        "course_id": r["course_id"], "title": r["title"], "facility": r["facility"],
        # 주소는 참조표에 없으면 None. 앱은 이때 시설명만 보여준다.
        "address": (r["address"] if "address" in keys else None) or None,
        "sport": r["sport"], "weekday": r["weekday"], "start_time": r["start_time"],
        "lat": r["lat"], "lng": r["lng"],
        "tags": {f: r[f"tag_{f}"] for f in FACTOR_ORDER},
    }


@app.post("/api/v1/recommend")
def recommend(req: RecommendRequest):
    conn = _connect()
    try:
        weak = req.weak_factors
        profile = "improve"
        if req.diagnosis_id:
            row = conn.execute(
                "SELECT payload, profile FROM diagnoses WHERE diagnosis_id=?", (req.diagnosis_id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail=f"진단 결과를 찾을 수 없습니다: {req.diagnosis_id}")
            weak = row["payload"].split(",")
            profile = row["profile"] or "improve"
        if not weak:
            raise HTTPException(status_code=422, detail="diagnosis_id 또는 weak_factors 중 하나는 있어야 합니다")
        bad = [f for f in weak if f not in FACTOR_LABEL]
        if bad:
            raise HTTPException(status_code=422, detail=f"알 수 없는 체력요인: {bad}")

        leave_min = parse_hhmm(req.leave_time)
        maintain = profile == "maintain"
        if maintain:
            # 이미 다 양호하다. 특정 요인만 파고들 이유가 없으므로 모든 요인을 같게 두고,
            # 여러 요인을 함께 쓰는 강좌가 위로 오게 한다(한쪽만 계속 쓰면 균형이 깨진다).
            user_vec = [1.0 for _ in FACTOR_ORDER]
        else:
            # 약점 요인은 부족도 1.0, 나머지는 0.3으로 두어 약점 대응 강좌가 상위에 오게 한다
            user_vec = [1.0 if f in weak else 0.3 for f in FACTOR_ORDER]

        scored = []
        for r in conn.execute("SELECT c.*, fa.address AS address FROM courses c LEFT JOIN facility_addresses fa ON fa.facility = c.facility").fetchall():
            course_vec = [r[f"tag_{f}"] for f in FACTOR_ORDER]
            if sum(course_vec) == 0:
                continue

            start_min = parse_hhmm(r["start_time"])
            gap = start_min - leave_min
            is_weekend = r["weekday"] in ("토", "일")
            if not is_weekend and gap < 30:
                continue  # 퇴근 후 30분 이동 여유 확보 불가

            dist = distance_to_route_km(
                r["lat"], r["lng"], req.work_lat, req.work_lng, req.home_lat, req.home_lng
            )
            if dist > req.max_distance_km:
                continue

            sim = cosine(user_vec, [float(v) for v in course_vec])
            prox = 1 - min(dist / req.max_distance_km, 1.0)
            time_fit = 1.0 if is_weekend or 30 <= gap <= 120 else 0.5
            score = 0.7 * sim + 0.2 * prox + 0.1 * time_fit

            if maintain:
                covered = [FACTOR_LABEL[f] for f in FACTOR_ORDER if r[f"tag_{f}"] == 1]
                reason = (
                    f"{'·'.join(covered)}을 함께 쓰는 강좌이며 퇴근 동선에서 {dist:.1f}km"
                    if len(covered) >= 2
                    else f"퇴근 동선에서 {dist:.1f}km, 지금 수준을 이어 가기 좋은 {r['sport']}"
                )
            else:
                hit = [FACTOR_LABEL[f] for f in weak if r[f"tag_{f}"] == 1]
                reason = (
                    f"{'·'.join(hit)} 강화 강좌이며 퇴근 동선에서 {dist:.1f}km"
                    if hit
                    else f"퇴근 동선에서 {dist:.1f}km, {r['sport']} 종목"
                )

            item = _course_dict(r)
            item.update({"distance_km": round(dist, 1), "score": round(score, 2), "match_reason": reason})
            scored.append(item)

        scored.sort(key=lambda x: x["score"], reverse=True)
        top = scored[: req.limit]

        response = {
            "query": {
                "weak_factors": weak,
                "leave_time": req.leave_time,
                "max_distance_km": req.max_distance_km,
            },
            "profile": profile,
            "profile_notice": (
                "모든 요인이 또래 상위권입니다. 보완할 곳을 찾기보다 "
                "지금 수준을 이어 갈 수 있는 곳을 골랐습니다."
                if maintain
                else None
            ),
            "total": len(top),
            "items": top,
        }
        if not top:
            # 강좌가 없을 때 "거리를 넓혀 보세요" 만 내밀면 막다른 길이다.
            # 평생학습 강좌는 주간 위주라 퇴근 후 시작하는 것이 전체의 18%뿐이고,
            # 주요 도시 14곳 중 7곳이 18:30·3km 조건에서 0건이 나온다.
            # 반면 공공체육시설은 시간표가 없어 어디서나 잡힌다. 그쪽을 같이 알려 준다.
            nearby = count_facilities_near(conn, req)
            if nearby:
                response["hint"] = (
                    f"이 시간·거리에 맞는 강좌가 없습니다. "
                    f"대신 시간표 없이 갈 수 있는 공공체육시설이 {nearby}곳 있습니다."
                )
                response["facility_count"] = nearby
            else:
                response["hint"] = (
                    "조건에 맞는 강좌가 없습니다. 최대 거리를 넓히거나 퇴근 시각을 조정해 보세요."
                )
                response["facility_count"] = 0
            conn.execute(
                "INSERT INTO match_fail_logs (device_id, logged_at, weak_factors, work_lat, work_lng, leave_time)"
                " VALUES (?,?,?,?,?,?)",
                (
                    req.device_id, datetime.now(KST).isoformat(), ",".join(weak),
                    req.work_lat, req.work_lng, req.leave_time,
                ),
            )
            conn.commit()
        return response
    finally:
        conn.close()


def count_facilities_near(conn: sqlite3.Connection, req) -> int:
    """퇴근 동선 주변에 쓸 만한 공공체육시설이 몇 곳인지 센다.

    강좌 추천이 0건일 때 사용자에게 내밀 대안이 있는지 알려 주는 용도다.
    점수는 필요 없고 개수만 있으면 되므로 정렬·사유 생성은 하지 않는다.
    """
    n = 0
    for r in conn.execute("SELECT lat, lng FROM facilities").fetchall():
        dist = distance_to_route_km(
            r["lat"], r["lng"], req.work_lat, req.work_lng, req.home_lat, req.home_lng
        )
        if dist <= req.max_distance_km:
            n += 1
    return n


def _place_dict(r: sqlite3.Row) -> dict:
    parts = [r["sido"], r["sigungu"]] + ([r["dong"]] if r["dong"] else [])
    return {
        "label": " ".join(parts),
        "sido": r["sido"],
        "sigungu": r["sigungu"],
        "dong": r["dong"] or None,
        "lat": r["lat"],
        "lng": r["lng"],
    }


@app.get("/api/v1/places")
def search_places(
    q: str | None = Query(default=None, max_length=40),
    limit: int = Query(default=12, ge=1, le=30),
):
    """
    집·직장 위치를 찾기 위한 지역 검색.

    가로 스크롤 칩으로는 전국을 담을 수 없어 입력 검색으로 바꿨다.
    검색 대상은 공공체육시설 주소에서 뽑은 지역 색인(data/places.csv)이라
    별도 인증키 없이 전국이 나온다. 동 단위까지 찾으며, 동선 추천은 km 단위라
    이 정도 정밀도면 충분하다.

    q가 없으면 시도별 대표 지역을 돌려준다. 빈 검색창만 두면 무엇을 쳐야 할지
    모르는 사용자가 막히기 때문이다.
    """
    conn = _connect()
    try:
        terms = [t for t in (q or "").split() if t]

        if not terms:
            # 시도마다 시설이 가장 많은 시군구를 하나씩, 인구가 많은 시도 순으로 낸다.
            # 시설 수로만 줄 세우면 "상주시, 밀양시"가 앞에 와서 아무도 안 누른다.
            picked = []
            for sido in SIDO_ORDER:
                row = conn.execute(
                    "SELECT * FROM places WHERE sido LIKE ?"
                    " ORDER BY facility_count DESC LIMIT 1",
                    (f"{sido}%",),
                ).fetchone()
                if row is not None:
                    picked.append(_place_dict(row))
                if len(picked) >= limit:
                    break
            return {"query": "", "total": len(picked), "items": picked}

        # 공백으로 나눠 모두 포함하는 곳을 찾는다. "부산 전포동"처럼 띄어 써도 된다.
        sql = "SELECT * FROM places WHERE 1=1"
        params: list = []
        for t in terms:
            clause = "sido LIKE ? OR sigungu LIKE ? OR dong LIKE ?"
            args = [f"%{t}%"] * 3
            extra = PLACE_ALIAS.get(t)
            if extra:
                # 통합으로 시도명에서 사라진 이름을 살린다.
                # 광주는 '전남광주통합특별시'에 흡수돼 시군구에 '광주'가 없다.
                # 그대로 두면 "광주"를 친 사용자에게 순천·여수가 나온다.
                marks = ",".join("?" * len(extra["sigungu"]))
                clause += f" OR (sido LIKE ? AND sigungu IN ({marks}))"
                args += [f"%{extra['sido']}%"] + list(extra["sigungu"])
            sql += f" AND ({clause})"
            params += args

        # 시군구·동에 걸린 것을 시도명에만 걸린 것보다 앞에 둔다.
        # 이게 없으면 "광주"에 시도명이 걸려 전남 전역이 먼저 나온다.
        # 별칭으로 걸린 곳(옛 광주광역시 자치구)도 같은 대접을 받아야 한다.
        parts: list[str] = []
        order_params: list = []
        for t in terms:
            cond = "sigungu LIKE ? OR dong LIKE ?"
            order_params += [f"%{t}%"] * 2
            extra = PLACE_ALIAS.get(t)
            if extra:
                marks = ",".join("?" * len(extra["sigungu"]))
                cond += f" OR (sido LIKE ? AND sigungu IN ({marks}))"
                order_params += [f"%{extra['sido']}%"] + list(extra["sigungu"])
            parts.append(f"(CASE WHEN {cond} THEN 1 ELSE 0 END)")
        score = " + ".join(parts)
        # 그다음은 시설이 많은 동네 순. 이름이 짧을수록 사용자가 찾던 곳일 확률이 높다.
        sql += (f" ORDER BY ({score}) DESC, facility_count DESC,"
                " LENGTH(sigungu) + LENGTH(dong) ASC LIMIT ?")
        params += order_params
        params.append(limit)

        rows = conn.execute(sql, params).fetchall()
        return {"query": q, "total": len(rows), "items": [_place_dict(r) for r in rows]}
    finally:
        conn.close()


@app.post("/api/v1/facilities")
def recommend_facilities(req: RecommendRequest):
    """
    퇴근 동선 주변의 공공체육시설을 고른다.

    강좌를 들을 필요가 없는 사람(이미 다 양호하거나, 정해진 시간표에 매이기 싫은
    사람)에게는 '언제든 가서 쓸 수 있는 곳'이 답이다. 강좌와 달리 요일·시각
    제약이 없으므로 거리와 종목만으로 고른다.

    출처: 공공데이터포털 공공체육시설 상세 정보(15107764).
    """
    conn = _connect()
    try:
        weak = req.weak_factors or []
        profile = "improve"
        if req.diagnosis_id:
            row = conn.execute(
                "SELECT payload, profile FROM diagnoses WHERE diagnosis_id=?", (req.diagnosis_id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail=f"진단 결과를 찾을 수 없습니다: {req.diagnosis_id}")
            weak = row["payload"].split(",")
            profile = row["profile"] or "improve"
        bad = [f for f in weak if f not in FACTOR_LABEL]
        if bad:
            raise HTTPException(status_code=422, detail=f"알 수 없는 체력요인: {bad}")

        maintain = profile == "maintain" or not weak
        user_vec = (
            [1.0 for _ in FACTOR_ORDER] if maintain
            else [1.0 if f in weak else 0.3 for f in FACTOR_ORDER]
        )

        scored = []
        sql = ("SELECT f.*, fa.address AS address FROM facilities f "
               "LEFT JOIN facility_addresses fa ON fa.facility = f.facility")
        for r in conn.execute(sql).fetchall():
            dist = distance_to_route_km(
                r["lat"], r["lng"], req.work_lat, req.work_lng, req.home_lat, req.home_lng
            )
            if dist > req.max_distance_km:
                continue

            vec = [float(r[f"tag_{f}"]) for f in FACTOR_ORDER]
            if sum(vec) == 0:
                continue
            sim = cosine(user_vec, vec)
            prox = 1 - min(dist / req.max_distance_km, 1.0)
            # 시간표가 없으니 시간 적합도 항이 빠진다. 그만큼 거리를 더 본다.
            score = 0.6 * sim + 0.4 * prox

            covered = [FACTOR_LABEL[f] for f in FACTOR_ORDER if r[f"tag_{f}"] == 1]
            if maintain:
                reason = f"퇴근 동선에서 {dist:.1f}km, {'·'.join(covered)} 유지에 좋습니다"
            else:
                hit = [FACTOR_LABEL[f] for f in weak if r[f"tag_{f}"] == 1]
                reason = (
                    f"{'·'.join(hit)}을 쓰는 시설이며 퇴근 동선에서 {dist:.1f}km"
                    if hit else f"퇴근 동선에서 {dist:.1f}km"
                )

            scored.append({
                "facility": r["facility"],
                "address": r["address"] or None,
                "sport": r["sport"],
                "lat": r["lat"], "lng": r["lng"],
                "tags": {f: r[f"tag_{f}"] for f in FACTOR_ORDER},
                "distance_km": round(dist, 1),
                "score": round(score, 2),
                "match_reason": reason,
            })

        # 같은 종목이 줄줄이 나오면 고를 이유가 없다. 종목당 2곳까지만 남긴다.
        scored.sort(key=lambda x: x["score"], reverse=True)
        per_sport: dict[str, int] = {}
        picked = []
        for s in scored:
            n = per_sport.get(s["sport"], 0)
            if n >= 2:
                continue
            per_sport[s["sport"]] = n + 1
            picked.append(s)
            if len(picked) >= req.limit:
                break

        return {
            "profile": profile,
            "notice": (
                "정해진 시간표 없이 언제든 이용할 수 있는 공공체육시설입니다. "
                "이용 시간과 요금은 시설마다 다르니 방문 전에 확인하세요."
            ),
            "source": "공공데이터포털 공공체육시설 상세 정보",
            "total": len(picked),
            "items": picked,
        }
    finally:
        conn.close()


@app.get("/api/v1/courses")
def list_courses(
    sport: str | None = None,
    weekday: str | None = None,
    after: str | None = None,
    factor: str | None = None,
    limit: int = Query(default=50, ge=1, le=200),
):
    conn = _connect()
    try:
        sql = "SELECT c.*, fa.address AS address FROM courses c LEFT JOIN facility_addresses fa ON fa.facility = c.facility WHERE 1=1"
        params: list = []
        if sport:
            sql += " AND sport LIKE ?"
            params.append(f"%{sport}%")
        if weekday:
            if weekday not in WEEKDAYS:
                raise HTTPException(status_code=422, detail=f"요일 값이 잘못되었습니다: {weekday}")
            sql += " AND weekday = ?"
            params.append(weekday)
        if factor:
            if factor not in FACTOR_LABEL:
                raise HTTPException(status_code=422, detail=f"알 수 없는 체력요인: {factor}")
            sql += f" AND tag_{factor} = 1"
        rows = conn.execute(sql + " ORDER BY course_id", params).fetchall()

        items = [_course_dict(r) for r in rows]
        if after:
            after_min = parse_hhmm(after)
            items = [i for i in items if parse_hhmm(i["start_time"]) >= after_min]
        return {"total": len(items), "items": items[:limit]}
    finally:
        conn.close()


@app.get("/api/v1/courses/{course_id}")
def get_course(course_id: str):
    conn = _connect()
    try:
        r = conn.execute("SELECT c.*, fa.address AS address FROM courses c LEFT JOIN facility_addresses fa ON fa.facility = c.facility WHERE c.course_id=?", (course_id,)).fetchone()
        if r is None:
            raise HTTPException(status_code=404, detail=f"강좌를 찾을 수 없습니다: {course_id}")
        item = _course_dict(r)
        # 예약 연동은 스코프 밖. 외부 신청 페이지로 연결만 한다.
        item["apply_url"] = f"https://www.google.com/search?q={r['facility']}+{r['title']}+수강신청"
        return item
    finally:
        conn.close()
