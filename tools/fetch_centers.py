"""
국민체력100 체력인증센터 목록을 받아 data/centers.csv 로 저장한다.

왜 필요한가
  앱이 "가까운 체력인증센터 찾기"를 보여주려면 센터 목록이 있어야 한다.
  공공데이터포털의 체력인증센터 데이터셋은 활용신청이 필요하고 좌표도 없어서,
  공식 홈페이지가 쓰는 목록 API를 그대로 호출한다.

주의
  - 이 엔드포인트는 시도(sdCd)별로 조회하며, 페이지 파라미터는 pageNo 다.
    (pageIndex 나 recordCountPerPage 는 무시되고 한 번에 5건씩만 준다.)
  - 응답에 위경도(pointX/pointY)는 비어 있다. mapHtml 안에 카카오 구 좌표계(KATEC)
    값이 들어 있지만 WGS84 변환이 필요해 쓰지 않는다. 앱은 시도·시군구로 찾는다.

실행: python tools/fetch_centers.py
"""

from __future__ import annotations

import csv
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ENDPOINT = "https://nfa.kspo.or.kr/intro/centerListJs.kspo"
OUT = Path(__file__).resolve().parent.parent / "data" / "centers.csv"

# 홈페이지 시도 선택 목록과 같은 코드
SIDO_CODES = [
    "11", "12", "26", "27", "28", "30", "31", "36",
    "41", "42", "43", "44", "45", "47", "48", "50",
]

HEADERS = {
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent": "Mozilla/5.0",
    "Referer": "https://nfa.kspo.or.kr/intro/centerList.kspo",
    "X-Requested-With": "XMLHttpRequest",
}


def post(sido: str, page: int) -> dict:
    body = urllib.parse.urlencode({
        "pageNo": page, "viewPageNo": page, "sdCd": sido,
    }).encode()
    req = urllib.request.Request(ENDPOINT, data=body, headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=20) as res:
        return json.loads(res.read().decode("utf-8"))


def clean(value: str | None) -> str:
    return re.sub(r"\s+", " ", (value or "")).strip()


def main() -> None:
    centers: dict[str, dict] = {}

    for sido in SIDO_CODES:
        total = None
        seen_here = 0
        for page in range(1, 21):
            try:
                data = post(sido, page)
            except (urllib.error.URLError, TimeoutError) as e:
                print(f"[실패] 시도 {sido} {page}쪽: {e}")
                break

            rows = data.get("centerList") or []
            if not rows:
                break
            if total is None:
                total = int(rows[0].get("totCount") or 0)

            for row in rows:
                centers[clean(row.get("centerCd"))] = row
            seen_here += len(rows)
            if total and seen_here >= total:
                break

    if not centers:
        sys.exit("센터를 하나도 받지 못했습니다. 사이트 구조가 바뀌었을 수 있습니다.")

    rows = []
    for c in centers.values():
        address = clean(c.get("addr1"))
        detail = clean(c.get("addr2"))
        if detail:
            address = f"{address} {detail}"
        rows.append({
            "center_code": clean(c.get("centerCd")),
            "sido": clean(c.get("sdNm")),
            "sigungu": clean(c.get("sggNm")),
            "center_name": clean(c.get("centerNm")),
            "address": address,
            "tel": clean(c.get("tel")),
        })
    rows.sort(key=lambda r: (r["sido"], r["sigungu"], r["center_name"]))

    OUT.parent.mkdir(exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["center_code", "sido", "sigungu",
                                          "center_name", "address", "tel"])
        w.writeheader()
        w.writerows(rows)

    by_sido: dict[str, int] = {}
    for r in rows:
        by_sido[r["sido"]] = by_sido.get(r["sido"], 0) + 1
    print(f"{OUT.name}: {len(rows)}개소 저장")
    print("  " + ", ".join(f"{k} {v}" for k, v in sorted(by_sido.items())))


if __name__ == "__main__":
    main()
