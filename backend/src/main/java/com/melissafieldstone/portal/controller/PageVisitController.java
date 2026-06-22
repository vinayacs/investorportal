package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.PageVisitRequest;
import com.melissafieldstone.portal.entity.PageVisit;
import com.melissafieldstone.portal.repository.PageVisitRepository;
import com.melissafieldstone.portal.service.GeoLocationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PageVisitController {

    private final PageVisitRepository pageVisitRepo;
    private final GeoLocationService geoLocationService;

    @PostMapping("/track")
    public ResponseEntity<Void> track(@RequestBody PageVisitRequest req, HttpServletRequest httpRequest) {
        String ip = extractClientIp(httpRequest);
        GeoLocationService.GeoLocation geo = geoLocationService.lookup(ip);

        PageVisit visit = new PageVisit();
        visit.setPage(req.getPage());
        visit.setIpAddress(ip);
        visit.setCity(geo.city());
        visit.setCountry(geo.country());
        visit.setRegion(geo.region());
        String ua = httpRequest.getHeader("User-Agent");
        visit.setUserAgent(ua != null ? ua.substring(0, Math.min(ua.length(), 512)) : null);
        visit.setVisitedAt(LocalDateTime.now());
        pageVisitRepo.save(visit);

        return ResponseEntity.ok().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }
}
