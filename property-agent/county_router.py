from scrapers.hcad import HCADScraper
from scrapers.dcad import DCADScraper
from scrapers.tad import TADScraper
from scrapers.true_automation import TrueAutomationScraper
from scrapers.denton import DentonCADScraper
from scrapers.collin import CollinCADScraper

# TrueAutomation platform client IDs for Texas counties
# Denton migrated to dentoncad.com; Collin migrated to esearch.collincad.org
TRUE_AUTO_COUNTIES = {
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
    "Collin":  CollinCADScraper,
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
        "city": county_info.get("city", ""),
        "address": address,
        "message": f"{county} County is not yet supported. Supported counties: Harris, Dallas, Tarrant, Collin, Denton, Fort Bend, Montgomery, Williamson, Bexar, Brazoria, Galveston.",
        "years": [],
    }


def route_to_cad_by_id(county_info: dict, prop_id: str) -> dict:
    county = county_info["county"]

    if county in DEDICATED_SCRAPERS:
        scraper = DEDICATED_SCRAPERS[county]()
        if hasattr(scraper, "get_appraisal_by_id"):
            return scraper.get_appraisal_by_id(prop_id, county_info)

    if county in TRUE_AUTO_COUNTIES:
        scraper = TrueAutomationScraper(cid=TRUE_AUTO_COUNTIES[county])
        if hasattr(scraper, "get_appraisal_by_id"):
            return scraper.get_appraisal_by_id(prop_id, county_info)

    return {
        "supported": False,
        "county": county,
        "city": "",
        "address": "",
        "propertyId": prop_id,
        "message": f"Property ID search is not yet supported for {county} County.",
        "years": [],
    }
