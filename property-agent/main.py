import re
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from geocoder import geocode_address, CITY_TO_COUNTY
from county_router import route_to_cad, route_to_cad_by_id
from neighborhood import get_neighborhood_signals
from census_acs import get_market_context, extract_zip

app = FastAPI(title="Property Analysis Agent")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class AnalyzeRequest(BaseModel):
    address: str


class AnalyzeByIdRequest(BaseModel):
    propertyId: str
    county: str


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/analyze")
def analyze(request: AnalyzeRequest):
    address = request.address.strip()
    if not address:
        raise HTTPException(status_code=400, detail="Address is required")

    # Fast path for Collin County: all data comes from SQLite — skip geocoding
    # and Overpass entirely (those two Overpass calls add 30-60s of latency).
    city = _city_from_address(address)
    if CITY_TO_COUNTY.get(city) == "Collin":
        print(f"[analyze] Collin fast path for city={city!r}", flush=True)
        county_info = {"county": "Collin", "city": city.title(), "state": "Texas"}
        result = route_to_cad(county_info, address)
        zip_code = extract_zip(address) or extract_zip(result.get("propertyAddress", ""))
        result["neighborhoods"] = []
        result["marketContext"] = get_market_context(zip_code) if zip_code else None
        print(f"[analyze] Collin fast path done: years={len(result.get('years', []))}", flush=True)
        return result

    # Normal path: geocode first to determine county + coordinates
    county_info = geocode_address(address)
    print(f"[analyze] address={address!r} county_info={county_info}", flush=True)
    if not county_info:
        raise HTTPException(
            status_code=422,
            detail=f"Could not geocode address '{address}'. Make sure it includes a city or zip code."
        )

    result = route_to_cad(county_info, address)
    print(f"[analyze] route result: supported={result.get('supported')} county={result.get('county')} years={len(result.get('years', []))}", flush=True)

    lat = county_info.get("lat")
    lon = county_info.get("lon")
    zip_code = county_info.get("zip") or extract_zip(address)

    # Run both neighborhood radii + market context in parallel
    if lat is not None and lon is not None:
        with ThreadPoolExecutor(max_workers=3) as ex:
            f_nb2 = ex.submit(get_neighborhood_signals, lat, lon, 2.0)
            f_nb5 = ex.submit(get_neighborhood_signals, lat, lon, 5.0)
            f_mc  = ex.submit(get_market_context, zip_code) if zip_code else None
            nb2 = f_nb2.result()
            nb5 = f_nb5.result()
            result["neighborhoods"] = [n for n in [nb2, nb5] if n is not None]
            result["marketContext"] = f_mc.result() if f_mc else None
    else:
        result["neighborhoods"] = []
        result["marketContext"] = get_market_context(zip_code) if zip_code else None

    return result


@app.post("/analyze-by-id")
def analyze_by_id(request: AnalyzeByIdRequest):
    prop_id = request.propertyId.strip()
    county = request.county.strip()
    if not prop_id or not county:
        raise HTTPException(status_code=400, detail="propertyId and county are required")

    county_info = {"county": county, "city": "", "state": "Texas"}
    result = route_to_cad_by_id(county_info, prop_id)

    resolved_address = result.get("propertyAddress") or result.get("address") or ""
    geo = geocode_address(resolved_address) if resolved_address else None
    lat = geo.get("lat") if geo else None
    lon = geo.get("lon") if geo else None

    if lat is not None and lon is not None:
        with ThreadPoolExecutor(max_workers=3) as ex:
            f_nb2 = ex.submit(get_neighborhood_signals, lat, lon, 2.0)
            f_nb5 = ex.submit(get_neighborhood_signals, lat, lon, 5.0)
            zip_code = (geo.get("zip") if geo else None) or extract_zip(resolved_address)
            f_mc = ex.submit(get_market_context, zip_code) if zip_code else None
            result["neighborhoods"] = [n for n in [f_nb2.result(), f_nb5.result()] if n is not None]
            result["marketContext"] = f_mc.result() if f_mc else None
    else:
        result["neighborhoods"] = []
        zip_code = extract_zip(resolved_address)
        result["marketContext"] = get_market_context(zip_code) if zip_code else None

    return result


def _city_from_address(address: str) -> str:
    """Extract lowercase city name from 'Street, City, TX Zip' format."""
    parts = [p.strip() for p in address.split(",")]
    for part in reversed(parts):
        cleaned = re.sub(r"\b(TX|Texas|\d{5})\b", "", part, flags=re.IGNORECASE).strip()
        if cleaned and not re.match(r"^\d+", cleaned):
            return cleaned.lower()
    return ""
