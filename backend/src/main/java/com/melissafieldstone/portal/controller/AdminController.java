package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.service.AdminService;
import com.melissafieldstone.portal.service.AuthService;
import com.melissafieldstone.portal.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final InvestmentService investmentService;
    private final AuthService authService;

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

    @PostMapping("/investments/{id}/documents")
    public ResponseEntity<InvestmentResponse> addDocument(@PathVariable Integer id,
                                                           @Valid @RequestBody AddDocumentRequest request) {
        return ResponseEntity.ok(investmentService.addDocument(id, request));
    }

    @PatchMapping("/investments/{id}/documents/{documentId}")
    public ResponseEntity<InvestmentResponse> updateDocument(@PathVariable Integer id,
                                                              @PathVariable Integer documentId,
                                                              @Valid @RequestBody AddDocumentRequest request) {
        return ResponseEntity.ok(investmentService.updateDocument(documentId, request));
    }

    @DeleteMapping("/investments/{id}/documents/{documentId}")
    public ResponseEntity<Map<String, String>> removeDocument(@PathVariable Integer id,
                                                               @PathVariable Integer documentId) {
        investmentService.removeDocument(documentId);
        return ResponseEntity.ok(Map.of("message", "Document removed."));
    }
}
