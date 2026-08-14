# 설계 확정 — 화면 목록 & API 명세 (데모 v0.1)

기준 문서: `docs/서비스_아이디어.md`, `docs/역할분담_일일계획.md`, 루트 `CLAUDE.md`
데모 v0.1 범위: **입력 → 진단 리포트 → 강좌 추천** 3개 흐름 + 상세·설정. 샘플 데이터, 로컬 서버.

---

## 1. 화면 목록 (6개)

| # | 화면 | 목적 | 표시 요소 | 이동 |
| --- | --- | --- | --- | --- |
| S1 | 홈 | 진입점. 최근 진단 요약과 다음 행동 제시 | 앱 타이틀, 최근 진단 카드(종합점수·유형명, 없으면 "진단 시작" 안내), [체력 측정하기] 버튼, [추천 강좌 보기] 버튼(진단 이력 있을 때만 활성), 우상단 설정 아이콘 | → S2, S4, S6 |
| S2 | 체력 측정값 입력 | 진단에 필요한 6개 수치 입력 | 성별 선택(남/여), 나이 입력, 키·몸무게, 악력(kg), 교차윗몸일으키기(회), 앉아윗몸앞으로굽히기(cm), 왕복오래달리기(회), 눈감고외발서기(초). 각 필드 하단 단위·허용범위 안내와 오류 메시지. [진단하기] 버튼(전 항목 유효할 때만 활성) | → S3 |
| S3 | 진단 리포트 | 신체 불균형 유형과 항목별 백분위 제시 | 불균형 유형명 + 한 줄 설명, 종합 점수(0~100), 체력요인 4종(근력·유연성·심폐지구력·평형성) 백분위 막대, 항목별 상세(입력값 / 상위 n% / 등급), 약점 요인 2개 강조, BMI 구간 판정, [이 약점에 맞는 강좌 보기] 버튼 | → S4 |
| S4 | 강좌 추천 목록 | 약점 + 퇴근 동선 + 시간 조건에 맞는 공공 강좌 랭킹 | 상단 조건 요약 칩(약점 요인, 퇴근시각, 동선), 강좌 카드 리스트(강좌명, 시설명, 요일·시작시각, 종목, 동선에서 n.nkm, 매칭 점수, 매칭 이유 한 줄). 결과 0건일 때 빈 상태 + 조건 완화 안내 | → S5, S6 |
| S5 | 강좌 상세 | 강좌 단건 정보 확인 및 신청 연결 | 강좌명, 시설명·주소, 좌표 텍스트, 요일·시작시각, 종목, 체력요인 태그, 매칭 이유, [신청 페이지 열기] 버튼(외부 브라우저 링크) | → 외부 브라우저 |
| S6 | 설정 | 추천 조건과 기기 식별자 관리 | 직장 위치(위도·경도, 프리셋 선택 가능), 집 위치, 퇴근 시각, 최대 허용 거리(km), 기기 UUID 표시, 서버 주소 표시, [저장] 버튼 | → S1 |

**이동 관계**: `S1 → S2 → S3 → S4 → S5`, `S1 → S4`(진단 이력 있을 때), `S1 ⇄ S6`, `S4 → S6`(조건 수정)

### 스코프 제외 항목 자체 검수

| CLAUDE.md 제외 항목 | 설계에 섞였는가 | 확인 |
| --- | --- | --- |
| 로그인/회원가입 | 없음. S6에 기기 로컬 UUID 표시만 | OK |
| 협업 필터링/행렬분해 | 추천은 콘텐츠 기반 코사인 유사도 + 거리·시간 필터만 | OK |
| 예약 연동 | S5는 외부 브라우저 링크 열기까지만 | OK |
| B2G 대시보드 | 데모 화면 목록에서 **제외**. 아이디어 문서의 기능 3번은 v0.1 범위 밖 | OK |
| 지도 화면 | 제외. S5는 좌표를 텍스트로만 표시 | OK |

> 아이디어 문서 C-3(B2G 대시보드)은 정적 화면 1장으로 축소된 항목이며, 역할분담 문서의
> 데모 v0.1 범위("입력 → 진단 → 추천 3개 흐름 + 상세·설정")에 포함되지 않아 오늘 구현하지 않는다.

---

## 2. API 명세

- Base URL: `http://<host>:8000`
- 공통: 요청/응답 `application/json`, 에러는 `{"detail": "..."}` + 적절한 HTTP 코드
- 인증 없음. 사용자 식별이 필요한 곳은 요청 본문의 `device_id`(기기 로컬 UUID) 사용

### 2.1 `POST /api/v1/diagnose` — 진단

측정값 입력 → 백분위·약점 판정.

