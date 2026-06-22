"""
US Census Bureau — American Community Survey (ACS) 5-Year Estimates
Table B25077: Median Value (Dollars) for Owner-Occupied Housing Units
Resolution: ZIP Code Tabulation Area (ZCTA)

No API key required for light usage (<500 requests/day).
Data typically lags ~1.5 years (e.g., 2023 estimates released Dec 2024).
"""
import os
import re
import requests

CENSUS_BASE = "https://api.census.gov/data"
_CENSUS_API_KEY = os.getenv("CENSUS_API_KEY", "")
# Try the 5 most recent years in descending order
_TREND_YEARS = [2024, 2023, 2022, 2021, 2020, 2019]
_HEADERS = {"User-Agent": "SmartRealtyTX/1.0"}


def extract_zip(address: str) -> str | None:
    m = re.search(r"\b(\d{5})\b", address)
    return m.group(1) if m else None


def get_market_context(zip_code: str) -> dict | None:
    """
    Returns median home value trend for the ZIP area over up to 5 years.
    Returns None if no API key is configured, ZIP isn't found, or all requests fail.
    Requires a free Census API key: https://api.census.gov/data/key_signup.html
    Set CENSUS_API_KEY in the environment to enable.
    """
    if not _CENSUS_API_KEY:
        return None

    trend = []
    for year in _TREND_YEARS:
        try:
            resp = requests.get(
                f"{CENSUS_BASE}/{year}/acs/acs5",
                params={
                    "get": "B25077_001E",
                    "for": f"zip code tabulation area:{zip_code}",
                    "key": _CENSUS_API_KEY,
                },
                timeout=10,
                headers=_HEADERS,
            )
            if resp.status_code != 200:
                continue
            rows = resp.json()
            if len(rows) < 2:
                continue
            value = int(rows[1][0])
            if value > 0:
                trend.append({"year": year, "medianHomeValue": value})
            if len(trend) == 5:
                break
        except Exception:
            continue

    if not trend:
        return None

    trend.sort(key=lambda x: x["year"])

    cagr_pct = None
    if len(trend) >= 2:
        v_start = trend[0]["medianHomeValue"]
        v_end   = trend[-1]["medianHomeValue"]
        period  = trend[-1]["year"] - trend[0]["year"]
        if v_start > 0 and period > 0:
            cagr = (v_end / v_start) ** (1 / period) - 1
            cagr_pct = round(cagr * 100, 2)

    return {
        "zipCode": zip_code,
        "medianHomeValueTrend": trend,
        "zipCagrPct": cagr_pct,
    }
