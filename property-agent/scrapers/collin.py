"""
Collin Central Appraisal District — file-based lookup (no scraping).

Data source: two CSV files exported from CCAD open-data portal, pre-built into
a SQLite database via build_collin_db.py.

DB location is read from env var COLLIN_DB_PATH (default: /data/collin/collin.db).
"""
import os
import re
import sqlite3
import sys

from .base import BaseCADScraper

# comps_engine and school_districts live one level up (property-agent root)
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from comps_engine import get_comps
from school_districts import get_school_district

PORTAL = "https://esearch.collincad.org"
DB_PATH = os.environ.get("COLLIN_DB_PATH", "/data/collin/collin.db")

YEARS = [2021, 2022, 2023, 2024, 2025]

# USPS abbreviation map for normalizing user input to address_key format
_ABBREVS = [
    (r"\bROAD\b",       "RD"),
    (r"\bSTREET\b",     "ST"),
    (r"\bAVENUE\b",     "AVE"),
    (r"\bLANE\b",       "LN"),
    (r"\bDRIVE\b",      "DR"),
    (r"\bBOULEVARD\b",  "BLVD"),
    (r"\bCOURT\b",      "CT"),
    (r"\bPLACE\b",      "PL"),
    (r"\bCIRCLE\b",     "CIR"),
    (r"\bTRAIL\b",      "TRL"),
    (r"\bPARKWAY\b",    "PKWY"),
    (r"\bFREEWAY\b",    "FWY"),
    (r"\bHIGHWAY\b",    "HWY"),
    (r"\bEXPRESSWAY\b", "EXPY"),
    (r"\bNORTH\b",      "N"),
    (r"\bSOUTH\b",      "S"),
    (r"\bEAST\b",       "E"),
    (r"\bWEST\b",       "W"),
    (r"\bTEXAS\b",      "TX"),
]


