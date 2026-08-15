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
> 상시 운영은 `docs/서버_배포.md` 참고.

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

| 담당 | 가이드 | 쓸 템플릿 |
| --- | --- | --- |
| 한서연 (검증) | [docs/가이드_한서연_검증.md](docs/가이드_한서연_검증.md) | `templates/리포트시트_템플릿.xlsx`, `templates/테스터명단_템플릿.xlsx` |
| 최서영 (데이터·보고서) | [docs/가이드_최서영_데이터.md](docs/가이드_최서영_데이터.md) | `templates/체력기준표_템플릿.xlsx`, `templates/강좌태그표_템플릿.xlsx`, `templates/시설주소표_템플릿.xlsx`, `templates/오입력_제외기준.md`, `templates/활용사례_보고서_초안.md` |

---

## 지금 되는 것

| 구성 | 상태 |
| --- | --- |
| FastAPI 백엔드 (진단·추천·강좌 조회) | 동작 확인 완료, pytest 10건 통과 |
| 샘플 데이터 (기준표 200행, 강좌 30행) | 실데이터 도착 시 CSV 교체만으로 대체 |
| 브라우저 미리보기 (`/demo`) | 7개 화면 전 흐름 동작 확인 |
| 안드로이드 앱 (Compose 7화면 + Retrofit) | 빌드·에뮬레이터 동작 확인 완료 |
| 카카오 지도 | 실제 렌더링 확인 완료 (에뮬레이터 설정은 `docs/개발_진행_가이드.md` 참고) |

## 1. 백엔드 실행 (개발자용)

```bash
cd server && python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

- 브라우저 미리보기: http://127.0.0.1:8000/demo
- API 문서: http://127.0.0.1:8000/docs
- 상태 확인: http://127.0.0.1:8000/api/v1/health

테스트:

```bash
cd server && python -m pytest -q
```

## 2. 안드로이드 앱 빌드

이 PC에는 JDK 8만 설치돼 있어 Android Gradle Plugin이 동작하지 않는다(JDK 17 필요).
Android SDK도 없다. 아래 스크립트가 둘 다 `C:\dev` 아래에 설치한다(기존 JDK 8은 건드리지 않음).

```bash
powershell -ExecutionPolicy Bypass -File tools\setup_android_sdk.ps1
```

설치 후:

```bash
.\gradlew.bat assembleDebug
```

APK: `app\build\outputs\apk\debug\app-debug.apk`

> Android Studio를 설치할 계획이라면 스크립트 대신 Studio를 쓰는 편이 낫다.
> Studio가 JDK 17·SDK·에뮬레이터를 함께 설치하고, 이후 `gradlew.bat`도 그대로 동작한다.

에뮬레이터에서 호스트 PC의 서버 주소는 `10.0.2.2:8000`이다. 실기기에서 테스트하려면
`local.properties`의 `BASE_URL_DEBUG`를 PC의 LAN IP로 바꾼다.

```
BASE_URL_DEBUG=http://192.168.0.10:8000/
```

## 3. 데이터 교체 (최서영 트랙 산출물 수령 시)

`data/norms.csv`, `data/courses.csv`를 덮어쓰고 서버를 재기동하면 끝이다.
서버는 기동 시 CSV 열 이름·순서를 검증하고, 다르면 적재를 거부하며 오류를 낸다.

스키마는 `CLAUDE.md`의 데이터 계약에 고정돼 있다.

- `norms.csv`: `gender, age_band, item, percentile, value`
  - `gender`: `M` / `F`
  - `age_band`: `20s` / `30s` / `40s` / `50s`
  - `item`: `grip`(상대악력 %) / `sit_up` / `sit_reach` / `shuttle_run` / `one_leg_stand`
  - `percentile`: 하위 기준 백분위. 값이 클수록 우수
- `courses.csv`: `course_id, title, facility, lat, lng, weekday, start_time, sport,
  tag_strength, tag_flex, tag_cardio, tag_balance`

현재 샘플은 `data/_make_sample.py`가 생성한 임시값이다. 실데이터 도착 후에는 쓰지 않는다.

## 4. 오늘 범위 밖 (일정상 주중 진행)

지도 화면, 서버 실배포, B2G 대시보드, 로그인, 예약 연동.
