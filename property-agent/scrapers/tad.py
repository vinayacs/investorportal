"""
Tarrant Appraisal District (TAD)
Search portal: https://www.tad.org
"""
import re
from bs4 import BeautifulSoup
from .base import BaseCADScraper

BASE = "https://www.tad.org"


class TADScraper(BaseCADScraper):

    def _search_candidates(self, session, street: str) -> list[dict]:
        """Search TAD by street number+name, return all matches."""
        session.headers.update({"Referer": f"{BASE}/"})
        try:
            resp = session.get(
                f"{BASE}/property_search.php",
                params={"type": "A", "value": street},
                timeout=15,
            )
            resp.raise_for_status()
        except Exception:
            return []

        soup = BeautifulSoup(resp.text, "lxml")
        candidates = []

        for link in soup.select("a[href*='PROP_ID='], a[href*='prop_id=']"):
            href = link["href"]
            match = re.search(r"PROP_ID=(\w+)", href, re.IGNORECASE)
            if match:
                prop_id = match.group(1)
                row = link.find_parent("tr")
                label = row.get_text(" ", strip=True) if row else link.get_text(strip=True)
                url = f"{BASE}/property_detail.php?PROP_ID={prop_id}"
                if not any(c["id"] == prop_id for c in candidates):
                    candidates.append({"id": prop_id, "label": label, "url": url})

        return candidates

    def get_owner_info(self, session, prop_id: str) -> dict:
        try:
            resp = session.get(f"{BASE}/property_detail.php",
                               params={"PROP_ID": prop_id}, timeout=15)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")
            return self._soup_owner_info(soup)
        except Exception:
            return {"ownerName": None, "mailingAddress": None}

    def _fetch_year(self, session, prop_id: str, year: int) -> dict | None:
        try:
            resp = session.get(
                f"{BASE}/property_detail.php",
                params={"PROP_ID": prop_id, "taxyear": year},
                timeout=15,
            )
            resp.raise_for_status()
        except Exception:
            return None

        soup = BeautifulSoup(resp.text, "lxml")
        return self._parse_values(soup, year)

    def _parse_values(self, soup: BeautifulSoup, year: int) -> dict | None:
        text = soup.get_text(" ", strip=True).lower()

        def extract(labels):
            for label in labels:
                idx = text.find(label)
                if idx != -1:
                    snippet = text[idx:idx + 80]
                    m = re.search(r"\$?([\d,]+)", snippet)
                    if m:
                        return int(m.group(1).replace(",", ""))
            return None

        land = extract(["land", "land value"])
        improvement = extract(["improvement", "impr", "building value"])
        appraised = extract(["appraised value", "total appraised", "market value"])
        taxable = extract(["taxable value", "net taxable"])

        if appraised is None and land is None:
            return None
        return {"year": year, "landValue": land, "improvementValue": improvement,
                "appraisedValue": appraised, "taxableValue": taxable}
