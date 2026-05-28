"""
Denton Central Appraisal District (DentonCAD)
Portal: https://www.dentoncad.com

Pure Playwright UI automation — drives the browser exactly like a human:
  1. Open property-search, select "Property Address" type, type the street
     name (no house number), press Enter, wait for ag-Grid rows to render.
  2. Collect every row whose streetPrimary contains the house number.
     Paginate through all pages to gather all matches.
  3. If 1 match → navigate to its detail page and read Value History.
     If multiple matches → return an ambiguous response listing the cities
     so the caller can ask the user to specify one.
     If 0 matches → return not-found.

Uses a 2560-wide viewport so ag-Grid virtualises all columns into view,
including streetPrimary and city which are hidden at narrower widths.
"""
import re
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
from bs4 import BeautifulSoup
from .base import BaseCADScraper, extract_street

PORTAL = "https://www.dentoncad.com"
MAX_PAGES = 20   # safety cap on pagination


class DentonCADScraper(BaseCADScraper):

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        county = county_info["county"]
        street = extract_street(address)           # e.g. "2304 Pinehurst Dr"
        house_num = re.match(r"(\d+)", street)
        house_num = house_num.group(1) if house_num else ""
        # Full street name without house number: "Pinehurst Dr"
        street_name = re.sub(r"^\d+\s*", "", street).strip()
        # Strip common street type suffixes for the CAD search so we get the
        # broadest result set — "Pinehurst" finds all pages, "Pinehurst Dr"
        # can miss properties that appear on later pages.
        search_term = re.sub(
            r"\s+\b(dr|st|ave|ln|blvd|ct|way|pl|cir|trl|pkwy|rd|fwy|hwy"
            r"|drive|street|avenue|lane|boulevard|court|place|circle"
            r"|trail|parkway|road|freeway|highway)\s*$",
            "", street_name, flags=re.IGNORECASE
        ).strip()

        # Try to extract the city from the full address for auto-disambiguation
        # e.g. "2004 Autumn Trl, Denton, TX 76210" → "denton"
        input_city = self._extract_city(address)

        try:
            with sync_playwright() as pw:
                browser = pw.chromium.launch(
                    headless=True,
                    args=["--no-sandbox", "--disable-dev-shm-usage"],
                )
                # Wide viewport so ag-Grid renders ALL columns, including
                # streetPrimary and city which are virtualised away at narrower widths.
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/124.0.0.0 Safari/537.36"
                    ),
                    viewport={"width": 2560, "height": 900},
                )
                page = ctx.new_page()

                matches = self._collect_all_matches(page, search_term, house_num)
                browser.close()

        except Exception as exc:
            return self._not_found(county, address, input_city,
                f"Denton CAD scraper error: {exc}")

        if not matches:
            return self._not_found(county, address, input_city,
                f"No property found matching '{street}' in Denton CAD.")

        # Auto-disambiguate if the input address contains a city
        if input_city and len(matches) > 1:
            filtered = [m for m in matches
                        if input_city in m["city"].lower()]
            if filtered:
                matches = filtered

        if len(matches) > 1:
            cities = sorted({m["city"] for m in matches})
            return self._ambiguous_response(county, address, cities)

        # Exactly one match — fetch its Value History
        match = matches[0]
        prop_url = f"{PORTAL}/property-detail/{match['pid']}"
        cad_city = match["city"].title()

        try:
            with sync_playwright() as pw:
                browser = pw.chromium.launch(
                    headless=True,
                    args=["--no-sandbox", "--disable-dev-shm-usage"],
                )
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/124.0.0.0 Safari/537.36"
                    ),
                    viewport={"width": 1280, "height": 900},
                )
                page = ctx.new_page()
                years, owner_info = self._read_value_history(page, prop_url)
                browser.close()
        except Exception as exc:
            return self._not_found(county, address, cad_city,
                f"Denton CAD detail-page error: {exc}")

        if not years:
            return self._not_found(county, address, cad_city,
                "Property found but Value History could not be read.")

        return self._build_response(
            county, address, cad_city,
            match["pid"], match["address"], years, prop_url,
            owner_info.get("ownerName"), owner_info.get("mailingAddress"),
        )

    # ------------------------------------------------------------------
    # Collect all rows across pages that match the house number
    # ------------------------------------------------------------------

    def _collect_all_matches(self, page, street_name: str,
                              house_num: str) -> list[dict]:
        """
        Run the search and walk every page of results.
        Returns a list of {"pid", "address", "city"} dicts for every row
        whose streetPrimary cell contains house_num as a whole word.
        """
        try:
            page.goto(f"{PORTAL}/property-search", timeout=30_000)
            page.wait_for_load_state("networkidle", timeout=20_000)
        except PWTimeout:
            return []

        self._select_search_type(page, "Property Address")

        try:
            inp = page.locator("input[placeholder*='Address']").first
            inp.click()
            inp.fill(street_name)
            inp.press("Enter")
        except Exception:
            return []

        # Wait up to 30s for first batch of rows
        try:
            page.wait_for_selector("div[row-index]", timeout=30_000)
        except PWTimeout:
            pass
        page.wait_for_timeout(1500)

        matches = []
        for _ in range(MAX_PAGES):
            matches.extend(self._read_page_matches(page, house_num))
            if not self._go_next_page(page):
                break

        return matches

    def _read_page_matches(self, page, house_num: str) -> list[dict]:
        """Return all rows on the current page whose address contains house_num."""
        rows = page.query_selector_all("div[row-index]")
        results = []
        for row in rows:
            addr_cell = row.query_selector("[col-id='streetPrimary']")
            addr_text = (addr_cell.inner_text().strip()
                         if addr_cell else row.inner_text().strip())

            if not addr_text or "No Rows" in addr_text:
                continue

            if house_num and not re.search(
                    r"\b" + re.escape(house_num) + r"\b", addr_text):
                continue

            pid_cell = row.query_selector("[col-id='pid'] button, [col-id='pid']")
            pid = pid_cell.inner_text().strip() if pid_cell else None
            if not pid:
                continue

            city_cell = row.query_selector("[col-id='city']")
            city = city_cell.inner_text().strip() if city_cell else ""

            results.append({"pid": pid, "address": addr_text, "city": city})
        return results

    def _go_next_page(self, page) -> bool:
        """Click the Next Page button. Returns True if navigation happened."""
        try:
            btn = page.query_selector("[aria-label='Go to next page']")
            if btn is None:
                return False
            if (btn.get_attribute("disabled") is not None
                    or btn.get_attribute("aria-disabled") == "true"):
                return False
            btn.click()
            page.wait_for_timeout(1500)
            return True
        except Exception:
            return False

    @staticmethod
    def _extract_city(address: str) -> str:
        """Return lowercase city string from 'Street, City, TX ZIP', or ''."""
        parts = [p.strip() for p in address.split(",")]
        if len(parts) >= 2:
            city_part = parts[1]
            city_part = re.sub(r"\b(TX|Texas)\b.*$", "", city_part,
                               flags=re.IGNORECASE).strip()
            if city_part:
                return city_part.lower()
        return ""

    @staticmethod
    def _select_search_type(page, option_text: str):
        try:
            selects = page.query_selector_all("div.MuiSelect-select")
            if not selects:
                return
            selects[0].click()
            page.wait_for_timeout(500)
            for opt in page.query_selector_all("li[role='option']"):
                if opt.inner_text().strip() == option_text:
                    opt.click()
                    page.wait_for_timeout(400)
                    return
            page.keyboard.press("Escape")
        except Exception:
            pass

    # ------------------------------------------------------------------
    # Step 2 — detail page Value History
    # ------------------------------------------------------------------

    def _read_value_history(self, page, url: str) -> tuple[list[dict], dict]:
        try:
            page.goto(url, timeout=30_000)
            page.wait_for_load_state("networkidle", timeout=20_000)
        except PWTimeout:
            return [], {}

        try:
            page.wait_for_selector("text=Value History", timeout=15_000)
        except PWTimeout:
            pass

        page.wait_for_timeout(2000)

        soup = BeautifulSoup(page.content(), "lxml")
        return self._parse_value_history(soup), self._soup_owner_info(soup)

    # ------------------------------------------------------------------
    # Parse the Value History section
    # ------------------------------------------------------------------

    def _parse_value_history(self, soup: BeautifulSoup) -> list[dict]:
        heading = None
        for tag in soup.find_all(string=re.compile(r"value history", re.IGNORECASE)):
            heading = tag
            break

        table = None
        if heading:
            parent = heading.find_parent()
            for _ in range(6):
                if parent is None:
                    break
                table = parent.find("table")
                if table:
                    break
                parent = parent.find_next_sibling() or parent.parent

        if not table:
            for t in soup.find_all("table"):
                txt = t.get_text(" ", strip=True).lower()
                if "year" in txt and ("appraised" in txt or "value" in txt):
                    table = t
                    break

        if table:
            rows = self._parse_table(table)
            if rows:
                return rows

        return self._text_scan(soup)

    def _parse_table(self, table) -> list[dict]:
        rows = table.find_all("tr")
        if not rows:
            return []

        headers = [th.get_text(strip=True).lower()
                   for th in rows[0].find_all(["th", "td"])]

        def col(*keywords):
            for kw in keywords:
                for i, h in enumerate(headers):
                    if kw in h:
                        return i
            return None

        year_col = col("year")
        land_col = col("land")
        impr_col = col("improvement", "impr", "building")
        appr_col = col("net appraised", "appraised", "market value", "total value")
        tax_col  = col("taxable", "net taxable")

        if year_col is None or appr_col is None:
            return []

        def cell_int(cells, idx):
            if idx is None or idx >= len(cells):
                return None
            raw = cells[idx].get_text(strip=True).replace(",", "").replace("$", "")
            m = re.search(r"\d+", raw)
            return int(m.group()) if m else None

        results = []
        for row in rows[1:]:
            cells = row.find_all(["td", "th"])
            year = cell_int(cells, year_col)
            if not year or year < 2000:
                continue
            results.append({
                "year": year,
                "landValue":        cell_int(cells, land_col),
                "improvementValue": cell_int(cells, impr_col),
                "appraisedValue":   cell_int(cells, appr_col),
                "taxableValue":     cell_int(cells, tax_col),
            })
        return results

    def _text_scan(self, soup: BeautifulSoup) -> list[dict]:
        text = soup.get_text(" ", strip=True).lower()
        results = []
        for year in range(2025, 2019, -1):
            idx = text.find(str(year))
            if idx == -1:
                continue
            snippet = text[idx:idx + 400]

            def grab(*labels):
                for label in labels:
                    li = snippet.find(label)
                    if li != -1:
                        m = re.search(r"\$?([\d,]+)", snippet[li:li + 80])
                        if m:
                            return int(m.group(1).replace(",", ""))
                return None

            appr = grab("net appraised", "appraised value", "market value")
            if appr:
                results.append({
                    "year": year,
                    "landValue":        grab("land value", "land"),
                    "improvementValue": grab("improvement", "impr"),
                    "appraisedValue":   appr,
                    "taxableValue":     grab("taxable value", "net taxable"),
                })
        return results

    # ------------------------------------------------------------------
    # Response helpers
    # ------------------------------------------------------------------

    def _ambiguous_response(self, county: str, address: str,
                             cities: list[str]) -> dict:
        return {
            "supported": True,
            "county": county,
            "city": "",
            "address": address,
            "propertyId": None,
            "propertyAddress": None,
            "cadUrl": "",
            "ownerName": None,
            "mailingAddress": None,
            "years": [],
            "requiresCity": True,
            "choices": cities,
            "message": (
                f"Multiple properties found for this address in different cities: "
                f"{', '.join(cities)}. Please specify the city."
            ),
        }

    def get_appraisal_by_id(self, prop_id: str, county_info: dict) -> dict:
        county = county_info["county"]
        prop_url = f"{PORTAL}/property-detail/{prop_id}"
        try:
            with sync_playwright() as pw:
                browser = pw.chromium.launch(
                    headless=True,
                    args=["--no-sandbox", "--disable-dev-shm-usage"],
                )
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/124.0.0.0 Safari/537.36"
                    ),
                    viewport={"width": 1280, "height": 900},
                )
                page = ctx.new_page()
                years, owner_info = self._read_value_history(page, prop_url)
                browser.close()
        except Exception as exc:
            return self._not_found(county, "", "",
                f"Denton CAD detail-page error: {exc}")

        if not years:
            return self._not_found(county, "", "",
                f"No Value History found for property ID {prop_id} in Denton CAD.")

        return self._build_response(county, "", "", prop_id, prop_id, years, prop_url,
                                    owner_info.get("ownerName"),
                                    owner_info.get("mailingAddress"))

    # Required by ABC
    def _search_candidates(self, session, street: str) -> list[dict]:
        return []

    def _fetch_year(self, session, prop_id: str, year: int) -> dict | None:
        return None
