"""
Harris County Appraisal District (HCAD)
Public records portal: https://public.hcad.org/records/Real.asp
"""
import re
from datetime import datetime
from bs4 import BeautifulSoup
from .base import BaseCADScraper, extract_street

BASE = "https://public.hcad.org/records"
CURRENT_YEAR = datetime.now().year


class HCADScraper(BaseCADScraper):

    def _search_candidates(self, session, street: str) -> list[dict]:
        """Search HCAD by street number+name, return all matching properties."""
        params = {
            "searchtype": "address",
            "addr": street,
            "situs_city": "",
            "situs_zip": "",
            "year": str(CURRENT_YEAR),
            "BegRng": "0",
            "EndRng": "100",
        }
        try:
            resp = session.get(f"{BASE}/Real.asp", params=params, timeout=15)
            resp.raise_for_status()
        except Exception:
            return []

        soup = BeautifulSoup(resp.text, "lxml")
        candidates = []

        for link in soup.select("a[href*='acct=']"):
            href = link["href"]
            match = re.search(r"acct=(\d+)", href)
            if match:
                account = match.group(1)
                label = link.get_text(strip=True)
                url = f"{BASE}/Real.asp?taxyear={CURRENT_YEAR}&sPatternMatch=A&acct={account}"
                # Avoid duplicates
                if not any(c["id"] == account for c in candidates):
                    candidates.append({"id": account, "label": label, "url": url})

        return candidates

    def _fetch_year(self, session, account: str, year: int) -> dict | None:
        params = {
            "taxyear": str(year),
            "sPatternMatch": "A",
            "acct": account,
        }
        try:
            resp = session.get(f"{BASE}/Real.asp", params=params, timeout=15)
            resp.raise_for_status()
        except Exception:
            return None

        soup = BeautifulSoup(resp.text, "lxml")
        return self._parse_values(soup, year)

    def _parse_values(self, soup: BeautifulSoup, year: int) -> dict | None:
        land = self._find_value(soup, ["land value", "land val"])
        improvement = self._find_value(soup, ["improvement value", "impr value", "imprv value"])
        appraised = self._find_value(soup, ["appraised value", "total appraised"])
        taxable = self._find_value(soup, ["taxable value", "net taxable"])

        if appraised is None and land is None:
            return None

        return {
            "year": year,
            "landValue": land,
            "improvementValue": improvement,
            "appraisedValue": appraised,
            "taxableValue": taxable,
        }

    def _find_value(self, soup: BeautifulSoup, labels: list[str]) -> int | None:
        text = soup.get_text(" ", strip=True).lower()
        for label in labels:
            idx = text.find(label)
            if idx != -1:
                snippet = text[idx:idx + 60]
                match = re.search(r"\$?([\d,]+)", snippet)
                if match:
                    return int(match.group(1).replace(",", ""))
        return None
