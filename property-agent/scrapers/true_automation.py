"""
TrueAutomation / Tyler Technologies platform
Used by: Collin, Denton, Fort Bend, Montgomery, Williamson, Bexar, Brazoria,
         Galveston, McLennan, Lubbock, Jefferson, El Paso counties (and more)
Portal: https://propaccess.trueautomation.com/clientdb/?cid=<CID>
"""
import re
from bs4 import BeautifulSoup
from .base import BaseCADScraper

BASE = "https://propaccess.trueautomation.com/clientdb"


class TrueAutomationScraper(BaseCADScraper):

    def __init__(self, cid: int):
        self.cid = cid

    def _search_candidates(self, session, street: str) -> list[dict]:
        """Search TrueAutomation by street number+name, return all matches."""
        params = {
            "cid": self.cid,
            "type": "A",
            "value": street,
        }
        try:
            resp = session.get(f"{BASE}/PropertySearch.aspx", params=params, timeout=15)
            resp.raise_for_status()
        except Exception:
            return []

        soup = BeautifulSoup(resp.text, "lxml")
        candidates = []

        for link in soup.select("a[href*='prop_id=']"):
            href = link["href"]
            match = re.search(r"prop_id=(\d+)", href)
            if match:
                prop_id = match.group(1)
                row = link.find_parent("tr")
                label = row.get_text(" ", strip=True) if row else link.get_text(strip=True)
                url = f"{BASE}/Property.aspx?cid={self.cid}&prop_id={prop_id}"
                if not any(c["id"] == prop_id for c in candidates):
                    candidates.append({"id": prop_id, "label": label, "url": url})

        return candidates

    def get_owner_info(self, session, prop_id: str) -> dict:
        try:
            resp = session.get(f"{BASE}/Property.aspx",
                               params={"cid": self.cid, "prop_id": prop_id},
                               timeout=15)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")
            return self._soup_owner_info(soup)
        except Exception:
            return {"ownerName": None, "mailingAddress": None}

    def _fetch_year(self, session, prop_id: str, year: int) -> dict | None:
        params = {"cid": self.cid, "prop_id": prop_id, "year": year}
        try:
            resp = session.get(f"{BASE}/Property.aspx", params=params, timeout=15)
            resp.raise_for_status()
        except Exception:
            return None

        soup = BeautifulSoup(resp.text, "lxml")
        return self._parse_values(soup, year)

    def _parse_values(self, soup: BeautifulSoup, year: int) -> dict | None:
        land = self._cell_value(soup, ["land", "land value"])
        improvement = self._cell_value(soup, ["improvement", "impr", "building"])
        appraised = self._cell_value(soup, ["appraised", "total appraised value", "total value"])
        taxable = self._cell_value(soup, ["taxable", "net taxable"])

        if appraised is None and land is None:
            return None

        return {
            "year": year,
            "landValue": land,
            "improvementValue": improvement,
            "appraisedValue": appraised,
            "taxableValue": taxable,
        }

    def _cell_value(self, soup: BeautifulSoup, labels: list[str]) -> int | None:
        for td in soup.find_all(["td", "th", "span", "div"]):
            text = td.get_text(strip=True).lower()
            for label in labels:
                if text == label or text.startswith(label):
                    sibling = td.find_next_sibling("td")
                    if sibling:
                        val_text = sibling.get_text(strip=True)
                        match = re.search(r"\$?([\d,]+)", val_text)
                        if match:
                            return int(match.group(1).replace(",", ""))
        return None
