package com.melissafieldstone.portal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class GeoLocationService {

    private final RestTemplate restTemplate;

    public GeoLocationService() {
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();
    }

    public record GeoLocation(String city, String country, String region) {}

    public GeoLocation lookup(String ip) {
        if (ip == null || ip.isBlank() || isPrivateIp(ip)) {
            return new GeoLocation("Local", "Local", "Local");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    "http://ip-api.com/json/" + ip + "?fields=status,city,country,regionName",
                    Map.class
            );
            if (response != null && "success".equals(response.get("status"))) {
                return new GeoLocation(
                        (String) response.getOrDefault("city", "Unknown"),
                        (String) response.getOrDefault("country", "Unknown"),
                        (String) response.getOrDefault("regionName", "Unknown")
                );
            }
        } catch (Exception e) {
            log.warn("Geo lookup failed for IP {}: {}", ip, e.getMessage());
        }
        return new GeoLocation("Unknown", "Unknown", "Unknown");
    }

    private boolean isPrivateIp(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("::1")
                || ip.startsWith("10.")
                || ip.startsWith("172.")
                || ip.startsWith("192.168.");
    }
}
