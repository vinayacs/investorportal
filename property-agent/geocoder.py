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
            nom_city = (
                raw.get("city")
                or raw.get("town")
                or raw.get("village")
                or raw.get("suburb")
                or ""
            ).title()
            return {
                "county": county,
                "city": nom_city,
                "state": raw.get("state", "Texas"),
                "display_name": location.address,
                "lat": location.latitude,
                "lon": location.longitude,
                "zip": raw.get("postcode", ""),
            }

    # Fallback 1: static city→county map
    city = _extract_city(address)
    if city:
        county = CITY_TO_COUNTY.get(city.lower())
        if county:
            # Try to get city-center coordinates so neighborhood signals still work
            try:
                city_loc = _geolocator.geocode(
                    f"{city}, Texas", addressdetails=True, country_codes="us"
                )
            except (GeocoderTimedOut, GeocoderServiceError):
                city_loc = None
            city_lat = city_loc.latitude if city_loc else None
            city_lon = city_loc.longitude if city_loc else None
            city_zip = city_loc.raw.get("address", {}).get("postcode", "") if city_loc else ""
            return {
                "county": county,
                "city": city.title(),
                "state": "Texas",
                "display_name": address,
                "lat": city_lat,
                "lon": city_lon,
                "zip": city_zip,
            }

        # Fallback 2: Nominatim city-level lookup — handles small towns not in the map
        # (rural addresses often fail street-level geocoding even when the city is known)
        try:
            city_loc = _geolocator.geocode(
                f"{city}, Texas", addressdetails=True, country_codes="us"
            )
        except (GeocoderTimedOut, GeocoderServiceError):
            city_loc = None

        if city_loc:
            raw = city_loc.raw.get("address", {})
            county = (
                raw.get("county", "")
                .replace(" County", "")
                .replace(" CAD", "")
                .strip()
            )
            if county:
                return {
                    "county": county,
                    "city": city.title(),
                    "state": raw.get("state", "Texas"),
                    "display_name": city_loc.address,
                    "lat": city_loc.latitude,
                    "lon": city_loc.longitude,
                    "zip": raw.get("postcode", ""),
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
