from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from geocoder import geocode_address
from county_router import route_to_cad

app = FastAPI(title="Property Analysis Agent")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class AnalyzeRequest(BaseModel):
    address: str


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
