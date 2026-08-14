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
    "one_leg_stand_sec": 21.0,
}


def test_health():
    with TestClient(app) as c:
        r = c.get("/api/v1/health")
        assert r.status_code == 200
        body = r.json()
        assert body["status"] == "ok"
        assert body["norms_rows"] == 200
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
        assert b["age_band"] == "30s"
        assert len(b["items"]) == 5
        assert len(b["factors"]) == 4
        assert len(b["weak_factors"]) == 2
        assert 0 <= b["total_score"] <= 100
        for f in b["factors"]:
            assert 1 <= f["percentile"] <= 99
        assert b["bmi"]["value"] > 0


def test_diagnose_rejects_out_of_range():
    with TestClient(app) as c:
        bad = dict(SAMPLE, sit_reach_cm=999)
        assert c.post("/api/v1/diagnose", json=bad).status_code == 422


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
            "weak_factors": ["flex", "balance"],
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
            "weak_factors": ["flex", "balance"],
            "work_lat": 37.5665, "work_lng": 126.9780,
            "home_lat": 37.4979, "home_lng": 127.0276,
            "leave_time": "18:30", "max_distance_km": 5.0,
        }).json()
        assert rec["total"] >= 1
        assert all(i["address"] for i in rec["items"]), "추천에 주소 없는 강좌가 있습니다"
