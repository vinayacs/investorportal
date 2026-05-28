from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from geocoder import geocode_address
from county_router import route_to_cad, route_to_cad_by_id

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
    if not county_info:
        raise HTTPException(
            status_code=422,
            detail=f"Could not geocode address '{address}'. Make sure it includes a city or zip code."
        )

    return route_to_cad(county_info, address)


@app.post("/analyze-by-id")
def analyze_by_id(request: AnalyzeByIdRequest):
    prop_id = request.propertyId.strip()
    county = request.county.strip()
    if not prop_id or not county:
        raise HTTPException(status_code=400, detail="propertyId and county are required")

    county_info = {"county": county, "city": "", "state": "Texas"}
    return route_to_cad_by_id(county_info, prop_id)
