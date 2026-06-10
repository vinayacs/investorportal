"""
Collin Central Appraisal District (CollinCAD)
Portal: https://esearch.collincad.org

Uses non-headless Chromium via Playwright with Xvfb virtual display to bypass
Cloudflare Bot Management on this portal.

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
from urllib.parse import quote_plus
from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout
from bs4 import BeautifulSoup
from .base import BaseCADScraper, extract_street

# Minimal stealth script injected before every page load.
# Hides the most common Playwright/headless Chromium signals that Cloudflare checks.
_STEALTH_JS = """
// Hide automation markers
Object.defineProperty(navigator, 'webdriver', {get: () => undefined});

// Realistic plugin list
Object.defineProperty(navigator, 'plugins', {get: () => [
    {name:'Chrome PDF Plugin',filename:'internal-pdf-viewer',description:'Portable Document Format'},
    {name:'Chrome PDF Viewer',filename:'mhjfbmdgcfjbbpaeojofohoefgiehjai',description:''},
    {name:'Native Client',filename:'internal-nacl-plugin',description:''},
]});

// Language / locale
Object.defineProperty(navigator, 'languages', {get: () => ['en-US','en']});

// Hardware signals (8-core, 8 GB — common laptop profile)
Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});

// Chrome runtime shim
window.chrome = {runtime:{}, loadTimes:function(){}, csi:function(){}, app:{}};

