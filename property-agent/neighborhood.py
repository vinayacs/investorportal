import requests

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

_QUERY_TEMPLATE = """
[out:json][timeout:25];
(
  node["amenity"~"^(school|college|university)$"](around:{r},{lat},{lon});
  way["amenity"~"^(school|college|university)$"](around:{r},{lat},{lon});
  node["leisure"~"^(park|nature_reserve)$"](around:{r},{lat},{lon});
  way["leisure"~"^(park|nature_reserve)$"](around:{r},{lat},{lon});
  node["shop"~"^(supermarket|grocery)$"](around:{r},{lat},{lon});
  node["amenity"~"^(hospital|clinic)$"](around:{r},{lat},{lon});
  way["amenity"~"^(hospital|clinic)$"](around:{r},{lat},{lon});
  node["public_transport"="station"](around:{r},{lat},{lon});
  node["railway"~"^(station|halt)$"](around:{r},{lat},{lon});
  node["amenity"="restaurant"](around:{r},{lat},{lon});
  node["building"="construction"](around:{r},{lat},{lon});
  way["building"="construction"](around:{r},{lat},{lon});
  way["landuse"="construction"](around:{r},{lat},{lon});
);
out tags;
"""


def get_neighborhood_signals(lat: float, lon: float, radius_miles: float = 5.0) -> dict | None:
    """
    Query Overpass API (OpenStreetMap) for nearby amenity counts.
    Returns None on any failure so the main response degrades gracefully.
    """
    radius_m = int(radius_miles * 1609.34)
    query = _QUERY_TEMPLATE.format(r=radius_m, lat=lat, lon=lon)

    try:
        resp = requests.post(
            OVERPASS_URL,
            data={"data": query},
            timeout=30,
            headers={"User-Agent": "SmartRealtyTX/1.0"},
        )
        resp.raise_for_status()
        data = resp.json()
    except Exception:
        return None

    counts = {
        "schools": 0,
        "parks": 0,
        "grocery": 0,
        "medical": 0,
        "transitStations": 0,
        "restaurants": 0,
        "newConstruction": 0,
    }

    seen: set[tuple] = set()
    for el in data.get("elements", []):
        key = (el.get("type"), el.get("id"))
        if key in seen:
            continue
        seen.add(key)

        tags = el.get("tags", {})
        amenity  = tags.get("amenity", "")
        leisure  = tags.get("leisure", "")
        shop     = tags.get("shop", "")
        building = tags.get("building", "")
        landuse  = tags.get("landuse", "")
        transport = tags.get("public_transport", "")
        railway  = tags.get("railway", "")

        if amenity in ("school", "college", "university"):
            counts["schools"] += 1
        elif leisure in ("park", "nature_reserve"):
            counts["parks"] += 1
        elif shop in ("supermarket", "grocery"):
            counts["grocery"] += 1
        elif amenity in ("hospital", "clinic"):
            counts["medical"] += 1
        elif transport == "station" or railway in ("station", "halt"):
            counts["transitStations"] += 1
        elif amenity == "restaurant":
            counts["restaurants"] += 1
        elif building == "construction" or landuse == "construction":
            counts["newConstruction"] += 1

    return {
        "radiusMiles": radius_miles,
        "lat": round(lat, 6),
        "lon": round(lon, 6),
        "amenities": counts,
    }
