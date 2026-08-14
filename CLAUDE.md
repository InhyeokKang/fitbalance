# 프로젝트: 직장인 신체 불균형 진단 + 공공 체육 강좌 추천 앱

## 문서
- 서비스 정의·기능·데이터는 docs/ 폴더의 문서가 유일한 기준. 코드 작성 전 반드시 읽을 것.
- docs와 이 파일이 충돌하면 이 파일이 우선.

## 스택 (변경 금지)
- 앱: Kotlin + Jetpack Compose, 단일 모듈, minSdk 26
- 백엔드: Python FastAPI + SQLite, 파일 하나로 시작해 필요 시 분리
- 지도: 카카오맵 Android SDK (키는 local.properties에서 읽기, 코드에 하드코딩 금지)

## 스코프 — 이번 빌드에서 만들지 않는 것
- 로그인/회원가입 없음. 사용자 식별은 기기 로컬 UUID
- 추천은 콘텐츠 기반 코사인 유사도만. 협업 필터링/행렬분해 금지
- 예약은 외부 브라우저 링크로 연결. 예약 연동 구현 금지
- B2G 대시보드는 정적 화면 1장만
- 위 항목을 "확장하면 좋다"는 제안도 하지 말 것

## 데이터 계약 (외부에서 도착 예정, 스키마 고정)
- data/norms.csv: gender, age_band, item, percentile, value
  → 성별·나이대·측정항목별 백분위 기준표. 지금은 샘플을 직접 생성해 개발하고,
    실데이터 도착 시 파일 교체만으로 동작해야 함
- data/courses.csv: course_id, title, facility, lat, lng, weekday, start_time, sport,
  tag_strength, tag_flex, tag_cardio, tag_balance (태그는 0/1)
  → 동일하게 샘플 30행으로 개발
- CSV 스키마를 코드 사정에 맞춰 바꾸지 말 것. 문제가 있으면 바꾸는 대신 보고할 것.

## 검증 (작업 완료 선언 전 필수)
- 백엔드: uvicorn 구동 후 각 엔드포인트 curl 호출 결과를 보여줄 것. pytest 최소 스모크 테스트 유지
- 앱: .\gradlew.bat assembleDebug 성공까지 확인. 에뮬레이터 화면은 내가 직접 확인하고
  문제는 logcat을 붙여넣어 전달하겠음
- 빌드 실패 상태로 턴을 끝내지 말 것

## 작업 방식
- 한 턴에 한 단계만. 다음 단계로 임의로 넘어가지 말 것
- 파일을 새로 만들거나 구조를 바꾸기 전에 무엇을 왜 바꾸는지 한 줄로 먼저 말할 것
- 주석·문자열은 한국어