**요청**
```json
{
  "device_id": "1f3d9a4c-7e2b-4a11-9c0e-5b7d2a8e4f10",
  "gender": "M",
  "age": 34,
  "height_cm": 175.0,
  "weight_kg": 78.0,
  "grip_kg": 45.0,
  "sit_up": 38,
  "sit_reach_cm": 6.5,
  "shuttle_run": 52,
  "one_leg_stand_sec": 21.0
}
```
- `gender`: `"M"` | `"F"`
- `age`: 19~64
- 허용 범위는 `docs/서비스_아이디어.md`의 측정 항목 표를 따른다. 범위 밖이면 422.

**응답 200**
```json
{
  "diagnosis_id": "d_20260815_001",
  "measured_at": "2026-08-15T21:10:00+09:00",
  "age_band": "30s",
  "gender": "M",
  "total_score": 61,
  "imbalance_type": "유연성 저하 좌식형",
  "imbalance_desc": "근력은 평균 이상이나 유연성과 평형성이 크게 뒤처져, 장시간 앉은 자세로 인한 전형적 불균형 패턴입니다.",
  "factors": [
    { "factor": "strength", "label": "근력", "percentile": 68, "grade": "양호" },
    { "factor": "flex", "label": "유연성", "percentile": 22, "grade": "노력 필요" },
    { "factor": "cardio", "label": "심폐지구력", "percentile": 55, "grade": "보통" },
    { "factor": "balance", "label": "평형성", "percentile": 31, "grade": "미흡" }
  ],
  "weak_factors": ["flex", "balance"],
  "items": [
    { "item": "grip", "label": "상대악력", "value": 57.7, "unit": "%", "percentile": 62, "grade": "양호" },
    { "item": "sit_up", "label": "교차윗몸일으키기", "value": 38, "unit": "회", "percentile": 74, "grade": "우수" },
    { "item": "sit_reach", "label": "앉아윗몸앞으로굽히기", "value": 6.5, "unit": "cm", "percentile": 22, "grade": "노력 필요" },
    { "item": "shuttle_run", "label": "왕복오래달리기", "value": 52, "unit": "회", "percentile": 55, "grade": "보통" },
    { "item": "one_leg_stand", "label": "눈감고외발서기", "value": 21.0, "unit": "초", "percentile": 31, "grade": "미흡" }
  ],
  "bmi": { "value": 25.5, "category": "과체중", "in_normal_range": false }
}
```

- `percentile`은 "상위 몇 %"가 아니라 **하위 기준 백분위**(값이 클수록 우수). 화면에서는
  "상위 n%" 표기 시 `100 - percentile`로 환산한다.
- `grade`: 백분위 구간 → `우수`(80+) / `양호`(60~79) / `보통`(40~59) / `미흡`(20~39) / `노력 필요`(<20)
- `weak_factors`: 4개 요인 중 백분위가 낮은 2개
- 진단 결과는 서버 SQLite에 저장되어 `diagnosis_id`로 추천 요청 시 재사용 가능

**에러**: 422(입력 범위 위반), 400(성별 코드 오류)

### 2.2 `POST /api/v1/recommend` — 추천

약점 + 퇴근 동선/시간 → 강좌 랭킹.

**요청**
```json
{
  "device_id": "1f3d9a4c-7e2b-4a11-9c0e-5b7d2a8e4f10",
  "diagnosis_id": "d_20260815_001",
  "work_lat": 37.5665, "work_lng": 126.9780,
  "home_lat": 37.4979, "home_lng": 127.0276,
  "leave_time": "18:30",
  "max_distance_km": 2.0,
  "limit": 10
}
```
- `diagnosis_id` 대신 `weak_factors: ["flex","balance"]`를 직접 넘겨도 동작(진단 없이 추천 조회 가능)
- `leave_time`: `HH:MM` 24시간. 강좌 시작시각이 `leave_time + 30분` 이후인 것만 후보
- `max_distance_km`: 퇴근 동선(직장↔집 직선)으로부터의 최대 거리

**응답 200**
```json
{
  "query": { "weak_factors": ["flex", "balance"], "leave_time": "18:30", "max_distance_km": 2.0 },
  "total": 4,
  "items": [
    {
      "course_id": "C012",
      "title": "저녁 요가 (중급)",
      "facility": "강남구민체육센터",
      "sport": "요가",
      "weekday": "화",
      "start_time": "19:30",
      "lat": 37.5172, "lng": 127.0473,
      "distance_km": 1.2,
      "score": 0.91,
      "match_reason": "유연성·평형성 강화 강좌이며 퇴근 동선에서 1.2km",
      "tags": { "strength": 0, "flex": 1, "cardio": 0, "balance": 1 }
    }
  ]
}
```
- `score`: 0~1. 코사인 유사도(0.7) + 거리 근접도(0.2) + 시간 여유(0.1) 가중합
- 결과 0건이면 `total: 0`, `items: []` 그리고 `hint` 필드에 완화 제안 문구 포함
- 매칭 실패(0건) 시 서버에 로그 저장 (B2G 수요 스코어의 입력, 오늘은 적재만)

