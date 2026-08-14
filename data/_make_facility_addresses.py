"""
시설 주소 참조표(facility_addresses.csv)를 만든다.

왜 별도 파일인가
  courses.csv 스키마는 계약으로 고정돼 있어 address 열을 덧붙일 수 없다.
  그래서 시설명을 키로 하는 별도 참조표를 두고 서버가 조인한다.
  courses.csv가 실데이터로 교체돼도 이 파일만 갱신하면 된다.

주소 값에 대하여
  지금 값은 시설명에서 확실하게 도출되는 자치구 단위까지만 채운 샘플이다.
  도로명·건물번호는 지어내지 않는다. 실제 전체 주소가 확보되면
  address 열을 그대로 덮어쓰면 되고, 서버·앱 코드는 고칠 필요가 없다.
  (address는 자유 문자열로 다루므로 길이·형식 제약이 없다.)

실행: python data/_make_facility_addresses.py
"""

import csv
from pathlib import Path

DATA_DIR = Path(__file__).parent

# 시설명 → 자치구. 시설명에 자치구가 들어 있지 않은 곳만 별도로 적는다.
EXCEPTIONS = {
    "여의도한강공원 체육관": "영등포구",
    "동대문구체육관": "동대문구",
}

SEOUL_GU = [
    "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
    "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
    "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구",
]


def district_of(facility: str) -> str | None:
    """시설명에서 자치구를 뽑는다. 못 뽑으면 None."""
    if facility in EXCEPTIONS:
        return EXCEPTIONS[facility]
    # 긴 이름부터 검사해야 '중구'가 '중랑구'를 가로채지 않는다.
    for gu in sorted(SEOUL_GU, key=len, reverse=True):
        if gu in facility:
            return gu
    return None


def main() -> None:
    courses_path = DATA_DIR / "courses.csv"
    if not courses_path.exists():
        raise SystemExit("data/courses.csv가 없습니다. _make_sample.py를 먼저 실행하세요.")

    with open(courses_path, encoding="utf-8-sig", newline="") as f:
        facilities = sorted({r["facility"] for r in csv.DictReader(f)})

    rows, unknown = [], []
    for name in facilities:
        gu = district_of(name)
        if gu is None:
            unknown.append(name)
            rows.append((name, ""))  # 빈 값이면 앱은 시설명만 보여준다
        else:
            rows.append((name, f"서울특별시 {gu}"))

    out = DATA_DIR / "facility_addresses.csv"
    with open(out, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["facility", "address"])
        w.writerows(rows)

    print(f"{out.name}: {len(rows)}행 생성")
    if unknown:
        print(f"자치구를 못 찾은 시설 {len(unknown)}건 (주소 빈 값): {', '.join(unknown)}")


if __name__ == "__main__":
    main()