class CollinCADScraper(BaseCADScraper):

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        county = county_info["county"]
        print(f"[CollinCAD] lookup address={address!r}", flush=True)

        try:
            conn = self._open_db()
        except FileNotFoundError as exc:
            return self._not_found(county, address, "",
                f"Collin County database not found. Run build_collin_db.py first. ({exc})")

        try:
            norm = self._normalize_key(address)
            print(f"[CollinCAD] normalized key={norm!r}", flush=True)
            row = self._lookup(conn, norm)
            if not row:
                return self._not_found(county, address, "",
                    f"No property found for '{address}' in Collin County database.")
            print(f"[CollinCAD] found propid={row['propid']}", flush=True)
            return self._build_from_row(row, county, address, conn)
        finally:
            conn.close()

    def get_appraisal_by_id(self, prop_id: str, county_info: dict) -> dict:
        county = county_info["county"]
        print(f"[CollinCAD] lookup by id={prop_id!r}", flush=True)

        try:
            conn = self._open_db()
        except FileNotFoundError as exc:
            return self._not_found(county, "", "",
                f"Collin County database not found. Run build_collin_db.py first. ({exc})")

        try:
            conn.row_factory = sqlite3.Row
            cur = conn.cursor()
            cur.execute("SELECT * FROM value_history WHERE propid = ? LIMIT 1", (prop_id,))
            row = cur.fetchone()
            if not row:
                return self._not_found(county, "", "",
                    f"No property found for ID {prop_id} in Collin County database.")
            return self._build_from_row(row, county, "", conn)
        finally:
            conn.close()

    # ------------------------------------------------------------------
    # Lookup helpers
    # ------------------------------------------------------------------

    def _open_db(self) -> sqlite3.Connection:
        if not os.path.exists(DB_PATH):
            raise FileNotFoundError(DB_PATH)
        # immutable=1: tells SQLite this DB won't change, so it won't try to
        # create WAL/SHM sidecar files — required when mounted read-only in Docker.
        conn = sqlite3.connect(f"file:{DB_PATH}?immutable=1", uri=True)
        conn.row_factory = sqlite3.Row
        return conn

    def _normalize_key(self, address: str) -> str:
        """Convert user input to address_key format: UPPERCASE, no commas, single spaces."""
        key = address.upper()
        key = re.sub(r",", " ", key)
        key = re.sub(r"\.", "", key)
        for pattern, abbrev in _ABBREVS:
            key = re.sub(pattern, abbrev, key)
        key = re.sub(r"\s+", " ", key).strip()
        return key

    def _lookup(self, conn: sqlite3.Connection, norm_key: str):
        cur = conn.cursor()

        # 1. Exact match
        cur.execute(
            "SELECT * FROM value_history WHERE address_key = ? LIMIT 1",
            (norm_key,)
        )
        row = cur.fetchone()
        if row:
            print(f"[CollinCAD] exact match", flush=True)
            return row

        # 2. Prefix match — drop trailing zip code (user may omit it)
        no_zip = re.sub(r"\s+\d{5}(-\d{4})?\s*$", "", norm_key).strip()
        if no_zip != norm_key:
            cur.execute(
                "SELECT * FROM value_history WHERE address_key LIKE ? "
                "ORDER BY length(address_key) LIMIT 1",
                (no_zip + " %",)
            )
            row = cur.fetchone()
            if row:
                print(f"[CollinCAD] prefix (no-zip) match", flush=True)
                return row

        # 3. Fuzzy — house number + first word of street, then filter by city
        m = re.match(r"^(\d+\s+\S+)", norm_key)
        if m:
            prefix = m.group(1)
            cur.execute(
                "SELECT * FROM value_history WHERE address_key LIKE ? "
                "ORDER BY length(address_key) LIMIT 10",
                (prefix + "%",)
            )
            rows = cur.fetchall()
            if rows:
                city_m = re.search(r"\b([A-Z]+(?:\s+[A-Z]+)*)\s+TX\b", norm_key)
                if city_m and len(rows) > 1:
                    city = city_m.group(1)
                    for r in rows:
                        if city in r["address_key"]:
                            print(f"[CollinCAD] fuzzy match (city filter)", flush=True)
                            return r
                print(f"[CollinCAD] fuzzy match (first result)", flush=True)
                return rows[0]

        return None

    # ------------------------------------------------------------------
    # Response builders
    # ------------------------------------------------------------------

    def _build_from_row(self, row, county: str, input_address: str, conn=None) -> dict:
        years = self._row_to_years(row)
        situs = (row["situsconcat"] or row["situsconcatshort"] or "").strip(" ,")
        prop_id = row["propid"] or ""
        cad_url = f"{PORTAL}/Property/View/{prop_id}" if prop_id else PORTAL
        owner_name = self._clean(row["ownername"])
        mailing = self._build_mailing(row)
        city = self._city_from_situs(situs)

        result = self._build_response(
            county, input_address or situs, city,
            prop_id, situs, years, cad_url,
            owner_name, mailing,
        )

        # Phase 1: comps + school district (Collin SQLite only)
        if conn is not None:
            subject_info = self._lookup_address_details(conn, row["address_key"], prop_id)
            zip_code = subject_info.get("zip") or ""
            sqft     = subject_info.get("sqft") or 0.0
            beds     = subject_info.get("beds")

            result["propertyDetails"] = {
                "sqft":      int(sqft) if sqft else None,
                "beds":      beds,
                "baths":     subject_info.get("baths"),
                "yearBuilt": subject_info.get("yearBuilt"),
            }

            comps_data = get_comps(conn, row["address_key"], zip_code, sqft, beds) if sqft > 0 else None
            result["comps"]               = comps_data["comps"] if comps_data else []
            result["residentialEstimate"] = comps_data["residentialEstimate"] if comps_data else None
            result["schoolDistrict"]      = get_school_district(zip_code)

        return result

    def _lookup_address_details(self, conn: sqlite3.Connection, address_key: str, prop_id: str) -> dict:
        """Pull subject sqft/beds/baths/zip from address_lookup for comps engine."""
        cur = conn.cursor()
        cur.execute("""
            SELECT living_are, beds, baths, yr_blt, situs_zip
            FROM address_lookup
            WHERE address_key = ? OR PROP_ID = ?
            LIMIT 1
        """, (address_key, prop_id))
        r = cur.fetchone()
        if not r:
            return {}
        return {
            "sqft":      _to_float(r["living_are"]),
            "beds":      _to_int(r["beds"]),
            "baths":     _to_float(r["baths"]) or None,
            "yearBuilt": _to_int(r["yr_blt"]),
            "zip":       (r["situs_zip"] or "").strip(),
        }

    def _row_to_years(self, row) -> list[dict]:
        results = []
        for yr in YEARS:
            appraised = _to_int(row[f"{yr}_currvalappraised"])
            if not appraised:
                continue
            results.append({
                "year":             yr,
                "landValue":        _to_int(row[f"{yr}_currvalland"]),
                "improvementValue": _to_int(row[f"{yr}_currvalimprv"]),
                "appraisedValue":   appraised,
                "taxableValue":     _to_int(row[f"{yr}_currvalassessed"]),
            })
        return results

    def _build_mailing(self, row) -> str | None:
        parts = [
            self._clean(row["owneraddrline1"]),
            self._clean(row["owneraddrline2"]),
            self._clean(row["owneraddrcity"]),
            self._clean(row["owneraddrstate"]),
            self._clean(row["owneraddrzip"]),
        ]
        parts = [p for p in parts if p]
        return " ".join(parts) if parts else None

    @staticmethod
    def _clean(val) -> str | None:
        if val is None:
            return None
        s = str(val).strip()
        return s if s else None

    @staticmethod
    def _city_from_situs(situs: str) -> str:
        m = re.search(r",\s*([^,]+?)\s*,?\s*TX\b", situs, re.IGNORECASE)
        return m.group(1).strip().title() if m else ""

    # ------------------------------------------------------------------
    # Abstract method stubs (base class requires them)
    # ------------------------------------------------------------------

    def _search_candidates(self, session, street: str) -> list[dict]:
        return []

    def _fetch_year(self, session, prop_id: str, year: int):
        return None


def _to_int(val) -> int | None:
    if val is None or val == "":
        return None
    try:
        f = float(str(val).replace(",", "").replace("$", ""))
        return int(f) if f else None
    except (ValueError, TypeError):
        return None


def _to_float(val) -> float:
    try:
        return float(str(val).replace(",", "").replace("$", "")) if val not in (None, "", "0") else 0.0
    except (ValueError, TypeError):
        return 0.0
