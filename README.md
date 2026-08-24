# fitbalance

직장인 신체 불균형 진단 + 퇴근길 공공 체육 강좌·시설 추천.
국민체력100 실측 자료로 약한 곳을 찾고, 출퇴근 동선 안에서 갈 만한 곳을 골라 줍니다.

**홈페이지 · 내려받기 → https://inhyeokkang.github.io/fitbalance/**

- 기준 문서: `contracts/design.md` (화면·API 명세), `docs/` (서비스 정의·일일 계획)
- 개발 규칙: 루트 `CLAUDE.md`

---

## 팀원(한서연·최서영)은 여기부터

→ **[docs/팀원_시작하기.md](docs/팀원_시작하기.md)**

앱이 무엇인지, 각자 뭘 하면 되는지 3분이면 파악됩니다.

---

## 앱을 써 보려면 (팀원·심사용)

저장소를 받을 필요 없습니다. [홈페이지](https://inhyeokkang.github.io/fitbalance/)에서
운영체제에 맞는 파일을 받아 실행하면 **에뮬레이터부터 앱, 서버까지 한 번에** 깔립니다.

| 쓰는 컴퓨터 | 받을 것 | 실행할 파일 |
| --- | --- | --- |
| 윈도우 | `fitbalance-windows.zip` | `설치.bat` |
| 맥 | `fitbalance-mac.zip` | `설치.command` (처음엔 우클릭 → 열기) |
| 안드로이드 폰 | `fitbalance.apk` | 폰에 옮겨 설치 |

처음 실행하면 약 2GB를 내려받습니다(10~20분). 다음부터는 켜기만 합니다.

> **서버가 꺼지면 진단·추천·검색이 멈춥니다.** 설치 스크립트가 서버까지 같이 켭니다.
> 상시 운영은 `notes/서버_배포.md` 참고.

---

## 저장소를 받은 사람 (개발용)

서버만 띄워 웹 데모를 보려면 아래 파일을 더블클릭하면 됩니다.

| 쓰는 컴퓨터 | 더블클릭할 파일 |
| --- | --- |
| 윈도우 | `실행_윈도우.bat` |
| 맥 | `실행_맥.command` (처음엔 우클릭 → 열기) |

검은 창이 뜨고 잠시 뒤 브라우저가 자동으로 열립니다. 첫 실행은 1~2분 걸립니다.
끝낼 때는 검은 창을 닫으면 됩니다. 파이썬이 없으면 설치 방법을 안내해 줍니다.

**각자 할 일과 문서 양식**

| 담당 | 가이드 | 리포트 (매일 + 종합) |
| --- | --- | --- |
| 한서연 (검증) | [docs/가이드_한서연_검증.md](docs/가이드_한서연_검증.md) | [docs/리포트/한서연/](docs/리포트/한서연/) |
| 최서영 (데이터) | [docs/가이드_최서영_데이터.md](docs/가이드_최서영_데이터.md) | [docs/리포트/최서영/](docs/리포트/최서영/) |

개발 메모·데이터 확보 기록·검토 문서는 `notes/` 에 있다.

---

## 지금 되는 것

| 구성 | 상태 |
| --- | --- |
| FastAPI 백엔드 | 동작 확인 완료, pytest 16건 통과 |
| 체력 기준표 450행 | 국민체력100 실측 15만 건에서 생성 (2022년 개정 기준 이후) |
| 공공체육시설 | 전국 34,727곳 중 강습 가능 5,804곳 |
| 지역 색인 2,153곳 | 전국 16개 시·도, 동 단위 검색 |
| 체력인증센터 78개소 | 전국 실데이터 |
| 강좌 시간표 | **실데이터 2,945건** — 전국평생학습강좌표준데이터. 광주·전남·세종은 아직 비어 있음 |
| 안드로이드 앱 | 빌드·에뮬레이터 동작 확인 완료 |
| 카카오 지도 | 실제 렌더링 확인 완료 (설정은 `docs/개발_진행_가이드.md`) |
| 홈페이지 | GitHub Pages 배포 완료 |

---

## 개발자용

### 백엔드

```bash
python -m uvicorn server.main:app --host 0.0.0.0 --port 8000
```

- 브라우저 미리보기: http://127.0.0.1:8000/demo
- API 문서: http://127.0.0.1:8000/docs
- 테스트: `python -m pytest -q`

상시 운영(무료 호스팅)은 [notes/서버_배포.md](notes/서버_배포.md) 참고.

### 앱 빌드

```bash
powershell -ExecutionPolicy Bypass -File tools\setup_android_sdk.ps1   # 처음 한 번
.\gradlew.bat assembleDebug
```

APK: `appuild\outputspk\debugpp-debug.apk`

에뮬레이터에 한글 자판이 없으면 `python tools/emulator_korean.py`.

### 배포 파일 만들기

```bash
python tools/build_release.py          # dist/ 에 zip 2개 + APK
gh release upload v0.1.0 dist/*        # 홈페이지 버튼이 릴리스를 가리킨다
```

### 데이터 다시 만들기

```bash
python tools/fetch_kspo.py         # 국민체력100 측정 원자료 표본
python tools/build_norms.py        # -> data/norms.csv (기준표 450행)
python tools/fetch_facilities.py --all   # 전국 공공체육시설
python tools/build_courses.py      # -> courses_seed.csv, facility_addresses.csv
python tools/build_places.py       # -> places.csv (지역 검색 색인)
python tools/fetch_lessons.py      # -> data/raw/lessons.csv (전국 강좌)
python tools/build_courses_from_lessons.py  # -> courses.csv
python tools/geocode_addresses.py  # 주소 -> 좌표 (카카오 로컬)
python tools/build_courses_from_lessons.py  # 새 좌표를 붙여 다시 생성
python tools/fetch_centers.py      # -> centers.csv (체력인증센터)
```

인증키는 `local.properties` 에 둔다(커밋되지 않음).
`DATA_GO_KR_KEY`(공공데이터포털), `KAKAO_MAP_KEY`(지도), `KAKAO_REST_KEY`(주소→좌표).

### 데이터 교체

최서영에게 받은 `courses.csv` 를 `data/` 에 덮어쓰고 서버를 다시 켜면 끝이다.
서버가 기동 시 열 이름·순서를 검증하고, 다르면 적재를 거부한다.

스키마는 `CLAUDE.md` 의 데이터 계약에 고정돼 있다.

- `norms.csv`: `gender, age_band, item, percentile, value`
  - `item`: `grip`(상대악력 %) / `sit_up` / `sit_reach` / `shuttle_run` / `standing_jump`
  - `age_band`: `19-24` ~ `60-64` (5세 단위 9구간)
- `courses.csv`: `course_id, title, facility, lat, lng, weekday, start_time, sport,
  tag_strength, tag_endurance, tag_flex, tag_cardio, tag_power`
