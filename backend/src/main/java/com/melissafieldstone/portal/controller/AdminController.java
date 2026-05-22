package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.*;
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
    public ResponseEntity<Map> analyzeProperty(@RequestParam String address) {
        String url = propertyAgentUrl + "/analyze";
        Map result = restTemplate.postForObject(url, Map.of("address", address), Map.class);
        return ResponseEntity.ok(result);
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
