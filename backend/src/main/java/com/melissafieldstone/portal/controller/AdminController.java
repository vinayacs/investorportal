package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.entity.ScraperLog;
import com.melissafieldstone.portal.repository.ScraperLogRepository;
import com.melissafieldstone.portal.service.AdminService;
import com.melissafieldstone.portal.service.AuthService;
import com.melissafieldstone.portal.service.InvestmentService;
import com.melissafieldstone.portal.service.MfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final InvestmentService investmentService;
    private final AuthService authService;
    private final RestTemplate restTemplate;
    private final ScraperLogRepository scraperLogRepo;

    @Value("${app.property-agent-url:http://property-agent:8000}")
    private String propertyAgentUrl;
    private final MfaService mfaService;

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(Authentication auth,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changeAdminPassword(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/investors")
    public ResponseEntity<List<InvestorResponse>> getAllInvestors() {
        return ResponseEntity.ok(adminService.getAllInvestors());
    }

    @PostMapping("/investors")
    public ResponseEntity<InvestorResponse> createInvestor(@Valid @RequestBody CreateInvestorRequest request) {
        return ResponseEntity.ok(adminService.createInvestor(request));
    }

    @GetMapping("/investors/{id}")
    public ResponseEntity<InvestorResponse> getInvestor(@PathVariable Integer id) {
        return ResponseEntity.ok(adminService.getInvestorById(id));
    }

    @PutMapping("/investors/{id}")
    public ResponseEntity<InvestorResponse> updateInvestor(@PathVariable Integer id,
                                                            @RequestBody InvestorUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateInvestor(id, request));
    }

    @PatchMapping("/investors/{id}/deactivate")
    public ResponseEntity<Map<String, String>> deactivateInvestor(@PathVariable Integer id) {
        adminService.deactivateInvestor(id);
        return ResponseEntity.ok(Map.of("message", "Investor deactivated."));
    }

    @GetMapping("/login-logs")
    public ResponseEntity<List<LoginLogResponse>> getLoginLogs() {
        return ResponseEntity.ok(adminService.getLoginLogs());
    }

    @GetMapping("/investments")
    public ResponseEntity<List<InvestmentResponse>> getAllInvestments() {
        return ResponseEntity.ok(investmentService.getAllInvestments());
    }

    @GetMapping("/investments/{id}")
    public ResponseEntity<InvestmentResponse> getInvestment(@PathVariable Integer id) {
        return ResponseEntity.ok(investmentService.getInvestmentById(id));
    }

    @PostMapping("/investments")
    public ResponseEntity<InvestmentResponse> createInvestment(@Valid @RequestBody CreateInvestmentRequest request) {
        return ResponseEntity.ok(investmentService.createInvestment(request));
    }

    @PutMapping("/investments/{id}")
    public ResponseEntity<InvestmentResponse> updateInvestment(@PathVariable Integer id,
                                                               @RequestBody CreateInvestmentRequest request) {
        return ResponseEntity.ok(investmentService.updateInvestment(id, request));
    }

    @DeleteMapping("/investments/{id}")
    public ResponseEntity<Map<String, String>> deleteInvestment(@PathVariable Integer id) {
        investmentService.deleteInvestment(id);
        return ResponseEntity.ok(Map.of("message", "Investment deleted."));
    }

    @PostMapping("/investments/{id}/documents/upload")
    public ResponseEntity<InvestmentResponse> uploadDocument(@PathVariable Integer id,
                                                              @RequestParam("name") String name,
                                                              @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(investmentService.uploadDocument(id, name, file));
    }

    @PostMapping("/investments/{id}/documents/link")
    public ResponseEntity<InvestmentResponse> linkDocument(@PathVariable Integer id,
                                                            @Valid @RequestBody AddDocumentRequest request) {
        return ResponseEntity.ok(investmentService.addDocument(id, request));
    }

    @PatchMapping("/investments/{id}/documents/{documentId}")
    public ResponseEntity<InvestmentResponse> updateDocument(@PathVariable Integer id,
                                                              @PathVariable Integer documentId,
                                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(investmentService.updateDocument(documentId, body.get("name")));
    }

    @DeleteMapping("/investments/{id}/documents/{documentId}")
    public ResponseEntity<Map<String, String>> removeDocument(@PathVariable Integer id,
                                                               @PathVariable Integer documentId) {
        investmentService.removeDocument(documentId);
        return ResponseEntity.ok(Map.of("message", "Document removed."));
    }

    // ── Property analysis ────────────────────────────────────────────────────

    @GetMapping("/property-analysis")
    public ResponseEntity<Map<String, Object>> analyzeProperty(
            @RequestParam(required = false) String address,
            @RequestParam(required = false) String propertyId,
            @RequestParam(required = false) String county) {

        long start = System.currentTimeMillis();
        Map<String, Object> result;
        String searchType;
        String input;

        if (propertyId != null && county != null) {
            searchType = "propertyId";
            input = propertyId + " (" + county + ")";
            String url = propertyAgentUrl + "/analyze-by-id";
            @SuppressWarnings("unchecked")
            Map<String, Object> r = restTemplate.postForObject(url,
                    Map.of("propertyId", propertyId, "county", county), Map.class);
            result = r;
        } else {
            searchType = "address";
            input = address;
            String url = propertyAgentUrl + "/analyze";
            @SuppressWarnings("unchecked")
            Map<String, Object> r = restTemplate.postForObject(url, Map.of("address", address), Map.class);
            result = r;
        }

        saveScraperLog(result, searchType, input, (int)(System.currentTimeMillis() - start));
        return ResponseEntity.ok(result);
    }

    private void saveScraperLog(Map<String, Object> result, String searchType, String input, int durationMs) {
        try {
            ScraperLog log = new ScraperLog();
            log.setTs(LocalDateTime.now());
            log.setSearchType(searchType);
            log.setInput(input);
            log.setDurationMs(durationMs);

            if (result != null) {
                log.setCounty(str(result.get("county")));
                log.setCity(str(result.get("city")));
                log.setPropertyId(str(result.get("propertyId")));

                @SuppressWarnings("unchecked")
                List<Object> years = (List<Object>) result.get("years");
                boolean success = years != null && !years.isEmpty();
                log.setSuccess(success);

                if (!success) {
                    log.setErrorMsg(str(result.get("message")));
                }
            } else {
                log.setSuccess(false);
                log.setErrorMsg("No response from property agent");
            }

            scraperLogRepo.save(log);
        } catch (Exception ignored) {}
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    // ── Scraper monitoring ───────────────────────────────────────────────────

    @GetMapping("/scraper-monitoring")
    public ResponseEntity<ScraperMonitoringResponse> getScraperMonitoring() {
        LocalDateTime since7d  = LocalDateTime.now().minusDays(7);
        LocalDateTime since30d = LocalDateTime.now().minusDays(30);

        List<ScraperLog> logs7d = scraperLogRepo.findSince(since7d);

        ScraperMonitoringResponse.Stats stats = new ScraperMonitoringResponse.Stats();
        stats.setTotal7d(logs7d.size());
        long success7d = logs7d.stream().filter(ScraperLog::isSuccess).count();
        stats.setSuccess7d(success7d);
        stats.setErrors7d(logs7d.size() - success7d);
        stats.setSuccessRate7d(logs7d.isEmpty() ? 0 : (double) success7d / logs7d.size());
        stats.setAvgDurationMs7d(logs7d.isEmpty() ? 0 :
                (long) logs7d.stream().mapToInt(ScraperLog::getDurationMs).average().orElse(0));

        List<Object[]> countyRaw = scraperLogRepo.countyStatsSince(since30d);
        List<ScraperMonitoringResponse.CountySummary> countySummary = new ArrayList<>();
        for (Object[] row : countyRaw) {
            ScraperMonitoringResponse.CountySummary cs = new ScraperMonitoringResponse.CountySummary();
            cs.setCounty(row[0] == null ? "Unknown" : row[0].toString());
            long total = ((Number) row[1]).longValue();
            long successes = ((Number) row[2]).longValue();
            cs.setTotal(total);
            cs.setSuccessCount(successes);
            cs.setSuccessRate(total == 0 ? 0 : (double) successes / total);
            cs.setAvgDurationMs(row[3] == null ? 0 : ((Number) row[3]).longValue());
            countySummary.add(cs);
        }

        List<ScraperLog> recent = scraperLogRepo.findTop100ByOrderByTsDesc();
        List<ScraperLogResponse> recentLogs = recent.stream().map(l -> {
            ScraperLogResponse r = new ScraperLogResponse();
            r.setId(l.getId());
            r.setTs(l.getTs());
            r.setCounty(l.getCounty());
            r.setCity(l.getCity());
            r.setSearchType(l.getSearchType());
            r.setInput(l.getInput());
            r.setSuccess(l.isSuccess());
            r.setDurationMs(l.getDurationMs());
            r.setPropertyId(l.getPropertyId());
            r.setErrorMsg(l.getErrorMsg());
            return r;
        }).toList();

        ScraperMonitoringResponse response = new ScraperMonitoringResponse();
        response.setStats(stats);
        response.setCountySummary(countySummary);
        response.setRecentLogs(recentLogs);
        return ResponseEntity.ok(response);
    }

    // ── Admin MFA management ──────────────────────────────────────────────────

    @GetMapping("/mfa/status")
    public ResponseEntity<MfaStatusResponse> getMfaStatus(Authentication auth) {
        return ResponseEntity.ok(mfaService.getStatusForAdmin(auth.getName()));
    }

    @PostMapping("/mfa/setup/totp")
    public ResponseEntity<MfaSetupTotpResponse> setupTotp(Authentication auth) {
        return ResponseEntity.ok(mfaService.setupTotpForAdmin(auth.getName()));
    }

    @PostMapping("/mfa/send-otp")
    public ResponseEntity<Void> sendMfaOtp(Authentication auth) {
        mfaService.sendOtpForAdmin(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<Void> enableMfa(Authentication auth,
                                          @Valid @RequestBody EnableMfaRequest request) {
        mfaService.enableMfaForAdmin(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> disableMfa(Authentication auth,
                                           @Valid @RequestBody DisableMfaRequest request) {
        mfaService.disableMfaForAdmin(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
