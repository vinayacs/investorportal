"""
Dallas Central Appraisal District (DCAD)
Search portal: https://www.dcad.org/account-search
"""
import re
from bs4 import BeautifulSoup
from .base import BaseCADScraper

BASE = "https://www.dcad.org"


class DCADScraper(BaseCADScraper):

    def _search_candidates(self, session, street: str) -> list[dict]:
        """Search DCAD by street number+name, return all matches."""
        session.headers.update({"Referer": f"{BASE}/account-search"})

        # Try JSON API first
        candidates = self._json_search(session, street)
        if candidates:
            return candidates

        # Fallback to HTML scrape
        return self._html_search(session, street)

    def _json_search(self, session, street: str) -> list[dict]:
        try:
            resp = session.get(
                f"{BASE}/api/property/search",
                params={"q": street, "type": "address"},
                timeout=15,
            )
            if not resp.ok:
                return []
            data = resp.json()
            if not isinstance(data, list):
                return []
            candidates = []
            for item in data:
                account = str(item.get("accountNumber") or item.get("account") or "")
                label = item.get("situsAddress") or item.get("address") or street
                if account:
                    candidates.append({
                        "id": account,
                        "label": label,
                        "url": f"{BASE}/property/{account}",
                    })
            return candidates
        except Exception:
            return []

    def _html_search(self, session, street: str) -> list[dict]:
        try:
            resp = session.get(f"{BASE}/account-search", params={"s": street}, timeout=15)
            resp.raise_for_status()
        except Exception:
            return []

        soup = BeautifulSoup(resp.text, "lxml")
        candidates = []
        for link in soup.select("a[href*='/property/']"):
            href = link["href"]
            match = re.search(r"/property/(\w+)", href)
            if match:
                account = match.group(1)
                label = link.get_text(strip=True)
                if not any(c["id"] == account for c in candidates):
                    candidates.append({
                        "id": account,
                        "label": label,
                        "url": f"{BASE}/property/{account}",
                    })
        return candidates

    def _fetch_year(self, session, account: str, year: int) -> dict | None:
        # Try JSON endpoint first
        try:
            resp = session.get(
                f"{BASE}/api/property/{account}/valuation",
                params={"year": year}, timeout=15,
            )
            if resp.ok:
                data = resp.json()
                result = self._parse_json(data, year)
                if result:
                    return result
        except Exception:
            pass

        # Fallback HTML
        try:
            resp = session.get(f"{BASE}/property/{account}", params={"year": year}, timeout=15)
            resp.raise_for_status()
            soup = BeautifulSoup(resp.text, "lxml")
            return self._parse_html(soup, year)
        except Exception:
            return None

    def _parse_json(self, data: dict, year: int) -> dict | None:
        def get(keys):
            for k in keys:
                if k in data:
                    raw = str(data[k]).replace(",", "").replace("$", "")
                    try:
                        return int(float(raw))
                    except ValueError:
                        pass
            return None

        land = get(["landValue", "land_value", "land"])
        improvement = get(["improvementValue", "impr_value", "improvement"])
        appraised = get(["appraisedValue", "appraised_value", "totalValue", "total_value"])
        taxable = get(["taxableValue", "taxable_value", "netTaxable"])

        if appraised is None and land is None:
            return None
        return {"year": year, "landValue": land, "improvementValue": improvement,
                "appraisedValue": appraised, "taxableValue": taxable}

    def _parse_html(self, soup: BeautifulSoup, year: int) -> dict | None:
        text = soup.get_text(" ", strip=True).lower()

        def extract(labels):
            for label in labels:
                idx = text.find(label)
                if idx != -1:
                    snippet = text[idx:idx + 60]
                    m = re.search(r"\$?([\d,]+)", snippet)
                    if m:
                        return int(m.group(1).replace(",", ""))
            return None

        land = extract(["land value", "land val"])
        improvement = extract(["improvement value", "impr value"])
        appraised = extract(["appraised value", "total appraised", "total value"])
        taxable = extract(["taxable value", "net taxable"])

        if appraised is None and land is None:
            return None
        return {"year": year, "landValue": land, "improvementValue": improvement,
                "appraisedValue": appraised, "taxableValue": taxable}
