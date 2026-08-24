"""
도로명주소를 실제 좌표로 바꾼다. 결과는 data/address_coords.csv 에 쌓아 둔다.

왜 필요한가
  강좌 데이터에는 위경도가 없고 도로명주소만 있다. 예전에는 places.csv(지역 색인)의
  시군구 대표점을 붙였는데, 강좌 주소에는 동명이 없어서 거의 전부 시군구 대표점으로
  떨어졌다. 실측해 보니 시군구 중심점과 실제 시설의 거리가 중앙값 6.5km,
  90퍼센타일 16.4km 였다. 앱은 퇴근 동선 3km 로 추천하므로 이 오차로는 추천이
  성립하지 않는다. 그래서 주소를 좌표로 직접 바꾼다.

무엇을 쓰나
  카카오 로컬 주소검색. 도로명주소로 찾고, 실패하면 지명(키워드)으로 한 번 더 찾는다.
  키는 local.properties 의 KAKAO_REST_KEY 에서 읽는다. 코드에 하드코딩하지 않는다.

실행
    python tools/geocode_addresses.py                 # data/courses.csv 의 주소를 채운다
    python tools/geocode_addresses.py <csv> [열이름]   # 다른 파일의 특정 열을 채운다

  이미 캐시에 있는 주소는 부르지 않는다. --refresh 를 주면 실패한 것만 다시 시도한다.
"""

from __future__ import annotations

import csv
import json
import math
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "data" / "address_coords.csv"
PROPS = ROOT / "local.properties"

ADDR_URL = "https://dapi.kakao.com/v2/local/search/address.json"
KEYWORD_URL = "https://dapi.kakao.com/v2/local/search/keyword.json"

# 남한 범위. 이 밖으로 나오는 좌표는 받아들이지 않는다.
LAT_MIN, LAT_MAX = 33.0, 38.7
LNG_MIN, LNG_MAX = 125.5, 131.0

# 시설명으로 찾은 좌표가 시군구 기준점에서 이만큼 넘게 떨어지면 다른 지역의
# 같은 이름으로 본다. 시군구 하나가 대략 이 정도 크기다.
MAX_FROM_ANCHOR_KM = 20.0


def read_key() -> str:
    if not PROPS.exists():
        sys.exit("local.properties 가 없습니다.")
    for line in PROPS.read_text(encoding="utf-8").splitlines():
        if line.strip().startswith("KAKAO_REST_KEY"):
            return line.split("=", 1)[1].strip()
    sys.exit("local.properties 에 KAKAO_REST_KEY 가 없습니다.")


def in_korea(lat: float, lng: float) -> bool:
    return LAT_MIN <= lat <= LAT_MAX and LNG_MIN <= lng <= LNG_MAX


def km_between(a: tuple[float, float], b: tuple[float, float]) -> float:
    """이 위도대에서 위도 1도는 약 111km, 경도 1도는 약 88km 다."""
    return math.hypot((a[0] - b[0]) * 111.0, (a[1] - b[1]) * 88.0)


def clean(address: str) -> str:
    """검색에 방해되는 꼬리를 떼어 낸다.

    이 데이터의 주소에는 층·호·건물명이 뒤에 붙어 있는 경우가 많다.
    ("경기도 광명시 철망산로 2 3층 다목적실")
    도로명 + 건물번호까지만 남기면 검색 성공률이 크게 오른다.
    """
    a = (address or "").strip()
    a = a.split("(")[0].strip()               # 괄호 안 법정동·건물명 제거
    a = a.replace(",", " ")
    # 도로명 자체에 숫자가 붙는 형태를 모두 받는다.
    #   "가능로152번길 14"  "논현로 131길 40"  "읍내로12길 43"  "월드컵로 318번길 2"
    # 이걸 못 받으면 "가능로 152" 같은 엉뚱한 곳으로 찍힌다.
    m = re.search(r"^(.*?(?:로|길)(?:\s*\d+번?길)?\s*\d+(?:-\d+)?)", a)
    if m:
        return re.sub(r"\s+", " ", m.group(1))
    return re.sub(r"\s+", " ", a)


def call(url: str, params: dict, key: str) -> dict | None:
    query = urllib.parse.urlencode(params)
    req = urllib.request.Request(
        f"{url}?{query}",
        headers={"Authorization": f"KakaoAK {key}", "User-Agent": "fitbalance/0.1"},
    )
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code in (401, 403):
                sys.exit(f"카카오 키가 거부됐습니다({e.code}). local.properties 의 "
                         f"KAKAO_REST_KEY 를 확인하세요.")
            if e.code == 429:                  # 한도 초과. 잠시 쉬고 다시
                time.sleep(2 * (attempt + 1))
                continue
            return None
        except (urllib.error.URLError, TimeoutError):
            time.sleep(1 + attempt)
    return None


