"""스모크 테스트: 3개 핵심 엔드포인트가 명세대로 응답하는지 확인한다."""
from fastapi.testclient import TestClient

from main import app

SAMPLE = {
    "device_id": "test-device-0001",
    "gender": "M",
    "age": 34,
    "height_cm": 175.0,
    "weight_kg": 78.0,
    "grip_kg": 45.0,
    "sit_up": 38,
    "sit_reach_cm": 6.5,
    "shuttle_run": 52,
    "standing_jump_cm": 205.0,
}


def test_health():
    with TestClient(app) as c:
        r = c.get("/api/v1/health")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "ok"
        assert body["norms_rows"] == 450
        assert body["courses_rows"] == 30


def test_demo_page_served():
    with TestClient(app) as c:
        r = c.get("/demo")
        assert r.status_code == 200
        # 브랜드 표기는 소문자 영문 워드마크로 통일했다.
        assert "fitbalance" in r.text
        assert "핏밸런스" not in r.text


def test_diagnose_returns_percentiles_and_weak_factors():
    with TestClient(app) as c:
        r = c.post("/api/v1/diagnose", json=SAMPLE)
        assert r.status_code == 200, r.text
        b = r.json()
        assert b["age_band"] == "30-34"
        assert b["age_band_label"] == "30~34세"
        assert b["estimated"] is False
        assert len(b["items"]) == 5
        assert len(b["factors"]) == 5
        assert len(b["weak_factors"]) == 2
        assert 0 <= b["total_score"] <= 100
        for f in b["factors"]:
            assert 1 <= f["percentile"] <= 99
        assert b["bmi"]["value"] > 0


def test_diagnose_rejects_out_of_range():
    with TestClient(app) as c:
        bad = dict(SAMPLE, sit_reach_cm=999)
        assert c.post("/api/v1/diagnose", json=bad).status_code == 422


def test_factors_follow_kspo_classification():
    """공단 분류를 따르는지 확인. 교차윗몸일으키기는 근력이 아니라 근지구력이다."""
    with TestClient(app) as c:
        b = c.post("/api/v1/diagnose", json=SAMPLE).json()
        codes = [f["factor"] for f in b["factors"]]
        assert codes == ["strength", "endurance", "flex", "cardio", "power"]
        labels = [f["label"] for f in b["factors"]]
        assert labels == ["근력", "근지구력", "유연성", "심폐지구력", "순발력"]
        # 평형성은 어르신기 항목이므로 성인기 진단에 나오면 안 된다.
        assert "평형성" not in labels
        items = [i["item"] for i in b["items"]]
        assert "one_leg_stand" not in items
        assert "standing_jump" in items


def test_age_band_is_five_year_range():
    """공단 인증기준이 5세 단위이므로 10세로 묶으면 안 된다."""
    with TestClient(app) as c:
        for age, expected in [(19, "19-24"), (29, "25-29"), (34, "30-34"), (64, "60-64")]:
            b = c.post("/api/v1/diagnose", json=dict(SAMPLE, age=age)).json()
            assert b["age_band"] == expected, f"{age}세 -> {b['age_band']}"


def test_selfcheck_returns_estimated_result():
    """도구 없는 간편 자가진단. 결과는 추정치이며 그렇게 표시돼야 한다."""
    with TestClient(app) as c:
        r = c.post("/api/v1/selfcheck", json={
            "device_id": "test-self-0001", "gender": "F", "age": 41,
            "strength": 1, "endurance": 0, "flex": 2, "cardio": 3, "power": 2,
            "activity": 1,
        })
        assert r.status_code == 200, r.text
        b = r.json()
        assert b["estimated"] is True
        assert b["notice"]
        assert b["age_band"] == "40-44"
        assert len(b["factors"]) == 5
        # 점수가 가장 낮은 두 요인이 약점으로 나와야 한다
        assert set(b["weak_factors"]) == {"endurance", "strength"}
        assert b["items"] == []
        assert b["bmi"] is None


def test_selfcheck_result_drives_recommendation():
    """자가진단 결과로도 추천이 돌아가야 한다. 약점 요인만 알면 되기 때문이다."""
    with TestClient(app) as c:
        s = c.post("/api/v1/selfcheck", json={
            "device_id": "test-self-0002", "gender": "M", "age": 33,
            "strength": 0, "endurance": 1, "flex": 3, "cardio": 2, "power": 3,
            "activity": 2,
        }).json()
        r = c.post("/api/v1/recommend", json={
            "device_id": "test-self-0002",
            "diagnosis_id": s["diagnosis_id"],
            "work_lat": 37.5665, "work_lng": 126.9780,
            "home_lat": 37.4979, "home_lng": 127.0276,
            "leave_time": "18:30", "max_distance_km": 5.0,
        })
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["query"]["weak_factors"] == s["weak_factors"]
        assert body["total"] >= 1


