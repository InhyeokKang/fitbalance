"""
전국평생학습강좌표준데이터에서 체육 강좌를 받아 온다.

왜 이 데이터인가
  강좌 시간표를 전국 단위로 주는 공공데이터를 계속 찾다가 이걸 발견했다.
  평생학습 강좌라 전부가 체육은 아니지만, 요가·필라테스·수영·탁구·배드민턴 같은
  생활체육 강좌가 상당수 들어 있고 무엇보다 **운영요일과 교육시작시각이 다 채워져 있다.**
  시설명·도로명주소·운영기관·전화번호·홈페이지까지 있어 우리 스키마에 그대로 맞는다.

    출처   공공데이터포털 「전국평생학습강좌표준데이터」
    API    https://api.data.go.kr/openapi/tn_pubr_public_lftm_lrn_lctre_api

준비
  이 데이터셋에 **활용신청**이 따로 필요하다. 자동승인이라 신청 즉시 쓸 수 있고,
  인증키는 국민체력100과 같은 것을 쓴다(계정당 하나).

      https://www.data.go.kr/data/15013110/standard.do

실행
    python tools/fetch_lessons.py                 # API 로 전량 수집
    python tools/fetch_lessons.py --csv 파일경로   # 내려받은 CSV 로 대신 (활용신청 전 확인용)

결과
    data/raw/lessons.csv   체육으로 판정된 강좌만. 원본 열을 그대로 둔다.

다음 단계
    python tools/build_courses_from_lessons.py    # -> data/courses.csv
"""

from __future__ import annotations

import csv
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "data" / "raw" / "lessons.csv"
ENDPOINT = "https://api.data.go.kr/openapi/tn_pubr_public_lftm_lrn_lctre_api"
PAGE_SIZE = 1000
MAX_PAGES = 200

# 강좌명·강좌내용에서 체육 강좌를 골라내는 말들.
# 넉넉히 잡고 뒤에서 종목으로 다시 거른다. 여기서 놓치면 복구할 방법이 없다.
SPORT_WORDS = (
    "요가|필라테스|수영|아쿠아|수중|헬스|웨이트|근력|코어|스트레칭|체조|에어로빅|줌바"
    "|밸리댄스|라인댄스|댄스|배드민턴|탁구|테니스|정구|골프|파크골프|볼링|당구|족구"
    "|게이트볼|축구|풋살|농구|배구|야구|검도|태권도|합기도|주짓수|복싱|무에타이|킥복싱"
    "|클라이밍|암벽|스피닝|사이클|자전거|등산|트레킹|걷기|국학기공|기공|태극권|단전"
    "|필드하키|승마|카약|조정|요트|스케이트|빙상|인라인|롤러|체력|운동|스포츠|체육"
)
SPORT_RE = re.compile(SPORT_WORDS)

# 체육처럼 보이지만 실제로는 아닌 것들. 먼저 걸러낸다.
EXCLUDE_RE = re.compile(r"운동회|자원봉사|운동장 대관|스포츠마케팅|스포츠경영|운동처방사 자격")

# 우리가 쓰는 열만 남긴다. API 응답의 영문 키 -> 한글 이름
FIELDS = {
    "lctreNm": "강좌명",
    "oprtDay": "운영요일",
    "eduTme": "교육시작시각",
    "eduEndTme": "교육종료시각",
    "eduPlace": "교육장소",
    "eduPlaceRdnmadr": "교육장도로명주소",
    "operInstitutionNm": "운영기관명",
    "institutionNm": "운영기관명2",
    "phoneNumber": "전화번호",
    "homepageUrl": "홈페이지",
    "lctreCn": "강좌내용",
    "eduTrgetSe": "교육대상",
    "eduMthSe": "교육방법",
    "eduBgngDe": "교육시작일자",
    "eduEndDe": "교육종료일자",
    "lctreCo": "정원",
    "lctreCost": "수강료",
}


