"""
Comparable sales engine — Tier 1 of the Residential Valuation Engine.

Queries the Collin County address_lookup SQLite table to find properties
with similar size/beds in the same ZIP, then estimates subject value as
median price-per-sqft × subject sqft.
"""
import sqlite3
import statistics


def get_comps(
    conn: sqlite3.Connection,
    subject_key: str,
    zip_code: str,
    sqft: float,
    beds: int | None,
    limit: int = 6,
) -> dict | None:
    """
    Return comparable sales + a residential value estimate.

    Returns None if there is insufficient data (< 2 comps or no sqft).
    """
    if not zip_code or sqft <= 0:
        return None

    sqft_min = sqft * 0.75
    sqft_max = sqft * 1.25

    cur = conn.cursor()

    # Primary: same ZIP, sqft ±25%, beds ±1
    rows = _query_comps(cur, subject_key, zip_code, sqft, sqft_min, sqft_max, beds, limit)

    # Fallback: relax beds filter if we got fewer than 3
    if len(rows) < 3 and beds is not None:
        rows = _query_comps(cur, subject_key, zip_code, sqft, sqft_min, sqft_max, None, limit)

    # Fallback: widen sqft range to ±40% if still thin
    if len(rows) < 3:
        rows = _query_comps(cur, subject_key, zip_code, sqft, sqft * 0.60, sqft * 1.40, None, limit)

    if len(rows) < 2:
        return None

    comps = []
    ppsf_values = []
    for row in rows:
        comp_sqft = _float(row["living_are"])
        comp_val  = _float(row["curr_marke"])
        if not comp_sqft or not comp_val:
            continue
        ppsf = round(comp_val / comp_sqft)
        ppsf_values.append(ppsf)
        comps.append({
            "address":     (row["situs_disp"] or row["address_key"]).strip(" ,"),
            "sqft":        int(comp_sqft),
            "beds":        _int(row["beds"]),
            "baths":       _float(row["baths"]),
            "yearBuilt":   _int(row["yr_blt"]),
            "marketValue": int(comp_val),
            "pricePerSqft": ppsf,
        })

    if len(ppsf_values) < 2:
        return None

    median_ppsf   = round(statistics.median(ppsf_values))
    base_estimate = round(median_ppsf * sqft / 1000) * 1000  # round to nearest $1k

    confidence = "high" if len(comps) >= 5 else ("medium" if len(comps) >= 3 else "low")

    return {
        "comps": comps[:limit],
        "residentialEstimate": {
            "estimatedValue": base_estimate,
            "medianPpsf":     median_ppsf,
            "subjectSqft":    int(sqft),
            "compsUsed":      len(comps),
            "confidence":     confidence,
        },
    }


def _query_comps(cur, subject_key, zip_code, sqft, sqft_min, sqft_max, beds, limit):
    if beds is not None:
        cur.execute("""
            SELECT address_key, situs_disp, living_are, beds, baths, yr_blt, curr_marke
            FROM address_lookup
            WHERE situs_zip = ?
              AND address_key != ?
              AND living_are IS NOT NULL AND living_are != '' AND living_are != '0'
              AND curr_marke IS NOT NULL AND curr_marke != '' AND CAST(curr_marke AS REAL) > 50000
              AND CAST(living_are AS REAL) BETWEEN ? AND ?
              AND beds IS NOT NULL AND beds != ''
              AND ABS(CAST(COALESCE(NULLIF(beds,''),0) AS REAL) - ?) <= 1
            ORDER BY ABS(CAST(living_are AS REAL) - ?)
            LIMIT ?
        """, (zip_code, subject_key, sqft_min, sqft_max, beds, sqft, limit))
    else:
        cur.execute("""
            SELECT address_key, situs_disp, living_are, beds, baths, yr_blt, curr_marke
            FROM address_lookup
            WHERE situs_zip = ?
              AND address_key != ?
              AND living_are IS NOT NULL AND living_are != '' AND living_are != '0'
              AND curr_marke IS NOT NULL AND curr_marke != '' AND CAST(curr_marke AS REAL) > 50000
              AND CAST(living_are AS REAL) BETWEEN ? AND ?
            ORDER BY ABS(CAST(living_are AS REAL) - ?)
            LIMIT ?
        """, (zip_code, subject_key, sqft_min, sqft_max, sqft, limit))
    return cur.fetchall()


def _float(val) -> float:
    try:
        return float(str(val).replace(",", "")) if val not in (None, "", "0") else 0.0
    except (ValueError, TypeError):
        return 0.0


def _int(val) -> int | None:
    f = _float(val)
    return int(f) if f else None