// Spoof WebGL renderer so it looks like a real Intel iGPU instead of Mesa/llvmpipe
(function() {
    const origGetContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function(type) {
        const ctx = origGetContext.apply(this, arguments);
        if (ctx && (type === 'webgl' || type === 'experimental-webgl' || type === 'webgl2')) {
            const origGetParam = ctx.getParameter.bind(ctx);
            ctx.getParameter = function(param) {
                if (param === 37445) return 'Intel Inc.';
                if (param === 37446) return 'Intel(R) Iris(TM) Plus Graphics 640';
                return origGetParam(param);
            };
        }
        return ctx;
    };
})();
"""

PORTAL = "https://esearch.collincad.org"


class CollinCADScraper(BaseCADScraper):

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        print(f"[CollinCAD] get_appraisal_history: address={address!r}", flush=True)
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

        last_exc = None
        matches = []
        years, owner_info, match = [], {}, None

        for attempt in range(1, 4):  # up to 3 attempts to get past Cloudflare
            try:
                with sync_playwright() as pw:
                    # Use Chromium non-headless — it has stronger anti-detection support
                    # than Firefox and Cloudflare has more "trusted" Chromium data.
                    browser = pw.chromium.launch(
                        headless=False,
                        args=[
                            '--no-sandbox',
                            '--disable-blink-features=AutomationControlled',
                            '--disable-infobars',
                            '--window-size=1920,1080',
                        ]
                    )
                    ctx = browser.new_context(
                        user_agent=(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                            "AppleWebKit/537.36 (KHTML, like Gecko) "
                            "Chrome/124.0.0.0 Safari/537.36"
                        ),
                        viewport={"width": 1920, "height": 1080},
                        locale='en-US',
                        timezone_id='America/Chicago',
                    )
                    page = ctx.new_page()
                    page.add_init_script(_STEALTH_JS)
                    page.on("dialog", lambda d: d.accept())

                    print(f"[CollinCAD] Attempt {attempt}/3", flush=True)
                    matches = self._search(page, house_num, search_name)
                    browser.close()

                    # None means Cloudflare blocked — no point retrying.
                    if matches is None:
                        return {
                            "supported": True,
                            "county": county,
                            "city": input_city or "",
                            "address": address,
                            "propertyId": None,
                            "propertyAddress": None,
                            "cadUrl": PORTAL,
                            "ownerName": None,
                            "mailingAddress": None,
                            "years": [],
                            "message": (
                                "Collin County's property portal is temporarily blocking "
                                "automated access (Cloudflare). Please search directly at "
                                "esearch.collincad.org or try again in a few hours."
                            ),
                        }

                    if not matches:
                        if attempt < 3:
                            print(f"[CollinCAD] No matches, retrying...", flush=True)
                            continue
                        return self._not_found(county, address, input_city,
                            f"No property found matching '{street}' in Collin CAD.")

                    if input_city and len(matches) > 1:
                        filtered = [m for m in matches if input_city in m["city"].lower()]
                        if filtered:
                            matches = filtered

                    if len(matches) > 1:
                        cities = sorted({m["city"] for m in matches})
                        return self._ambiguous_response(county, address, cities)

                    match = matches[0]
                    prop_url = (f"{PORTAL}/Property/View/{match['pid']}"
                                f"?ownerId={match['oid']}")
                    # Open a fresh browser session for the detail page.
                    with sync_playwright() as pw2:
                        br2 = pw2.chromium.launch(
                            headless=False,
                            args=[
                                '--no-sandbox',
                                '--disable-blink-features=AutomationControlled',
                                '--disable-infobars',
                                '--window-size=1920,1080',
                            ]
                        )
                        c2 = br2.new_context(
                            user_agent=(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                "AppleWebKit/537.36 (KHTML, like Gecko) "
                                "Chrome/124.0.0.0 Safari/537.36"
                            ),
                            viewport={"width": 1920, "height": 1080},
                            locale='en-US',
                            timezone_id='America/Chicago',
                        )
                        p2 = c2.new_page()
                        p2.add_init_script(_STEALTH_JS)
                        p2.on("dialog", lambda d: d.accept())
                        years, owner_info = self._warm_and_read_detail(p2, prop_url)
                        br2.close()
                    break  # success

            except Exception as exc:
                import traceback
                last_exc = exc
                print(f"[CollinCAD] Attempt {attempt} EXCEPTION: {exc}\n"
                      f"{traceback.format_exc()}", flush=True)
                if attempt == 3:
                    return self._not_found(county, address, input_city,
                        f"Collin CAD scraper error: {exc}")

        if not match:
            return self._not_found(county, address, input_city,
                f"No property found matching '{street}' in Collin CAD.")

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
        print(f"[CollinCAD] _search: house_num={house_num!r} search_name={search_name!r}", flush=True)

        # Load home page and wait for Cloudflare challenge to clear.
        try:
            page.goto(PORTAL, timeout=40000)
            page.wait_for_function(
                "() => document.title !== 'Just a moment...' && document.title !== ''",
                timeout=60000,
            )
            page.wait_for_selector('a:has-text("By Address")', timeout=30000)
        except PWTimeout:
            print(f"[CollinCAD] TIMEOUT on home page. title={page.title()!r}", flush=True)
            return []
        print(f"[CollinCAD] Home page ready. title={page.title()!r}", flush=True)
        # Log whether cf_clearance cookie was set (key diagnostic for Cloudflare bypass).
        cf_cookies = [c['name'] for c in page.context.cookies() if 'cf' in c['name'].lower()]
        print(f"[CollinCAD] CF cookies after home page: {cf_cookies}", flush=True)

        # Extended human-like behaviour — gives Cloudflare time to build session trust.
        for x, y in [(300, 300), (700, 400), (500, 500), (400, 250), (600, 350), (350, 450)]:
            page.mouse.move(x, y)
            page.wait_for_timeout(400)
        page.evaluate("window.scrollBy(0, 300)")
        page.wait_for_timeout(1200)
        page.evaluate("window.scrollBy(0, -150)")
        page.wait_for_timeout(1000)

        # Primary approach: click the "By Address" tab, fill #StreetNumber + #StreetName,
        # then submit. The page is Blazor Server (not Angular/Angular form) — standard
        # Playwright click + pressSequentially fires the Blazor event handlers correctly.
        # #keywords (Quick Search) is in a hidden tab; By Address is the cleaner path.
        form_succeeded = False

        try:
            # Click the "By Address" tab to show the address search fields.
            by_addr_tab = page.locator('a:has-text("By Address")').first
            by_addr_tab.click(timeout=10000)
            print(f"[CollinCAD] Clicked 'By Address' tab", flush=True)
            page.wait_for_timeout(600)

            # Wait for #StreetNumber to become visible (Blazor re-renders).
            page.wait_for_selector('#StreetNumber', state='visible', timeout=10000)

            sn_loc = page.locator('#StreetNumber')
            sn_loc.click()
            page.wait_for_timeout(100)
            sn_loc.pressSequentially(house_num, delay=60)
            print(f"[CollinCAD] Filled #StreetNumber: {house_num!r}", flush=True)

            page.wait_for_timeout(200)

            name_loc = page.locator('#StreetName')
            name_loc.click()
            page.wait_for_timeout(100)
            name_loc.pressSequentially(search_name, delay=60)
            print(f"[CollinCAD] Filled #StreetName: {search_name!r}", flush=True)
            page.wait_for_timeout(400)

            # Press Enter on StreetName to trigger Blazor search.
            name_loc.press('Enter')

            try:
                page.wait_for_url("**/search/result**", timeout=20000)
                page.wait_for_timeout(2000)
                title = page.title()
                if "just a moment" not in title.lower():
                    print(f"[CollinCAD] Form nav succeeded (Enter). URL={page.url} title={title!r}", flush=True)
                    form_succeeded = True
                else:
                    print(f"[CollinCAD] Form nav landed on Cloudflare. title={title!r}", flush=True)
                    return None
            except PWTimeout:
                print(f"[CollinCAD] Enter nav timeout. URL={page.url}", flush=True)

            # If Enter didn't navigate, try the Search submit button.
            if not form_succeeded:
                try:
                    btn = page.locator(
                        'button[type="submit"], input[type="submit"], '
                        'button:has-text("Search"), .btn-search, #btnSearch'
                    ).first
                    btn.click(timeout=5000)
                    print(f"[CollinCAD] Clicked search submit button", flush=True)
                    try:
                        page.wait_for_url("**/search/result**", timeout=20000)
                        page.wait_for_timeout(2000)
                        title = page.title()
                        if "just a moment" not in title.lower():
                            print(f"[CollinCAD] Form nav succeeded (button). URL={page.url}", flush=True)
                            form_succeeded = True
                        else:
                            print(f"[CollinCAD] Button nav landed on Cloudflare.", flush=True)
                            return None
                    except PWTimeout:
                        print(f"[CollinCAD] Button nav timeout. URL={page.url}", flush=True)
                except Exception as e:
                    print(f"[CollinCAD] Submit button click failed: {e}", flush=True)

        except Exception as exc:
            print(f"[CollinCAD] By Address form interaction error: {exc}", flush=True)

        if form_succeeded:
            return self._parse_results(page, house_num)

        # Fallback: page.goto() (sends proper Referer/cookie headers, avoids the
        # race where wait_for_function fires on the old page title during JS navigation).
        keywords = quote_plus(f"{house_num} {search_name}")
        search_url = f"{PORTAL}/search/result?keywords={keywords}"
        print(f"[CollinCAD] goto fallback: {search_url}", flush=True)

        # Try JS-initiated navigation first — keeps same execution context/cookies.
        try:
            page.evaluate(f"() => {{ window.location.href = {repr(search_url)}; }}")
            page.wait_for_url("**/search/result**", timeout=40000)
        except PWTimeout:
            print(f"[CollinCAD] JS nav timeout, trying page.goto", flush=True)
            try:
                page.goto(search_url, timeout=40000, wait_until='domcontentloaded')
            except PWTimeout:
                print(f"[CollinCAD] goto timeout", flush=True)

        # Cloudflare challenge can take up to 60s on rate-limited paths.
        try:
            page.wait_for_function(
                "() => document.title !== 'Just a moment...' && document.title !== ''",
                timeout=60000,
            )
        except PWTimeout:
            pass

        page.wait_for_timeout(1500)
        final_title = page.title()
        if "just a moment" in final_title.lower():
            print(f"[CollinCAD] Cloudflare blocking search results. title={final_title!r}", flush=True)
            return None  # Signal to caller: Cloudflare blocked, stop retrying.

        print(f"[CollinCAD] Search results loaded. URL={page.url} title={final_title!r}", flush=True)
        return self._parse_results(page, house_num)

    def _parse_results(self, page, house_num: str) -> list[dict]:
        soup = BeautifulSoup(page.content(), "lxml")
        results = []

        pattern = re.compile(
            r"redirectToPropertyDetails\('(\d+)','(\d+)','(\d+)'", re.IGNORECASE
        )
        all_els = soup.find_all(onclick=pattern)
        print(f"[CollinCAD] _parse_results: house_num={house_num!r}, "
              f"elements_with_onclick={len(all_els)}", flush=True)

        seen = set()
        for el in all_els:
            m = pattern.search(el.get("onclick", ""))
            if not m:
                continue
            pid, year, oid = m.group(1), m.group(2), m.group(3)
            if pid in seen:
                continue
            seen.add(pid)

            if el.name == "tr":
                row = el
            else:
                row = el.find_parent("tr") or el.find_parent("li") or el.parent
            row_text = row.get_text(" ", strip=True) if row else el.get_text(strip=True)

            house_match = not house_num or bool(re.search(
                r"\b" + re.escape(house_num) + r"\b", row_text))
            print(f"[CollinCAD]   pid={pid} tag={el.name} "
                  f"house_match={house_match} "
                  f"row_text={row_text[:120]!r}", flush=True)

            if not house_match:
                continue

            city = ""
            city_m = re.search(r",\s*([A-Z][A-Z ]+)\s+TX\b", row_text)
            if city_m:
                city = city_m.group(1).strip().title()

            addr_m = re.search(
                re.escape(house_num) + r"\s+\S.*?TX\s+\d{5}",
                row_text, re.IGNORECASE
            )
            situs = addr_m.group(0).strip() if addr_m else row_text[:80]

            results.append({
                "pid": pid, "oid": oid,
                "address": situs, "city": city,
            })

        print(f"[CollinCAD] _parse_results returning {len(results)} match(es)", flush=True)
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
            page.wait_for_function(
                "() => document.title !== 'Just a moment...' && document.title !== ''",
                timeout=60000,
            )
            page.wait_for_selector('a:has-text("By Address")', timeout=30000)
        except PWTimeout:
            pass
        page.wait_for_timeout(800)
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
                browser = pw.chromium.launch(
                    headless=False,
                    args=[
                        '--no-sandbox',
                        '--disable-blink-features=AutomationControlled',
                        '--disable-infobars',
                        '--window-size=1920,1080',
                    ]
                )
                ctx = browser.new_context(
                    user_agent=(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/124.0.0.0 Safari/537.36"
                    ),
                    viewport={"width": 1920, "height": 1080},
                    locale='en-US',
                    timezone_id='America/Chicago',
                )
                page = ctx.new_page()
                page.add_init_script(_STEALTH_JS)
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
