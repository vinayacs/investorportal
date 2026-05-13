package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.entity.*;
import com.melissafieldstone.portal.repository.*;
import com.melissafieldstone.portal.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final InvestorCredentialsRepository credentialsRepo;
    private final AdminUserRepository adminUserRepo;
    private final InvestorLoginLogRepository loginLogRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        // Try investor login
        var credOpt = credentialsRepo.findByUsername(request.getUsername());
        if (credOpt.isPresent()) {
            InvestorCredentials cred = credOpt.get();
            if (!cred.getIsActive()) {
                logAttempt(cred.getInvestor(), ip, "FAILED", "Account inactive", "LOGIN");
                throw new RuntimeException("Account is inactive");
            }
            if (!passwordEncoder.matches(request.getPassword(), cred.getPasswordHash())) {
                logAttempt(cred.getInvestor(), ip, "FAILED", "Invalid password", "LOGIN");
                throw new RuntimeException("Invalid credentials");
            }
            logAttempt(cred.getInvestor(), ip, "SUCCESS", null, "LOGIN");
            String token = jwtUtil.generateToken(cred.getUsername(), cred.getRole());
            Investor inv = cred.getInvestor();
            return new LoginResponse(token, cred.getRole(), inv.getFirstName(), inv.getLastName());
        }

        // Try admin login
        var adminOpt = adminUserRepo.findByEmail(request.getUsername());
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            if (!admin.getIsActive()) throw new RuntimeException("Account is inactive");
            if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
                throw new RuntimeException("Invalid credentials");
            }
            String token = jwtUtil.generateToken(admin.getEmail(), "ADMIN");
            return new LoginResponse(token, "ADMIN", admin.getName(), "");
        }

        throw new RuntimeException("Invalid credentials");
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        credentialsRepo.findByUsername(request.getEmail()).ifPresent(cred -> {
            String token = UUID.randomUUID().toString();
            cred.setResetToken(token);
            cred.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            credentialsRepo.save(cred);
            emailService.sendPasswordResetEmail(request.getEmail(), token);
        });
        // Silently succeed even if email not found (security best practice)
    }

    public void changeAdminPassword(String email, ChangePasswordRequest request) {
        AdminUser admin = adminUserRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }
        admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        adminUserRepo.save(admin);
    }

    public void changePassword(String username, ChangePasswordRequest request) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), cred.getPasswordHash())) {
            throw new RuntimeException("Current password is incorrect");
        }
        cred.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        credentialsRepo.save(cred);
        logAttempt(cred.getInvestor(), null, "SUCCESS", null, "PASSWORD_CHANGE");
    }

    public void resetPassword(ResetPasswordRequest request) {
        InvestorCredentials cred = credentialsRepo.findByResetToken(request.getToken())
                .orElseThrow(() -> new RuntimeException("Invalid or expired token"));
        if (cred.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token has expired");
        }
        cred.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        cred.setResetToken(null);
        cred.setResetTokenExpiry(null);
        credentialsRepo.save(cred);
        logAttempt(cred.getInvestor(), null, "SUCCESS", null, "PASSWORD_RESET");
    }

    private void logAttempt(Investor investor, String ip, String status, String reason, String action) {
        InvestorLoginLog log = new InvestorLoginLog();
        log.setInvestor(investor);
        log.setIpAddress(ip);
        log.setStatus(status);
        log.setFailureReason(reason);
        log.setAction(action);
        log.setLoginTimestamp(LocalDateTime.now());
        loginLogRepo.save(log);
    }
}