def geocode(address: str, key: str, facility: str = "") -> tuple[float, float, str] | None:
    """(위도, 경도, 방식) 을 돌려준다. 방식은 나중에 정확도를 따질 때 쓴다."""
    target = clean(address)
    if not target:
        return None

    data = call(ADDR_URL, {"query": target, "size": 1}, key)
    docs = (data or {}).get("documents") or []
    if docs:
        d = docs[0]
        lat, lng = float(d["y"]), float(d["x"])
        if in_korea(lat, lng):
            return lat, lng, "address"

    # 도로명으로 못 찾으면 지명으로 한 번 더. 지역명을 앞에 붙여 동명이인을 줄인다.
    parts = target.split()
    hint = " ".join(parts[:2]) if len(parts) >= 2 else target
    data = call(KEYWORD_URL, {"query": target, "size": 1}, key)
    docs = (data or {}).get("documents") or []
    if docs:
        d = docs[0]
        lat, lng = float(d["y"]), float(d["x"])
        if in_korea(lat, lng):
            return lat, lng, "keyword"

    # 여기서부터는 정확도가 떨어지는 수단이다. 먼저 시군구 기준점을 잡아 두고,
    # 시설명으로 찾은 결과가 그 지역 안에 있는지 검사하는 데 쓴다.
    anchor = None
    if hint != target:
        data = call(KEYWORD_URL, {"query": hint, "size": 1}, key)
        docs = (data or {}).get("documents") or []
        if docs:
            la, ln = float(docs[0]["y"]), float(docs[0]["x"])
            if in_korea(la, ln):
                anchor = (la, ln)

    # 주소로 안 되면 시설명으로 찾는다. "수원시평생학습관" 처럼 이름이 고유하면
    # 도로명주소보다 잘 걸린다. 다만 "평촌마을경로당" 같은 흔한 이름은 다른 지역의
    # 같은 이름에 걸리므로, 시군구 기준점에서 멀면 버린다.
    if facility:
        for q in (f"{hint} {facility}", facility):
            data = call(KEYWORD_URL, {"query": q, "size": 1}, key)
            docs = (data or {}).get("documents") or []
            if not docs:
                continue
            lat, lng = float(docs[0]["y"]), float(docs[0]["x"])
            if not in_korea(lat, lng):
                continue
            if anchor and km_between(anchor, (lat, lng)) > MAX_FROM_ANCHOR_KM:
                continue                       # 같은 이름의 다른 지역 시설
            return lat, lng, "facility"

    if anchor:
        return anchor[0], anchor[1], "sigungu"
    return None


def load_cache() -> dict[str, tuple[str, str, str]]:
    if not CACHE.exists():
        return {}
    with open(CACHE, encoding="utf-8-sig", newline="") as f:
        return {r["address"]: (r["lat"], r["lng"], r["method"]) for r in csv.DictReader(f)}


def save_cache(cache: dict[str, tuple[str, str, str]]) -> None:
    CACHE.parent.mkdir(parents=True, exist_ok=True)
    with open(CACHE, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["address", "lat", "lng", "method"])
        for addr in sorted(cache):
            lat, lng, method = cache[addr]
            w.writerow([addr, lat, lng, method])


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    refresh = "--refresh" in sys.argv

    src = Path(args[0]) if args else ROOT / "data" / "courses_address.csv"
    column = args[1] if len(args) > 1 else "address"
    if not src.exists():
        sys.exit(f"{src} 가 없습니다.")

    with open(src, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    if column not in (rows[0] if rows else {}):
        sys.exit(f"{src.name} 에 '{column}' 열이 없습니다. 열: {list(rows[0].keys())}")

    addresses = sorted({(r[column] or "").strip() for r in rows if (r[column] or "").strip()})
    cache = load_cache()
    # --refresh 는 정확히 못 찾은 것(주소 검색 실패)만 다시 시도한다.
    todo = [a for a in addresses
            if a not in cache or (refresh and cache[a][2] != "address")]

    # 주소로 못 찾을 때 쓸 시설명. 같은 주소에 여러 시설이면 첫 번째를 쓴다.
    fac_of: dict[str, str] = {}
    if "facility" in rows[0]:
        for r in rows:
            a = (r[column] or "").strip()
            if a and a not in fac_of:
                fac_of[a] = (r["facility"] or "").strip()

    print(f"주소 {len(addresses):,}개 중 새로 변환할 것 {len(todo):,}개 "
          f"(캐시 {len(addresses) - len(todo):,}개)")

    ok = fail = 0
    for i, addr in enumerate(todo, 1):
        got = geocode(addr, KEY, fac_of.get(addr, ""))
        if got:
            cache[addr] = (f"{got[0]:.6f}", f"{got[1]:.6f}", got[2])
            ok += 1
        else:
            cache[addr] = ("", "", "fail")
            fail += 1
        if i % 50 == 0 or i == len(todo):
            print(f"  {i:,}/{len(todo):,}  성공 {ok:,} 실패 {fail:,}")
            save_cache(cache)
        time.sleep(0.05)                       # 초당 20건. 한도에 한참 못 미친다.

    save_cache(cache)

    hit = sum(1 for a in addresses if cache.get(a, ("",))[0])
    by = {}
    for a in addresses:
        by[cache.get(a, ("", "", "none"))[2]] = by.get(cache.get(a, ("", "", "none"))[2], 0) + 1
    print(f"\n{CACHE.relative_to(ROOT)} 저장")
    print(f"좌표 확보 {hit:,}/{len(addresses):,} ({hit / len(addresses) * 100:.1f}%)")
    print(f"방식별: {by}")

    covered = sum(1 for r in rows if cache.get((r[column] or "").strip(), ("",))[0])
    print(f"행 기준 {covered:,}/{len(rows):,} ({covered / len(rows) * 100:.1f}%)")

    missing = [a for a in addresses if not cache.get(a, ("",))[0]]
    if missing:
        print(f"\n못 찾은 주소 {len(missing)}개 (앞 10개)")
        for a in missing[:10]:
            print(f"  {a}")


KEY = read_key()

if __name__ == "__main__":
    main()