**에러**: 404(`diagnosis_id` 없음), 422(시각 형식 오류)

### 2.3 `GET /api/v1/courses` — 강좌 조회

**쿼리 파라미터** (전부 선택)
- `sport`: 종목명 부분 일치
- `weekday`: `월`~`일`
- `after`: `HH:MM`, 이 시각 이후 시작하는 강좌만
- `factor`: `strength|flex|cardio|balance`, 해당 태그가 1인 강좌만
- `limit`: 기본 50

**응답 200**
```json
{
  "total": 30,
  "items": [
    {
      "course_id": "C001",
      "title": "직장인 코어 필라테스",
      "facility": "마포구민체육센터",
      "sport": "필라테스",
      "weekday": "월",
      "start_time": "19:00",
      "lat": 37.5563, "lng": 126.9236,
      "tags": { "strength": 1, "flex": 1, "cardio": 0, "balance": 1 }
    }
  ]
}
```

### 2.4 `GET /api/v1/courses/{course_id}` — 강좌 단건

**응답 200**: 2.3의 `items` 원소와 동일한 구조 + `apply_url`(외부 신청 페이지 링크)
**에러**: 404

### 2.5 `GET /api/v1/health` — 상태 확인

```json
{ "status": "ok", "norms_rows": 180, "courses_rows": 30 }
```

---

## 3. 진단·추천 계산 규칙

### 3.1 백분위 산출
1. `age`로 `age_band` 결정: `20s`(19~29), `30s`(30~39), `40s`(40~49), `50s`(50~64)
2. `norms.csv`에서 `(gender, age_band, item)`에 해당하는 (percentile, value) 점들을 정렬
3. 사용자 값을 이 점들 사이에서 **선형 보간**하여 백분위 산출.
   최소점 미만은 1, 최대점 초과는 99로 클램프
4. `grip`은 상대악력 `악력kg / 체중kg × 100`으로 변환 후 조회

### 3.2 요인 백분위
- `strength` = (`grip` 백분위 + `sit_up` 백분위) / 2
- `flex` = `sit_reach` 백분위
- `cardio` = `shuttle_run` 백분위
- `balance` = `one_leg_stand` 백분위
- `total_score` = 4개 요인 백분위 평균(반올림)

### 3.3 불균형 유형 (규칙 기반, 실데이터 도착 후 군집 라벨로 교체 예정)
약점 2개 조합으로 유형명 결정. 예: `flex+balance` → "유연성 저하 좌식형",
`cardio+strength` → "체력 저하 번아웃형", `flex+cardio` → "경직 저활동형" 등.
전 요인 60 이상이면 "균형 양호형".

### 3.4 추천 점수
```
user_vec  = 요인별 부족도 = (100 - factor_percentile) / 100   # 4차원
course_vec = [tag_strength, tag_flex, tag_cardio, tag_balance]
sim  = cosine(user_vec, course_vec)
prox = 1 - min(distance_km / max_distance_km, 1)
time = 1 if 여유 30~120분 else 0.5
score = 0.7*sim + 0.2*prox + 0.1*time
```
- `distance_km`: 직장 좌표–집 좌표를 잇는 **선분과 강좌 좌표 사이의 최단거리**(퇴근 동선 근접도)
- 태그가 전부 0인 강좌는 후보에서 제외

---

## 4. 데이터 파일

`CLAUDE.md`의 스키마를 **열 이름·순서 그대로** 유지한다.

- `data/norms.csv`: `gender, age_band, item, percentile, value`
  - 샘플 규모: 2성별 × 4나이대 × 5항목 × 5백분위(10/25/50/75/90) = 200행
  - ※ CLAUDE.md의 "샘플 20행"은 최소 예시 규모이나, 5개 항목의 보간 곡선을 만들려면
    항목당 최소 5점이 필요해 200행으로 생성한다. **열 구성은 변경하지 않음**
- `data/courses.csv`: `course_id, title, facility, lat, lng, weekday, start_time, sport,
  tag_strength, tag_flex, tag_cardio, tag_balance` — 샘플 30행
- 서버 기동 시 두 CSV를 SQLite(`data/app.db`)로 적재. 파일 교체 후 재기동만으로 반영
