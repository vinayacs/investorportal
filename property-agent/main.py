from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from geocoder import geocode_address
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
    if lat is not None and lon is not None:
        nb2 = get_neighborhood_signals(lat, lon, radius_miles=2.0)
        nb5 = get_neighborhood_signals(lat, lon, radius_miles=5.0)
        result["neighborhoods"] = [n for n in [nb2, nb5] if n is not None]
    else:
        result["neighborhoods"] = []

    zip_code = county_info.get("zip") or extract_zip(address)
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

    # Geocode the resolved property address to get coordinates for neighborhood signals
    resolved_address = result.get("propertyAddress") or result.get("address") or ""
    geo = geocode_address(resolved_address) if resolved_address else None
    lat = geo.get("lat") if geo else None
    lon = geo.get("lon") if geo else None
    if lat is not None and lon is not None:
        nb2 = get_neighborhood_signals(lat, lon, radius_miles=2.0)
        nb5 = get_neighborhood_signals(lat, lon, radius_miles=5.0)
        result["neighborhoods"] = [n for n in [nb2, nb5] if n is not None]
    else:
        result["neighborhoods"] = []

    zip_code = (geo.get("zip") if geo else None) or extract_zip(resolved_address)
    result["marketContext"] = get_market_context(zip_code) if zip_code else None

    return result
