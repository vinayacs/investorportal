"""
Collin County ISD lookup by ZIP code.

Premium percentages reflect market premium vs the county median —
properties in top-tier ISDs consistently trade at higher PPSF.
These are approximate; individual streets can cross ISD boundaries.
"""

# ZIP → (ISD name, premium_pct vs county median)
_ISD_BY_ZIP: dict[str, tuple[str, int]] = {
    # Prosper ISD — highest rated, fastest growing
    "75078": ("Prosper ISD", 12),
    "75056": ("Prosper ISD", 12),  # The Colony / NW Frisco overlap
    # Lovejoy ISD — small, high-performing
    "75009": ("Lovejoy ISD", 11),
    # Frisco ISD
    "75033": ("Frisco ISD", 10),
    "75034": ("Frisco ISD", 10),
    "75035": ("Frisco ISD", 10),
    "75036": ("Frisco ISD", 10),
    # Allen ISD
    "75002": ("Allen ISD", 8),
    "75013": ("Allen ISD", 8),
    # McKinney ISD
    "75069": ("McKinney ISD", 6),
    "75070": ("McKinney ISD", 6),
    "75071": ("McKinney ISD", 6),
    "75072": ("McKinney ISD", 6),
    # Plano ISD
    "75023": ("Plano ISD", 7),
    "75024": ("Plano ISD", 7),
    "75025": ("Plano ISD", 7),
    "75026": ("Plano ISD", 7),
    "75074": ("Plano ISD", 7),
    "75075": ("Plano ISD", 7),
    "75094": ("Plano ISD", 7),
    # Wylie ISD
    "75098": ("Wylie ISD", 5),
    "75048": ("Wylie ISD", 5),
    # Celina ISD
    "75009": ("Celina ISD", 6),  # overlaps Lovejoy for some streets
    # Anna ISD
    "75409": ("Anna ISD", 4),
    # Princeton ISD
    "75407": ("Princeton ISD", 3),
    # Melissa ISD
    "75454": ("Melissa ISD", 5),
    # Farmersville ISD
    "75442": ("Farmersville ISD", 2),
    # Blue Ridge ISD
    "75424": ("Blue Ridge ISD", 2),
    # Gunter ISD
    "75058": ("Gunter ISD", 4),
    # Howe ISD / Van Alstyne ISD
    "75069": ("McKinney ISD", 6),
    "75495": ("Van Alstyne ISD", 4),
}


def get_school_district(zip_code: str) -> dict | None:
    """
    Return ISD info for a Collin County ZIP, or None if not found.
    """
    if not zip_code:
        return None
    entry = _ISD_BY_ZIP.get(zip_code.strip())
    if not entry:
        return None
    name, premium_pct = entry
    return {
        "name":       name,
        "premiumPct": premium_pct,
        "zip":        zip_code.strip(),
    }