def test_recommend_ranks_courses_for_weak_factors():
    with TestClient(app) as c:
        d = c.post("/api/v1/diagnose", json=SAMPLE).json()
        r = c.post("/api/v1/recommend", json={
            "device_id": SAMPLE["device_id"],
            "diagnosis_id": d["diagnosis_id"],
            "work_lat": 37.5665, "work_lng": 126.9780,
            "home_lat": 37.4979, "home_lng": 127.0276,
            "leave_time": "18:30",
            "max_distance_km": 3.0,
            "limit": 10,
        })
        assert r.status_code == 200, r.text
        b = r.json()
        assert b["query"]["weak_factors"] == d["weak_factors"]
        assert b["total"] >= 1
        scores = [i["score"] for i in b["items"]]
        assert scores == sorted(scores, reverse=True)
        for i in b["items"]:
            assert i["distance_km"] <= 3.0
            assert i["match_reason"]


def test_recommend_without_diagnosis_id():
    with TestClient(app) as c:
        r = c.post("/api/v1/recommend", json={
            "device_id": "no-diag",
            "weak_factors": ["flex", "power"],
            "work_lat": 37.5665, "work_lng": 126.9780,
            "home_lat": 37.4979, "home_lng": 127.0276,
            "leave_time": "18:30", "max_distance_km": 3.0,
        })
        assert r.status_code == 200
        assert r.json()["total"] >= 1


def test_recommend_unknown_diagnosis_id_404():
    with TestClient(app) as c:
        r = c.post("/api/v1/recommend", json={
            "device_id": "x", "diagnosis_id": "d_nope",
            "work_lat": 37.5, "work_lng": 127.0, "home_lat": 37.5, "home_lng": 127.0,
        })
        assert r.status_code == 404


def test_courses_list_and_filter():
    with TestClient(app) as c:
        assert c.get("/api/v1/courses").json()["total"] == 30
        flex = c.get("/api/v1/courses", params={"factor": "flex"}).json()
        assert all(i["tags"]["flex"] == 1 for i in flex["items"])
        late = c.get("/api/v1/courses", params={"after": "19:30"}).json()
        assert all(i["start_time"] >= "19:30" for i in late["items"])


def test_course_detail_and_404():
    with TestClient(app) as c:
        b = c.get("/api/v1/courses/C012").json()
        assert b["title"] == "저녁 요가 (중급)"
        assert b["apply_url"].startswith("https://")
        assert c.get("/api/v1/courses/ZZZZ").status_code == 404


def test_address_is_joined_everywhere():
    """좌표 대신 주소를 보여줘야 하므로, 세 경로 모두에 address가 실려야 한다."""
    with TestClient(app) as c:
        detail = c.get("/api/v1/courses/C012").json()
        assert detail["address"], "상세에 주소가 없습니다"
        assert detail["address"].startswith("서울")

        listed = c.get("/api/v1/courses").json()["items"]
        assert all(i["address"] for i in listed), "목록에 주소 없는 강좌가 있습니다"

        rec = c.post("/api/v1/recommend", json={
            "device_id": "addr-test",
            "weak_factors": ["flex", "power"],
            "work_lat": 37.5665, "work_lng": 126.9780,
            "home_lat": 37.4979, "home_lng": 127.0276,
            "leave_time": "18:30", "max_distance_km": 5.0,
        }).json()
        assert rec["total"] >= 1
        assert all(i["address"] for i in rec["items"]), "추천에 주소 없는 강좌가 있습니다"


def test_centers_lists_real_kspo_centers():
    """체력인증센터 목록. 측정 장비가 없는 사용자를 무료 측정으로 안내하는 화면이 쓴다."""
    with TestClient(app) as c:
        b = c.get("/api/v1/centers").json()
        assert b["total"] >= 70, f"센터가 {b['total']}개뿐입니다"
        assert b["reserve_url"].startswith("https://")
        first = b["items"][0]
        for key in ("center_code", "sido", "sigungu", "center_name", "address", "map_query"):
            assert first[key], f"{key}가 비어 있습니다"


def test_centers_puts_requested_sido_first():
    with TestClient(app) as c:
        b = c.get("/api/v1/centers", params={"sido": "서울"}).json()
        assert b["nearby_count"] >= 1
        # 요청한 지역이 앞쪽에 모여 있어야 한다
        head = b["items"][: b["nearby_count"]]
        assert all(i["sido"] == "서울" for i in head)