def read_key() -> str:
    props = ROOT / "local.properties"
    if props.exists():
        for line in props.read_text(encoding="utf-8").splitlines():
            if line.startswith("DATA_GO_KR_KEY="):
                key = line.split("=", 1)[1].strip()
                if key:
                    return urllib.parse.unquote(key) if "%" in key else key
    sys.exit("local.properties 에 DATA_GO_KR_KEY 가 없습니다.")


def is_sport(row: dict) -> bool:
    text = f"{row.get('강좌명', '')} {row.get('강좌내용', '')}"
    return bool(SPORT_RE.search(text)) and not EXCLUDE_RE.search(text)


def call(key: str, page: int) -> tuple[int, list[dict]]:
    query = urllib.parse.urlencode({
        "serviceKey": key, "pageNo": page, "numOfRows": PAGE_SIZE, "type": "json",
    })
    req = urllib.request.Request(f"{ENDPOINT}?{query}", headers={"User-Agent": "fitbalance/0.1"})
    with urllib.request.urlopen(req, timeout=60) as res:
        payload = json.loads(res.read().decode("utf-8"))
    body = payload.get("response", {}).get("body", {})
    items = body.get("items") or []
    if isinstance(items, dict):
        items = items.get("item") or []
    return int(body.get("totalCount") or 0), items


def normalize(raw: dict) -> dict:
    """API 응답(영문 키)이든 CSV(한글 열)든 같은 모양으로 맞춘다."""
    if any(k in raw for k in FIELDS):
        return {ko: str(raw.get(en, "")).strip() for en, ko in FIELDS.items()}
    return {ko: str(raw.get(ko, "")).strip() for ko in FIELDS.values()}


def from_api() -> list[dict]:
    key = read_key()
    try:
        total, _ = call(key, 1)
    except urllib.error.HTTPError as e:
        if e.code in (401, 403):
            sys.exit(
                "\n이 데이터셋에 활용신청이 안 되어 있습니다(자동승인, 즉시 사용 가능).\n"
                "  https://www.data.go.kr/data/15013110/standard.do\n"
                "신청 후 다시 실행하세요. 인증키는 기존 것을 그대로 씁니다.\n"
            )
        raise

    last_page = min(MAX_PAGES, (total + PAGE_SIZE - 1) // PAGE_SIZE)
    print(f"전체 {total:,}건 ({last_page}쪽)")

    rows: list[dict] = []
    for page in range(1, last_page + 1):
        try:
            _, got = call(key, page)
        except (urllib.error.URLError, TimeoutError) as e:
            print(f"  [건너뜀] {page}쪽: {e}")
            continue
        if not got:
            break
        rows += [normalize(r) for r in got]
        if page % 10 == 0 or page == last_page:
            print(f"  {page}/{last_page}쪽  누적 {len(rows):,}건")
        time.sleep(0.15)
    return rows


def from_csv(path: Path) -> list[dict]:
    with open(path, encoding="utf-8-sig", newline="") as f:
        return [normalize(r) for r in csv.DictReader(f)]


def main() -> None:
    if "--csv" in sys.argv:
        path = Path(sys.argv[sys.argv.index("--csv") + 1])
        if not path.exists():
            sys.exit(f"{path} 를 찾을 수 없습니다.")
        rows = from_csv(path)
        print(f"CSV 에서 {len(rows):,}건을 읽었습니다.")
    else:
        rows = from_api()

    if not rows:
        sys.exit("받은 데이터가 없습니다.")

    sports = [r for r in rows if is_sport(r)]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(FIELDS.values()))
        w.writeheader()
        w.writerows(sports)

    def filled(key: str) -> float:
        return sum(1 for r in sports if r.get(key)) / max(len(sports), 1) * 100

    print(f"\n체육 강좌 {len(sports):,}건 / 전체 {len(rows):,}건 ({len(sports)/max(len(rows),1)*100:.1f}%)")
    print(f"{OUT.relative_to(ROOT)} 저장\n")
    print("핵심 열이 얼마나 채워졌나")
    for k in ["강좌명", "운영요일", "교육시작시각", "교육장소", "교육장도로명주소"]:
        print(f"  {k:<14} {filled(k):5.1f}%")
    print("\n다음: python tools/build_courses_from_lessons.py")


if __name__ == "__main__":
    main()
