from scrapers.hcad import HCADScraper
from scrapers.dcad import DCADScraper
from scrapers.tad import TADScraper
from scrapers.true_automation import TrueAutomationScraper
from scrapers.denton import DentonCADScraper

# TrueAutomation platform client IDs for Texas counties
# Denton is excluded — it migrated to dentoncad.com (TrueProdigy platform)
TRUE_AUTO_COUNTIES = {
    "Collin":     21,
    "Fort Bend":  79,
    "Montgomery": 170,
    "Williamson": 246,
    "Bexar":      15,
    "Brazoria":   20,
    "Galveston":  84,
    "McLennan":   161,
    "Lubbock":    152,
    "Jefferson":  121,
    "El Paso":    71,
}

DEDICATED_SCRAPERS = {
    "Harris":  HCADScraper,
    "Dallas":  DCADScraper,
    "Tarrant": TADScraper,
    "Denton":  DentonCADScraper,
}


def route_to_cad(county_info: dict, address: str) -> dict:
    county = county_info["county"]

    # Dedicated scraper
    if county in DEDICATED_SCRAPERS:
        scraper = DEDICATED_SCRAPERS[county]()
        return scraper.get_appraisal_history(address, county_info)

    # TrueAutomation platform
    if county in TRUE_AUTO_COUNTIES:
        scraper = TrueAutomationScraper(cid=TRUE_AUTO_COUNTIES[county])
        return scraper.get_appraisal_history(address, county_info)

    return {
        "supported": False,
        "county": county,
        "address": address,
        "message": f"{county} County is not yet supported. Supported counties: Harris, Dallas, Tarrant, Collin, Denton, Fort Bend, Montgomery, Williamson, Bexar, Brazoria, Galveston.",
        "years": [],
    }
