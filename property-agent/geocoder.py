import re
from geopy.geocoders import Nominatim
from geopy.exc import GeocoderTimedOut, GeocoderServiceError

_geolocator = Nominatim(user_agent="smartrealtytx-property-agent/1.0", timeout=10)

# Fallback: well-known TX city → county mappings for common cases
CITY_TO_COUNTY = {
    "houston": "Harris",
    "dallas": "Dallas",
    "austin": "Travis",
    "san antonio": "Bexar",
    "fort worth": "Tarrant",
    "arlington": "Tarrant",
    "plano": "Collin",
    "frisco": "Collin",
    "mckinney": "Collin",
    "allen": "Collin",
    "denton": "Denton",
    "lewisville": "Denton",
    "flower mound": "Denton",
    "sugar land": "Fort Bend",
    "missouri city": "Fort Bend",
    "pearland": "Brazoria",
    "the woodlands": "Montgomery",
    "conroe": "Montgomery",
    "round rock": "Williamson",
    "cedar park": "Williamson",
    "georgetown": "Williamson",
    "pasadena": "Harris",
    "katy": "Harris",
    "spring": "Harris",
    "humble": "Harris",
    "league city": "Galveston",
    "galveston": "Galveston",
    "beaumont": "Jefferson",
    "el paso": "El Paso",
    "lubbock": "Lubbock",
    "amarillo": "Potter",
    "waco": "McLennan",
    "corpus christi": "Nueces",
    "laredo": "Webb",
    "irving": "Dallas",
    "garland": "Dallas",
    "mesquite": "Dallas",
    "grand prairie": "Dallas",
    "carrollton": "Dallas",
    "richardson": "Dallas",
    "euless": "Tarrant",
    "bedford": "Tarrant",
    "hurst": "Tarrant",
    "grapevine": "Tarrant",
    "southlake": "Tarrant",
}


def geocode_address(address: str) -> dict | None:
    # Try to append Texas if not present
    query = address
    if "texas" not in address.lower() and ", tx" not in address.lower():
        query = f"{address}, Texas"

    try:
        location = _geolocator.geocode(query, addressdetails=True, country_codes="us")
    except (GeocoderTimedOut, GeocoderServiceError):
        location = None

    if location:
        raw = location.raw.get("address", {})
        county = (
            raw.get("county", "")
            .replace(" County", "")
            .replace(" CAD", "")
            .strip()
        )
        if county:
            return {
                "county": county,
                "state": raw.get("state", "Texas"),
                "display_name": location.address,
                "lat": location.latitude,
                "lon": location.longitude,
            }

    # Fallback: extract city name from address and look up county
    city = _extract_city(address)
    if city:
        county = CITY_TO_COUNTY.get(city.lower())
        if county:
            return {
                "county": county,
                "state": "Texas",
                "display_name": address,
                "lat": None,
                "lon": None,
            }

    return None


def _extract_city(address: str) -> str | None:
    # Try to extract city from "Street, City, TX zip" or "Street, City, TX"
    parts = [p.strip() for p in address.split(",")]
    for part in reversed(parts):
        part_clean = re.sub(r"\b(TX|Texas|\d{5})\b", "", part, flags=re.IGNORECASE).strip()
        if part_clean and not re.match(r"^\d+", part_clean):
            return part_clean
    return None
