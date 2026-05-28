"""
Collin Central Appraisal District (CollinCAD)
Portal: https://esearch.collincad.org

Uses Firefox via Playwright — Cloudflare Bot Management on this portal blocks
headless Chromium but passes Firefox without issue.

Flow (single browser session):
  1. Load home page (passes Cloudflare), move mouse (satisfies hasMovedMouse).
  2. Click "By Address" tab, fill StreetNumber + StreetName, click Search.
  3. Site navigates to /search/result?keywords=... — parse matching rows.
     Each row has onclick="redirectToPropertyDetails('PID','YEAR','OID', ...)".
  4. Filter rows by house number. Auto-disambiguate on city if input has one.
  5. Navigate to /Property/View/{PID}?ownerId={OID} (same session, already has
     Cloudflare cookies) and parse the "Property Roll Value History" table.
"""
import re
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
from bs4 import BeautifulSoup
from .base import BaseCADScraper, extract_street

PORTAL = "https://esearch.collincad.org"


class CollinCADScraper(BaseCADScraper):

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        county = county_info["county"]
        street = extract_street(address)
        house_num = re.match(r"(\d+)", street)
        house_num = house_num.group(1) if house_num else ""
        street_name = re.sub(r"^\d+\s*", "", street).strip()
        search_name = re.sub(
            r"\s+\b(dr|st|ave|ln|blvd|ct|way|pl|cir|trl|pkwy|rd|fwy|hwy"
            r"|drive|street|avenue|lane|boulevard|court|place|circle"
            r"|trail|parkway|road|freeway|highway)\s*$",
            "", street_name, flags=re.IGNORECASE
        ).strip()
        input_city = self._extract_city(address)

        try:
            with sync_playwright() as pw:
                browser = pw.firefox.launch(
                    headless=True,
                    firefox_user_prefs={
                        'dom.webdriver.enabled': False,
                        'useAutomationExtension': False,
                        'privacy.resistFingerprinting': False,
                    }
                )
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) "
                        "Gecko/20100101 Firefox/125.0"
                    ),
                    viewport={"width": 1920, "height": 1080},
                    locale='en-US',
                    timezone_id='America/Chicago',
                )
                page = ctx.new_page()
                page.add_init_script(
                    'Object.defineProperty(navigator, "webdriver", {get: () => undefined})'
                )
                page.on("dialog", lambda d: d.accept())

                # Step 1: search (uses the Cloudflare session warmed up on home page)
                matches = self._search(page, house_num, search_name)

                if not matches:
                    browser.close()
                    return self._not_found(county, address, input_city,
                        f"No property found matching '{street}' in Collin CAD.")

                if input_city and len(matches) > 1:
                    filtered = [m for m in matches if input_city in m["city"].lower()]
                    if filtered:
                        matches = filtered

                if len(matches) > 1:
                    browser.close()
                    cities = sorted({m["city"] for m in matches})
                    return self._ambiguous_response(county, address, cities)

                # Step 2: detail page in the SAME browser session — no re-auth needed
                match = matches[0]
                prop_url = (f"{PORTAL}/Property/View/{match['pid']}"
                            f"?ownerId={match['oid']}")
                years, owner_info = self._read_detail(page, prop_url)
                browser.close()

        except Exception as exc:
            return self._not_found(county, address, input_city,
                f"Collin CAD scraper error: {exc}")

        cad_city = self._title_city(match["address"])

        if not years:
            return self._not_found(county, address, cad_city,
                "Property found but Value History could not be read.")

        return self._build_response(
            county, address, cad_city,
            match["pid"], match["address"], years, prop_url,
            owner_info.get("ownerName"), owner_info.get("mailingAddress"),
        )

    # ------------------------------------------------------------------
    # Step 1: search
    # ------------------------------------------------------------------

    def _search(self, page, house_num: str, search_name: str) -> list[dict]:
        # Load home page so Cloudflare sets cookies
        try:
            page.goto(PORTAL, timeout=40000)
            page.wait_for_load_state("networkidle", timeout=30000)
        except PWTimeout:
            return []
        page.wait_for_timeout(2000)

        # Move mouse so the site's hasMovedMouse check passes
        page.mouse.move(300, 300)
        page.mouse.move(500, 400)
        page.wait_for_timeout(600)

        # Click "By Address" tab
        try:
            page.locator('a:has-text("By Address")').first.click()
            page.wait_for_timeout(800)
        except Exception:
            return []

        # Fill street number and name only
        page.fill("#StreetNumber", house_num)
        page.fill("#StreetName", search_name)
        page.wait_for_timeout(400)

        # Click Search button
        clicked = False
        for btn in page.query_selector_all("button"):
            if btn.is_visible() and btn.inner_text().strip() == "Search":
                btn.click()
                clicked = True
                break
        if not clicked:
            return []

        # Wait for navigation to search results
        try:
            page.wait_for_url("**/search/result**", timeout=30000)
        except PWTimeout:
            return []
        page.wait_for_timeout(2000)

        return self._parse_results(page, house_num)

    def _parse_results(self, page, house_num: str) -> list[dict]:
        soup = BeautifulSoup(page.content(), "lxml")
        results = []

        # Each matching row has onclick="redirectToPropertyDetails('PID','YEAR','OID',...)"
        pattern = re.compile(
            r"redirectToPropertyDetails\('(\d+)','(\d+)','(\d+)'", re.IGNORECASE
        )
        seen = set()
        for el in soup.find_all(onclick=pattern):
            m = pattern.search(el.get("onclick", ""))
            if not m:
                continue
            pid, year, oid = m.group(1), m.group(2), m.group(3)
            if pid in seen:
                continue
            seen.add(pid)

            row = el.find_parent("tr") or el.find_parent("li") or el.parent
            row_text = row.get_text(" ", strip=True) if row else el.get_text(strip=True)

            if house_num and not re.search(
                    r"\b" + re.escape(house_num) + r"\b", row_text):
                continue

            # Extract city from situs address (e.g. "5754 ALPENROSE AVE, FRISCO TX 75035")
            city = ""
            city_m = re.search(r",\s*([A-Z][A-Z ]+)\s+TX\b", row_text)
            if city_m:
                city = city_m.group(1).strip().title()

            # Extract situs address anchored to the house number
            addr_m = re.search(
                re.escape(house_num) + r"\s+\S.*?TX\s+\d{5}",
                row_text, re.IGNORECASE
            )
            situs = addr_m.group(0).strip() if addr_m else row_text[:80]

            results.append({
                "pid": pid, "oid": oid,
                "address": situs, "city": city,
            })

        return results

    # ------------------------------------------------------------------
    # Step 2: detail page
    # ------------------------------------------------------------------

    def _read_detail(self, page, url: str) -> tuple[list[dict], dict]:
        """Navigate to a property detail URL and parse value history.
        Assumes the page already has a valid Cloudflare session (warmed up
        by _search or by a prior home-page load in get_appraisal_by_id)."""
        try:
            page.goto(url, timeout=40000)
        except PWTimeout:
            pass

        try:
            page.wait_for_selector(
                "text=Property Roll Value History, text=Value History",
                timeout=30000
            )
        except PWTimeout:
            pass
        page.wait_for_timeout(2000)

        soup = BeautifulSoup(page.content(), "lxml")
        return self._parse_value_history(soup), self._soup_owner_info(soup)

    def _warm_and_read_detail(self, page, url: str) -> tuple[list[dict], dict]:
        """Load the portal home page first to get Cloudflare cookies, then
        navigate to the detail URL. Used by get_appraisal_by_id which starts
        with a cold browser session."""
        try:
            page.goto(PORTAL, timeout=40000)
            page.wait_for_load_state("networkidle", timeout=30000)
        except PWTimeout:
            pass
        page.wait_for_timeout(1500)
        page.mouse.move(400, 300)
        page.wait_for_timeout(300)
        return self._read_detail(page, url)

    def _parse_value_history(self, soup: BeautifulSoup) -> list[dict]:
        # Find "Property Roll Value History" heading, then the table after it
        table = None
        for heading in soup.find_all(string=re.compile(
                r"property roll value history|value history", re.IGNORECASE)):
            parent = heading.find_parent()
            for _ in range(8):
                if parent is None:
                    break
                table = parent.find("table")
                if table:
                    break
                parent = parent.find_next_sibling() or parent.parent
            if table:
                break

        if not table:
            for t in soup.find_all("table"):
                txt = t.get_text(" ", strip=True).lower()
                if "year" in txt and "appraised" in txt and "improvement" in txt:
                    table = t
                    break

        if not table:
            return []

        rows = table.find_all("tr")
        if not rows:
            return []

        headers = [th.get_text(strip=True).lower()
                   for th in rows[0].find_all(["th", "td"])]

        def col(*kws):
            for kw in kws:
                for i, h in enumerate(headers):
                    if kw in h:
                        return i
            return None

        year_col = col("year")
        impr_col = col("improvement")
        land_col = col("land")
        appr_col = col("appraised")
        tax_col  = col("assessed")

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
                "year":             year,
                "landValue":        cell_int(cells, land_col),
                "improvementValue": cell_int(cells, impr_col),
                "appraisedValue":   cell_int(cells, appr_col),
                "taxableValue":     cell_int(cells, tax_col),
            })
        return results

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _extract_city(address: str) -> str:
        parts = [p.strip() for p in address.split(",")]
        if len(parts) >= 2:
            city_part = re.sub(r"\b(TX|Texas)\b.*$", "", parts[1],
                               flags=re.IGNORECASE).strip()
            if city_part:
                return city_part.lower()
        return ""

    @staticmethod
    def _title_city(address: str) -> str:
        m = re.search(r",\s*([A-Z][A-Z ]+)\s+TX\b", address)
        return m.group(1).strip().title() if m else ""

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
                f"Multiple properties found in different cities: "
                f"{', '.join(cities)}. Please specify the city."
            ),
        }

    def get_appraisal_by_id(self, prop_id: str, county_info: dict) -> dict:
        county = county_info["county"]
        prop_url = f"{PORTAL}/Property/View/{prop_id}"
        try:
            with sync_playwright() as pw:
                browser = pw.firefox.launch(
                    headless=True,
                    firefox_user_prefs={
                        'dom.webdriver.enabled': False,
                        'useAutomationExtension': False,
                        'privacy.resistFingerprinting': False,
                    }
                )
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) "
                        "Gecko/20100101 Firefox/125.0"
                    ),
                    viewport={"width": 1920, "height": 1080},
                    locale='en-US',
                    timezone_id='America/Chicago',
                )
                page = ctx.new_page()
                page.add_init_script(
                    'Object.defineProperty(navigator, "webdriver", {get: () => undefined})'
                )
                page.on("dialog", lambda d: d.accept())
                years, owner_info = self._warm_and_read_detail(page, prop_url)
                browser.close()
        except Exception as exc:
            return self._not_found(county, "", "",
                f"Collin CAD detail-page error: {exc}")

        if not years:
            return self._not_found(county, "", "",
                f"No Value History found for property ID {prop_id}.")

        return self._build_response(
            county, "", "", prop_id, prop_id, years, prop_url,
            owner_info.get("ownerName"), owner_info.get("mailingAddress"))

    def _search_candidates(self, session, street: str) -> list[dict]:
        return []

    def _fetch_year(self, session, prop_id: str, year: int) -> dict | None:
        return None
