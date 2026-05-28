import re
from abc import ABC, abstractmethod
import requests
from bs4 import BeautifulSoup

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.5",
}


def extract_street(address: str) -> str:
    """
    Pull just the street number + name from a full address.
    '1234 Main St, Houston, TX 77001' → '1234 Main St'
    '1234 Main St' → '1234 Main St'
    """
    part = address.split(",")[0].strip()
    # Remove any trailing state/zip that ended up in the first segment
    part = re.sub(r"\b(TX|Texas)\b.*$", "", part, flags=re.IGNORECASE).strip()
    return part


class BaseCADScraper(ABC):

    def get_session(self) -> requests.Session:
        s = requests.Session()
        s.headers.update(HEADERS)
        return s

    @abstractmethod
    def _search_candidates(self, session: requests.Session, street: str) -> list[dict]:
        """
        Search the CAD by street number+name.
        Returns a list of candidate dicts: {id, label}
        where id is the property/account identifier and label is a display string.
        May return many matches — caller will iterate to find one with history.
        """
        pass

    @abstractmethod
    def _fetch_year(self, session: requests.Session, prop_id: str, year: int) -> dict | None:
        """Fetch appraisal values for a single property+year. Returns None if not found."""
        pass

    def _score_candidate(self, candidate: dict, street: str) -> int:
        """Score how well a candidate matches the searched street. Higher = better match."""
        label = candidate.get("label", "").lower()
        street_lower = street.lower()

        score = 0
        # Strong signal: street number appears at the start of the label
        num_match = re.match(r"(\d+)", street_lower)
        if num_match:
            num = num_match.group(1)
            if re.search(r"\b" + re.escape(num) + r"\b", label):
                score += 10

        # Weaker signal: street name words appear in label
        words = street_lower.split()
        for word in words[1:]:  # skip the number token
            if len(word) > 2 and word in label:
                score += 1

        return score

    def get_owner_info(self, session: requests.Session, prop_id: str) -> dict:
        """Override in subclasses to return ownerName and mailingAddress."""
        return {"ownerName": None, "mailingAddress": None}

    @staticmethod
    def _soup_owner_info(soup: BeautifulSoup) -> dict:
        """
        Robust owner name + mailing address extractor.
        Three strategies handle table-based, div-based, and plain-text CAD layouts.
        """
        owner = None
        mail_addr = None

        # Strict sets (safe everywhere) and extended sets (used inside owner section)
        OWNER_STRICT = {"owner", "owner name", "property owner", "legal owner",
                        "owner/applicant"}
        OWNER_EXT    = OWNER_STRICT | {"name"}
        MAIL_STRICT  = {"mailing address", "mail address", "mail addr",
                        "mailing", "owner mailing address", "owner address"}
        MAIL_EXT     = MAIL_STRICT | {"address"}

        STOP_WORDS = {"phone", "email", "legal description", "value history",
                      "property id", "account number", "tax year", "improvement",
                      "land value", "appraised value", "assessed", "account"}

        def norm(t: str) -> str:
            return t.lower().strip().rstrip(":").strip()

        def clean(t: str) -> str:
            return re.sub(r"\s{2,}", " ", t).strip()

        # Labels that should never be treated as a value
        HEADER_LIKE = (OWNER_STRICT | MAIL_STRICT | {
            "property id", "account", "account number", "situs address",
            "legal description", "year", "improvement", "land", "appraised",
            "assessed", "taxable", "phone", "email", "city", "state", "zip",
        })

        # ── Strategy 1: table row label → value (handles td/th in same <tr>) ────
        for tr in soup.find_all("tr"):
            cells = tr.find_all(["td", "th"])
            # Scan every consecutive pair so 4-cell rows like
            # [label1, val1, label2, val2] are also handled
            for i in range(len(cells) - 1):
                lbl = norm(cells[i].get_text())
                val = clean(cells[i + 1].get_text(" "))
                val_n = norm(val)
                if val_n in HEADER_LIKE:   # reject column-header false positives
                    continue
                if not owner and lbl in OWNER_STRICT and val and len(val) > 2:
                    owner = val
                if not mail_addr and lbl in MAIL_STRICT and val:
                    mail_addr = val

        # ── Strategy 2: any short element as label + sibling/child as value ─────
        # Covers Bootstrap div-col layouts and <b>/<strong> inline labels
        if not owner or not mail_addr:
            # First pass: look for an "Owner Information" heading to widen label set
            in_owner_section = False
            for el in soup.find_all(
                    ["td", "th", "dt", "label", "b", "strong", "div", "span", "p"]):
                txt = el.get_text(strip=True)
                if len(txt) > 60 or not txt:
                    continue
                n = norm(txt)

                # Detect owner-info section heading
                if re.search(r"owner\s+(information|detail|info)", n):
                    in_owner_section = True
                    continue
                # Exit section on known other headings
                if in_owner_section and re.search(
                        r"value history|property information|legal description"
                        r"|improvement|tax information", n):
                    in_owner_section = False

                o_set = OWNER_EXT if in_owner_section else OWNER_STRICT
                m_set = MAIL_EXT if in_owner_section else MAIL_STRICT

                # Skip section-header th elements (colspan > 1) — they match
                # label words like "Owner" but the adjacent row's first cell is
                # a data field (e.g. "Owner ID"), not the value we want.
                is_section_header = (el.name == "th" and el.get("colspan"))

                if not owner and n in o_set and not is_section_header:
                    # 1. Text node immediately after the label inside the same parent
                    #    e.g. <span><strong>Owner: </strong>Name Here</span>
                    from bs4 import NavigableString
                    for sib in el.next_siblings:
                        if isinstance(sib, NavigableString):
                            val = sib.strip()
                            if val and len(val) > 2 and norm(val) not in HEADER_LIKE:
                                owner = val
                                break
                        elif hasattr(sib, "name"):
                            break  # stop at the next element sibling

                    # 2. Direct element sibling
                    if not owner:
                        nxt = el.find_next_sibling()
                        if nxt and nxt.name in ("td", "dd", "div", "span", "p"):
                            val = nxt.get_text(strip=True)
                            if val and len(val) > 2 and norm(val) not in HEADER_LIKE:
                                owner = val

                    # 3. Parent's next sibling's first meaningful child
                    #    (only for non-th labels to avoid section-header false positives)
                    if not owner and el.parent and el.name != "th":
                        row_sib = el.parent.find_next_sibling()
                        if row_sib:
                            child = row_sib.find(["td", "dd", "div", "span"])
                            if child:
                                val = child.get_text(strip=True)
                                if val and len(val) > 2 and norm(val) not in HEADER_LIKE:
                                    owner = val

                if not mail_addr and n in m_set:
                    nxt = el.find_next_sibling()
                    if nxt and nxt.name in ("td", "dd", "div", "span", "p"):
                        val = clean(nxt.get_text(" "))
                        if val and norm(val) not in HEADER_LIKE:
                            mail_addr = val
                    if not mail_addr and el.parent:
                        row_sib = el.parent.find_next_sibling()
                        if row_sib:
                            child = row_sib.find(["td", "dd", "div", "span"])
                            if child:
                                val = clean(child.get_text(" "))
                                if norm(val) not in HEADER_LIKE:
                                    mail_addr = val

        # ── Strategy 3: plain-text line-by-line scan ─────────────────────────────
        if not owner or not mail_addr:
            lines = [l.strip() for l in soup.get_text("\n").split("\n") if l.strip()]
            in_sect = False
            for i, line in enumerate(lines):
                ll = norm(line)
                if re.search(r"owner\s+(information|detail|info)", ll):
                    in_sect = True
                    continue
                if in_sect and re.search(
                        r"value history|property information|legal description", ll):
                    in_sect = False

                o_set = OWNER_EXT if in_sect else OWNER_STRICT
                m_set = MAIL_EXT if in_sect else MAIL_STRICT

                if not owner and ll in o_set and i + 1 < len(lines):
                    cand = lines[i + 1]
                    if (len(cand) > 2
                            and norm(cand) not in o_set
                            and norm(cand) not in m_set
                            and not any(w in cand.lower() for w in STOP_WORDS)):
                        owner = cand

                if not mail_addr and ll in m_set:
                    parts = []
                    for j in range(i + 1, min(i + 5, len(lines))):
                        p = lines[j]
                        pl = norm(p)
                        if (pl in o_set or pl in m_set
                                or any(w in pl for w in STOP_WORDS)
                                or re.match(r"^\d{4}$", p)):
                            break
                        parts.append(p)
                    if parts:
                        mail_addr = " ".join(parts)

        return {"ownerName": owner, "mailingAddress": mail_addr}

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        county = county_info["county"]
        city = county_info.get("city", "")
        session = self.get_session()
        street = extract_street(address)

        candidates = self._search_candidates(session, street)
        if not candidates:
            return self._not_found(county, address, city,
                f"No properties found for '{street}' in {county} CAD.")

        # Sort candidates so the best address-match is tried first
        candidates = sorted(
            candidates,
            key=lambda c: self._score_candidate(c, street),
            reverse=True,
        )

        from datetime import datetime
        current_year = datetime.now().year
        years_to_fetch = list(range(current_year - 2, current_year + 1))

        # Try each candidate until we find one that has appraisal history
        for candidate in candidates:
            prop_id = candidate["id"]
            years = []
            for year in years_to_fetch:
                values = self._fetch_year(session, prop_id, year)
                if values:
                    years.append(values)

            if years:  # This candidate has history — it's the right one
                cad_url = candidate.get("url", "")
                owner_info = self.get_owner_info(session, prop_id)
                return self._build_response(
                    county, address, city,
                    prop_id, candidate["label"],
                    years, cad_url,
                    owner_info.get("ownerName"),
                    owner_info.get("mailingAddress"),
                )

        # All candidates found but none had appraisal history
        return self._not_found(county, address, city,
            f"Found {len(candidates)} properties matching '{street}' but none had appraisal history. "
            "Try including the city or zip code.")

    def _build_response(self, county, address, city,
                        property_id, property_address, years, cad_url="",
                        owner_name=None, mailing_address=None) -> dict:
        return {
            "supported": True,
            "county": county,
            "city": city,
            "address": address,
            "propertyId": property_id,
            "propertyAddress": property_address,
            "cadUrl": cad_url,
            "ownerName": owner_name,
            "mailingAddress": mailing_address,
            "years": years,
        }

    def _not_found(self, county, address, city="", message="") -> dict:
        return {
            "supported": True,
            "county": county,
            "city": city,
            "address": address,
            "propertyId": None,
            "propertyAddress": None,
            "cadUrl": "",
            "ownerName": None,
            "mailingAddress": None,
            "years": [],
            "message": message or f"Property not found in {county} CAD.",
        }
