import re
from abc import ABC, abstractmethod
import requests

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

    def get_appraisal_history(self, address: str, county_info: dict) -> dict:
        county = county_info["county"]
        session = self.get_session()
        street = extract_street(address)

        candidates = self._search_candidates(session, street)
        if not candidates:
            return self._not_found(county, address,
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
                return self._build_response(
                    county, address,
                    prop_id, candidate["label"],
                    years, cad_url
                )

        # All candidates found but none had appraisal history
        return self._not_found(county, address,
            f"Found {len(candidates)} properties matching '{street}' but none had appraisal history. "
            "Try including the city or zip code.")

    def _build_response(self, county, address, property_id,
                        property_address, years, cad_url="") -> dict:
        return {
            "supported": True,
            "county": county,
            "address": address,
            "propertyId": property_id,
            "propertyAddress": property_address,
            "cadUrl": cad_url,
            "years": years,
        }

    def _not_found(self, county, address, message="") -> dict:
        return {
            "supported": True,
            "county": county,
            "address": address,
            "propertyId": None,
            "propertyAddress": None,
            "cadUrl": "",
            "years": [],
            "message": message or f"Property not found in {county} CAD.",
        }
