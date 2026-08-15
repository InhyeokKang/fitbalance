"""
받아온 국민체력100 원자료에서 백분위 기준표(data/norms.csv)를 만든다.

실행 순서
    python tools/fetch_kspo.py     # 원자료 표본 받기
    python tools/build_norms.py    # 이 스크립트

하는 일
  1. data/raw/kspo_readable.csv 를 읽는다
  2. 오입력으로 보이는 값을 제외한다 (기준은 아래 EXCLUDE 표, 보고서에 그대로 쓴다)
  3. 성별 × 5세 단위 나이대 × 측정항목별로 백분위 지점을 계산한다
  4. data/norms.csv 로 저장하고, 제외 통계를 화면에 찍는다

이 스크립트가 만든 표는 **초안**이다. 제외 기준이 타당한지, 표본 수가 충분한지는
최서영 트랙에서 검증한다. 표본이 적은 칸은 경고로 표시한다.
"""

from __future__ import annotations

import csv
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "data" / "raw" / "kspo_readable.csv"
OUT = ROOT / "data" / "norms.csv"

PERCENTILES = [10, 25, 50, 75, 90]

AGE_BANDS = [
    (19, 24, "19-24"), (25, 29, "25-29"), (30, 34, "30-34"), (35, 39, "35-39"),
    (40, 44, "40-44"), (45, 49, "45-49"), (50, 54, "50-54"), (55, 59, "55-59"),
    (60, 64, "60-64"),
]

# 우리 item 코드 -> 원자료의 한글 열 이름
ITEM_COLUMN = {
    "grip": "상대악력_퍼센트",
    "sit_up": "교차윗몸일으키기_회",
    "sit_reach": "앉아윗몸앞으로굽히기_cm",
    "shuttle_run": "왕복오래달리기_회",
    "standing_jump": "제자리멀리뛰기_cm",
}

# 오입력 제외 기준. 이 범위를 벗어나면 측정 오류로 보고 버린다.
# 근거: 성인기 인증기준에서 나올 수 있는 값의 바깥 경계를 넉넉히 잡았다.
EXCLUDE = {
    "grip": (10.0, 120.0),          # 상대악력 %
    "sit_up": (0.0, 100.0),         # 회/60초
    "sit_reach": (-30.0, 45.0),     # cm, 음수 정상
    "shuttle_run": (0.0, 150.0),    # 회
    "standing_jump": (30.0, 350.0), # cm
}

# 표본이 이보다 적으면 신뢰하기 어려워 경고한다
MIN_SAMPLES = 100

# 이 연월 이후 자료만 쓴다.
# 공단이 2022년에 체력측정 인증기준을 개정했고, 그 이전 자료는 기준이 달라 섞으면 안 된다.
# (원자료 자체는 2011년부터 있다.)
SINCE_YM = "202201"


def age_band(age: int) -> str | None:
    for low, high, name in AGE_BANDS:
        if low <= age <= high:
            return name
    return None


def percentile(sorted_values: list[float], p: float) -> float:
    """선형 보간 백분위. numpy 없이 계산한다."""
    if not sorted_values:
        raise ValueError("빈 목록")
    if len(sorted_values) == 1:
        return sorted_values[0]
    pos = (len(sorted_values) - 1) * (p / 100.0)
    low = int(pos)
    high = min(low + 1, len(sorted_values) - 1)
    frac = pos - low
    return sorted_values[low] * (1 - frac) + sorted_values[high] * frac


def main() -> None:
    if not SRC.exists():
        sys.exit(f"{SRC.relative_to(ROOT)} 가 없습니다. 먼저 tools/fetch_kspo.py 를 실행하세요.")

    # (gender, band, item) -> 값 목록
    buckets: dict[tuple[str, str, str], list[float]] = defaultdict(list)
    total = 0
    dropped = defaultdict(int)

    with open(SRC, encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            total += 1

            ym = (row.get("측정연월") or "").strip()
            if len(ym) < 6 or ym < SINCE_YM:
                dropped[f"{SINCE_YM} 이전 측정"] += 1
                continue

            gender = (row.get("성별") or "").strip().upper()
            if gender not in ("M", "F"):
                dropped["성별 없음/이상"] += 1
                continue

            try:
                age = int(float(row.get("나이") or ""))
            except ValueError:
                dropped["나이 없음/이상"] += 1
                continue
            band = age_band(age)
            if band is None:
                dropped["성인기(19~64세) 범위 밖"] += 1
                continue

            for item, column in ITEM_COLUMN.items():
                raw = (row.get(column) or "").strip()
                if not raw:
                    dropped[f"{item} 결측"] += 1
                    continue
                try:
                    value = float(raw)
                except ValueError:
                    dropped[f"{item} 숫자 아님"] += 1
                    continue
                low, high = EXCLUDE[item]
                if not (low <= value <= high):
                    dropped[f"{item} 범위 밖"] += 1
                    continue
                buckets[(gender, band, item)].append(value)

    if not buckets:
        sys.exit("쓸 수 있는 값이 하나도 없습니다. 원자료를 확인하세요.")

    rows = []
    thin = []
    for gender in ("M", "F"):
        for _, _, band in AGE_BANDS:
            for item in ITEM_COLUMN:
                values = sorted(buckets.get((gender, band, item), []))
                if not values:
                    thin.append((gender, band, item, 0))
                    continue
                if len(values) < MIN_SAMPLES:
                    thin.append((gender, band, item, len(values)))
                for p in PERCENTILES:
                    rows.append([gender, band, item, p, round(percentile(values, p), 1)])

    with open(OUT, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["gender", "age_band", "item", "percentile", "value"])
        w.writerows(rows)

    used = sum(len(v) for v in buckets.values())
    print(f"원자료 {total:,}행에서 측정값 {used:,}개를 썼습니다.")
    print(f"{OUT.relative_to(ROOT)}: {len(rows)}행 생성 (기대 450행)\n")

    print("제외 내역")
    for reason, count in sorted(dropped.items(), key=lambda kv: -kv[1]):
        print(f"  {reason:<28} {count:>8,}")

    if thin:
        print(f"\n표본 {MIN_SAMPLES}건 미만인 칸 {len(thin)}개 — 검증이 필요합니다")
        for gender, band, item, n in thin[:15]:
            print(f"  {gender} {band} {item:<14} {n}건")
        if len(thin) > 15:
            print(f"  ... 외 {len(thin) - 15}개")
    else:
        print(f"\n모든 칸이 표본 {MIN_SAMPLES}건 이상입니다.")


if __name__ == "__main__":
    main()
