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
ITEM_META = {
    "grip": ("상대악력", "%", 10.0, 150.0),
    "sit_up": ("교차윗몸일으키기", "회", 0.0, 100.0),
    "sit_reach": ("앉아윗몸앞으로굽히기", "cm", -30.0, 40.0),
    "shuttle_run": ("왕복오래달리기", "회", 0.0, 120.0),
    "one_leg_stand": ("눈감고외발서기", "초", 0.0, 120.0),
}

FACTOR_LABEL = {
    "strength": "근력",
    "flex": "유연성",
    "cardio": "심폐지구력",
    "balance": "평형성",
}
FACTOR_ORDER = ["strength", "flex", "cardio", "balance"]

# 약점 요인 조합 -> 불균형 유형명과 설명
IMBALANCE_TYPES = {
    frozenset(["flex", "balance"]): (
        "유연성 저하 좌식형",
        "근력은 유지되고 있으나 유연성과 평형성이 뒤처져, 장시간 앉은 자세로 인한 전형적 불균형 패턴입니다.",
    ),
    frozenset(["flex", "cardio"]): (
        "경직 저활동형",
        "몸이 굳어 있고 심폐지구력도 낮아, 활동량 자체가 부족한 상태입니다.",
    ),
    frozenset(["flex", "strength"]): (
        "근력·유연성 동반 저하형",
        "근력과 유연성이 함께 낮아 부상 위험이 큽니다. 저강도 근력 운동부터 시작하는 것이 좋습니다.",
    ),
    frozenset(["cardio", "strength"]): (
        "체력 저하 번아웃형",
        "근력과 심폐지구력이 모두 낮습니다. 만성 피로로 이어지기 쉬운 상태입니다.",
    ),
    frozenset(["cardio", "balance"]): (
        "지구력 부족 불안정형",
        "심폐지구력과 평형성이 낮아 장시간 활동 시 자세가 쉽게 무너집니다.",
    ),
    frozenset(["strength", "balance"]): (
        "코어 약화형",
        "근력과 평형성이 낮아 코어가 약해진 상태로, 허리 부담이 커지기 쉽습니다.",
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
            tag_strength INTEGER, tag_flex INTEGER, tag_cardio INTEGER, tag_balance INTEGER
        );
        CREATE TABLE IF NOT EXISTS diagnoses (
            diagnosis_id TEXT PRIMARY KEY, device_id TEXT, measured_at TEXT,
            gender TEXT, age_band TEXT, payload TEXT
        );
        CREATE TABLE IF NOT EXISTS match_fail_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT, logged_at TEXT,
            weak_factors TEXT, work_lat REAL, work_lng REAL, leave_time TEXT
        );
        """
    )

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
            "sport", "tag_strength", "tag_flex", "tag_cardio", "tag_balance",
        ]
        if reader.fieldnames != expected:
            raise RuntimeError(f"courses.csv 열 구성이 계약과 다릅니다: {reader.fieldnames} (기대: {expected})")
        course_rows = [
            (
                r["course_id"], r["title"], r["facility"], float(r["lat"]), float(r["lng"]),
                r["weekday"], r["start_time"], r["sport"],
                int(r["tag_strength"]), int(r["tag_flex"]), int(r["tag_cardio"]), int(r["tag_balance"]),
            )
            for r in reader
        ]
    cur.executemany("INSERT INTO courses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", course_rows)

    conn.commit()
    conn.close()
    return len(norms_rows), len(course_rows)


# ---------------------------------------------------------------- 계산 로직


def age_to_band(age: int) -> str:
    if age < 30:
        return "20s"
    if age < 40:
        return "30s"
    if age < 50:
        return "40s"
    return "50s"


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

    if value <= points[0][0]:
        return 1.0
    if value >= points[-1][0]:
        return 99.0
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


def imbalance_of(weak: list[str], factor_pct: dict[str, float]) -> tuple[str, str]:
    if all(v >= 60 for v in factor_pct.values()):
        return "균형 양호형", "네 가지 체력요인이 모두 평균 이상입니다. 현재 활동량을 유지하세요."
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
    one_leg_stand_sec: float = Field(ge=0, le=120)


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
            "one_leg_stand": req.one_leg_stand_sec,
        }

        items = []
        pct = {}
        for item, value in raw_values.items():
            label, unit, lo, hi = ITEM_META[item]
            if not (lo <= value <= hi):
                raise HTTPException(status_code=422, detail=f"{label} 값이 허용 범위({lo}~{hi}{unit})를 벗어났습니다: {value}")
            p = percentile_of(conn, req.gender, band, item, value)
            pct[item] = p
            items.append({
                "item": item, "label": label, "value": value, "unit": unit,
                "percentile": round(p), "grade": grade_of(p),
            })

        factor_pct = {
            "strength": round((pct["grip"] + pct["sit_up"]) / 2, 1),
            "flex": pct["sit_reach"],
            "cardio": pct["shuttle_run"],
            "balance": pct["one_leg_stand"],
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
        cat, in_range = bmi_category(bmi)

        now = datetime.now(KST)
        diagnosis_id = f"d_{now:%Y%m%d}_{uuid.uuid4().hex[:8]}"
        result = {
            "diagnosis_id": diagnosis_id,
            "measured_at": now.isoformat(timespec="seconds"),
            "age_band": band,
            "gender": req.gender,
            "total_score": round(sum(factor_pct.values()) / 4),
            "imbalance_type": type_name,
            "imbalance_desc": type_desc,
            "factors": factors,
            "weak_factors": weak,
            "items": items,
            "bmi": {"value": bmi, "category": cat, "in_normal_range": in_range},
        }

        conn.execute(
            "INSERT INTO diagnoses VALUES (?,?,?,?,?,?)",
            (diagnosis_id, req.device_id, now.isoformat(), req.gender, band, ",".join(weak)),
        )
        conn.commit()
        return result
    finally:
        conn.close()


def _course_dict(r: sqlite3.Row) -> dict:
    return {
        "course_id": r["course_id"], "title": r["title"], "facility": r["facility"],
        "sport": r["sport"], "weekday": r["weekday"], "start_time": r["start_time"],
        "lat": r["lat"], "lng": r["lng"],
        "tags": {
            "strength": r["tag_strength"], "flex": r["tag_flex"],
            "cardio": r["tag_cardio"], "balance": r["tag_balance"],
        },
    }


@app.post("/api/v1/recommend")
def recommend(req: RecommendRequest):
    conn = _connect()
    try:
        weak = req.weak_factors
        if req.diagnosis_id:
            row = conn.execute(
                "SELECT payload FROM diagnoses WHERE diagnosis_id=?", (req.diagnosis_id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail=f"진단 결과를 찾을 수 없습니다: {req.diagnosis_id}")
            weak = row["payload"].split(",")
        if not weak:
            raise HTTPException(status_code=422, detail="diagnosis_id 또는 weak_factors 중 하나는 있어야 합니다")
        bad = [f for f in weak if f not in FACTOR_LABEL]
        if bad:
            raise HTTPException(status_code=422, detail=f"알 수 없는 체력요인: {bad}")

        leave_min = parse_hhmm(req.leave_time)
        # 약점 요인은 부족도 1.0, 나머지는 0.3으로 두어 약점 대응 강좌가 상위에 오게 한다
        user_vec = [1.0 if f in weak else 0.3 for f in FACTOR_ORDER]

        scored = []
        for r in conn.execute("SELECT * FROM courses").fetchall():
            course_vec = [r["tag_strength"], r["tag_flex"], r["tag_cardio"], r["tag_balance"]]
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

            hit = [FACTOR_LABEL[f] for f in weak if r[f"tag_{f}"] == 1]
            if hit:
                reason = f"{'·'.join(hit)} 강화 강좌이며 퇴근 동선에서 {dist:.1f}km"
            else:
                reason = f"퇴근 동선에서 {dist:.1f}km, {r['sport']} 종목"

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
            "total": len(top),
            "items": top,
        }
        if not top:
            response["hint"] = "조건에 맞는 강좌가 없습니다. 최대 거리를 넓히거나 퇴근 시각을 조정해 보세요."
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
        sql = "SELECT * FROM courses WHERE 1=1"
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
        r = conn.execute("SELECT * FROM courses WHERE course_id=?", (course_id,)).fetchone()
        if r is None:
            raise HTTPException(status_code=404, detail=f"강좌를 찾을 수 없습니다: {course_id}")
        item = _course_dict(r)
        # 예약 연동은 스코프 밖. 외부 신청 페이지로 연결만 한다.
        item["apply_url"] = f"https://www.google.com/search?q={r['facility']}+{r['title']}+수강신청"
        return item
    finally:
        conn.close()
