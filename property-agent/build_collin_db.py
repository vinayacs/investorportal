#!/usr/bin/env python3
"""
One-time build script: converts Collin County CAD CSV exports into a SQLite
lookup database for the property agent.

Usage:
    python build_collin_db.py <csv_dir> [output_db_path]

<csv_dir> must contain:
    collin_county_property_owner_value_history_2021_2025.csv
    collin_county_address_appraisal_lookup.csv

Output defaults to <csv_dir>/collin.db
"""
import csv
import os
import sqlite3
import sys

VH_FILE = "collin_county_property_owner_value_history_2021_2025.csv"
AL_FILE = "collin_county_address_appraisal_lookup.csv"

YEARS = [2021, 2022, 2023, 2024, 2025]

VH_COLS = [
    "address_key", "propid", "geoid",
    "situsconcat", "situsconcatshort",
    "ownername", "ownernameaddtl",
    "owneraddrline1", "owneraddrline2",
    "owneraddrcity", "owneraddrstate", "owneraddrzip",
    "imprvyearbuilt", "imprvmainarea",
    "market_change_2021_to_2025_pct",
    "projected_market_2026_4pct",
    "projected_market_2027_4pct",
    "projected_market_2028_4pct",
]
for _yr in YEARS:
    VH_COLS += [
        f"{_yr}_currvalmarket",
        f"{_yr}_currvalappraised",
        f"{_yr}_currvalassessed",
        f"{_yr}_currvalimprv",
        f"{_yr}_currvalland",
    ]

AL_COLS = [
    "address_key", "PROP_ID", "situs_disp", "situs_city", "situs_zip",
    "curr_marke", "curr_appra", "curr_asses", "curr_imprv", "curr_land_",
    "living_are", "yr_blt", "beds", "baths",
]


def _create_tables(conn: sqlite3.Connection) -> None:
    vh_defs = ", ".join(f'"{c}" TEXT' for c in VH_COLS)
    conn.execute(f"""
        CREATE TABLE IF NOT EXISTS value_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            {vh_defs}
        )
    """)

    al_defs = ", ".join(f'"{c}" TEXT' for c in AL_COLS)
    conn.execute(f"""
        CREATE TABLE IF NOT EXISTS address_lookup (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            {al_defs}
        )
    """)
    conn.commit()


def _import_csv(conn: sqlite3.Connection, path: str, table: str,
                cols: list[str], batch_size: int = 5000) -> int:
    print(f"  Importing {os.path.basename(path)} → {table} ...", flush=True)
    quoted = ", ".join(f'"{c}"' for c in cols)
    placeholders = ", ".join("?" * len(cols))
    sql = f"INSERT INTO {table} ({quoted}) VALUES ({placeholders})"

    total = 0
    batch: list[list] = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            batch.append([row.get(c, "") for c in cols])
            if len(batch) >= batch_size:
                conn.executemany(sql, batch)
                total += len(batch)
                batch = []
                if total % 100_000 == 0:
                    print(f"    {total:,} rows ...", flush=True)
        if batch:
            conn.executemany(sql, batch)
            total += len(batch)

    conn.commit()
    print(f"    Done: {total:,} rows", flush=True)
    return total


def main() -> None:
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <csv_dir> [output_db_path]")
        sys.exit(1)

    csv_dir = sys.argv[1]
    db_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(csv_dir, "collin.db")

    vh_path = os.path.join(csv_dir, VH_FILE)
    al_path = os.path.join(csv_dir, AL_FILE)
    for p in [vh_path, al_path]:
        if not os.path.exists(p):
            print(f"ERROR: not found: {p}")
            sys.exit(1)

    if os.path.exists(db_path):
        os.remove(db_path)
        print(f"Removed existing {db_path}", flush=True)

    print(f"Building {db_path} ...", flush=True)
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA cache_size=-64000")

    _create_tables(conn)
    _import_csv(conn, vh_path, "value_history", VH_COLS)
    _import_csv(conn, al_path, "address_lookup", AL_COLS)

    print("Creating indexes ...", flush=True)
    conn.execute("CREATE INDEX idx_vh_key ON value_history(address_key)")
    conn.execute("CREATE INDEX idx_vh_propid ON value_history(propid)")
    conn.execute("CREATE INDEX idx_al_key ON address_lookup(address_key)")
    conn.commit()
    conn.close()

    size_mb = os.path.getsize(db_path) / 1024 / 1024
    print(f"Done! {db_path} ({size_mb:.0f} MB)", flush=True)


if __name__ == "__main__":
    main()
