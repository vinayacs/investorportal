package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.entity.*;
import com.melissafieldstone.portal.repository.*;
import com.melissafieldstone.portal.security.JwtUtil;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int PENDING_EXPIRY_MINUTES = 5;

    private static final int TRUSTED_DEVICE_DAYS = 1;

    private final InvestorCredentialsRepository credentialsRepo;
    private final AdminUserRepository adminUserRepo;
    private final InvestorLoginLogRepository loginLogRepo;
    private final TrustedDeviceRepository trustedDeviceRepo;
    private final EmailService emailService;
    private final JwtUtil jwtUtil;

    // ── Login MFA initiation ──────────────────────────────────────────────────

    public LoginResponse initiateMfaForInvestor(InvestorCredentials cred) {
        boolean emailOn = Boolean.TRUE.equals(cred.getMfaEmailEnabled());
        boolean totpOn  = Boolean.TRUE.equals(cred.getMfaTotpEnabled());

        if (!emailOn && !totpOn) {
            // Inconsistent state: mfaEnabled flag is true but no method configured — reset and proceed without MFA
            cred.setMfaEnabled(false);
            credentialsRepo.save(cred);
            String jwt = jwtUtil.generateToken(cred.getUsername(), cred.getRole());
            Investor inv = cred.getInvestor();
            return new LoginResponse(jwt, cred.getRole(), inv.getFirstName(), inv.getLastName());
        }

        String pendingToken = UUID.randomUUID().toString();
        cred.setMfaPendingToken(pendingToken);
        cred.setMfaPendingExpiry(LocalDateTime.now().plusMinutes(PENDING_EXPIRY_MINUTES));

        String mfaType;
        if (emailOn && totpOn) {
            mfaType = "BOTH";
            credentialsRepo.save(cred);
        } else if (emailOn) {
            mfaType = "EMAIL";
            cred.setMfaSelectedMethod("EMAIL");
            sendEmailOtpToInvestor(cred);
        } else {
            mfaType = "TOTP";
            cred.setMfaSelectedMethod("TOTP");
            credentialsRepo.save(cred);
        }
        return LoginResponse.pending(pendingToken, mfaType);
    }

    public LoginResponse initiateMfaForAdmin(AdminUser admin) {
        boolean emailOn = Boolean.TRUE.equals(admin.getMfaEmailEnabled());
        boolean totpOn  = Boolean.TRUE.equals(admin.getMfaTotpEnabled());

        if (!emailOn && !totpOn) {
            admin.setMfaEnabled(false);
            adminUserRepo.save(admin);
            String jwt = jwtUtil.generateToken(admin.getEmail(), "ADMIN");
            return new LoginResponse(jwt, "ADMIN", admin.getName(), "");
        }

        String pendingToken = UUID.randomUUID().toString();
        admin.setMfaPendingToken(pendingToken);
        admin.setMfaPendingExpiry(LocalDateTime.now().plusMinutes(PENDING_EXPIRY_MINUTES));

        String mfaType;
        if (emailOn && totpOn) {
            mfaType = "BOTH";
            adminUserRepo.save(admin);
        } else if (emailOn) {
            mfaType = "EMAIL";
            admin.setMfaSelectedMethod("EMAIL");
            sendEmailOtpToAdmin(admin);
        } else {
            mfaType = "TOTP";
            admin.setMfaSelectedMethod("TOTP");
            adminUserRepo.save(admin);
        }
        return LoginResponse.pending(pendingToken, mfaType);
    }

    // ── Method selection (when both are enabled) ──────────────────────────────

    public void selectMethod(Map<String, String> body) {
        String mfaToken = body.get("mfaToken");
        String method   = body.get("method");
        if (mfaToken == null || method == null) throw new RuntimeException("Missing parameters.");

        var credOpt = credentialsRepo.findByMfaPendingToken(mfaToken);
        if (credOpt.isPresent()) {
            InvestorCredentials cred = credOpt.get();
            validatePendingExpiry(cred.getMfaPendingExpiry());
            validateMethodAvailable(method,
                    Boolean.TRUE.equals(cred.getMfaEmailEnabled()),
                    Boolean.TRUE.equals(cred.getMfaTotpEnabled()));
            cred.setMfaSelectedMethod(method);
            if ("EMAIL".equals(method)) sendEmailOtpToInvestor(cred);
            else credentialsRepo.save(cred);
            return;
        }

        var adminOpt = adminUserRepo.findByMfaPendingToken(mfaToken);
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            validatePendingExpiry(admin.getMfaPendingExpiry());
            validateMethodAvailable(method,
                    Boolean.TRUE.equals(admin.getMfaEmailEnabled()),
                    Boolean.TRUE.equals(admin.getMfaTotpEnabled()));
            admin.setMfaSelectedMethod(method);
            if ("EMAIL".equals(method)) sendEmailOtpToAdmin(admin);
            else adminUserRepo.save(admin);
            return;
        }
        throw new RuntimeException("Invalid or expired MFA session.");
    }

    // ── Login MFA verification ────────────────────────────────────────────────

    public LoginResponse verifyAndCompleteLogin(MfaVerifyRequest request) {
        String token = request.getMfaToken();
        String code  = request.getCode().trim();

        var credOpt = credentialsRepo.findByMfaPendingToken(token);
        if (credOpt.isPresent()) {
            InvestorCredentials cred = credOpt.get();
            if (cred.getMfaPendingExpiry().isBefore(LocalDateTime.now())) {
                clearInvestorPending(cred);
                throw new RuntimeException("MFA session expired. Please log in again.");
            }
            try {
                verifyCode(cred.getMfaSelectedMethod(), code,
                           cred.getMfaOtpCode(), cred.getMfaOtpExpiry(), cred.getTotpSecret());
            } catch (RuntimeException e) {
                logAttempt(cred.getInvestor(), null, "FAILED", "Invalid MFA code", "LOGIN");
                throw e;
            }
            clearInvestorPending(cred);
            credentialsRepo.save(cred);
            logAttempt(cred.getInvestor(), null, "SUCCESS", null, "LOGIN");
            String jwt = jwtUtil.generateToken(cred.getUsername(), cred.getRole());
            Investor inv = cred.getInvestor();
            LoginResponse response = new LoginResponse(jwt, cred.getRole(), inv.getFirstName(), inv.getLastName());
            if (request.isRememberDevice()) {
                response.setDeviceToken(createTrustedDevice(cred.getUsername()));
            }
            return response;
        }

        var adminOpt = adminUserRepo.findByMfaPendingToken(token);
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            if (admin.getMfaPendingExpiry().isBefore(LocalDateTime.now())) {
                clearAdminPending(admin);
                throw new RuntimeException("MFA session expired. Please log in again.");
            }
            verifyCode(admin.getMfaSelectedMethod(), code,
                       admin.getMfaOtpCode(), admin.getMfaOtpExpiry(), admin.getTotpSecret());
            clearAdminPending(admin);
            adminUserRepo.save(admin);
            String jwt = jwtUtil.generateToken(admin.getEmail(), "ADMIN");
            LoginResponse response = new LoginResponse(jwt, "ADMIN", admin.getName(), "");
            if (request.isRememberDevice()) {
                response.setDeviceToken(createTrustedDevice(admin.getEmail()));
            }
            return response;
        }
        throw new RuntimeException("Invalid or expired MFA session.");
    }

    public void resendOtp(String mfaToken) {
        var credOpt = credentialsRepo.findByMfaPendingToken(mfaToken);
        if (credOpt.isPresent()) {
            InvestorCredentials cred = credOpt.get();
            if (!"EMAIL".equals(cred.getMfaSelectedMethod())) throw new RuntimeException("Resend not applicable.");
            validatePendingExpiry(cred.getMfaPendingExpiry());
            sendEmailOtpToInvestor(cred);
            return;
        }
        var adminOpt = adminUserRepo.findByMfaPendingToken(mfaToken);
        if (adminOpt.isPresent()) {
            AdminUser admin = adminOpt.get();
            if (!"EMAIL".equals(admin.getMfaSelectedMethod())) throw new RuntimeException("Resend not applicable.");
            validatePendingExpiry(admin.getMfaPendingExpiry());
            sendEmailOtpToAdmin(admin);
            return;
        }
        throw new RuntimeException("Invalid or expired MFA session.");
    }

    // ── MFA status ────────────────────────────────────────────────────────────

    public MfaStatusResponse getStatusForInvestor(String username) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new MfaStatusResponse(
                Boolean.TRUE.equals(cred.getMfaEmailEnabled()),
                Boolean.TRUE.equals(cred.getMfaTotpEnabled()));
    }

    public MfaStatusResponse getStatusForAdmin(String email) {
        AdminUser admin = adminUserRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        return new MfaStatusResponse(
                Boolean.TRUE.equals(admin.getMfaEmailEnabled()),
                Boolean.TRUE.equals(admin.getMfaTotpEnabled()));
    }

    // ── TOTP setup ────────────────────────────────────────────────────────────

    public MfaSetupTotpResponse setupTotpForInvestor(String username) {
        credentialsRepo.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
        String secret = new DefaultSecretGenerator().generate();
        return new MfaSetupTotpResponse(secret, buildOtpauthUrl(username, secret));
    }

    public MfaSetupTotpResponse setupTotpForAdmin(String email) {
        adminUserRepo.findByEmail(email).orElseThrow(() -> new RuntimeException("Admin not found"));
        String secret = new DefaultSecretGenerator().generate();
        return new MfaSetupTotpResponse(secret, buildOtpauthUrl(email, secret));
    }

    // ── OTP send (email MFA setup / disable) ─────────────────────────────────

    public void sendOtpForInvestor(String username) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        sendEmailOtpToInvestor(cred);
    }

    public void sendOtpForAdmin(String email) {
        AdminUser admin = adminUserRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        sendEmailOtpToAdmin(admin);
    }

    // ── Enable MFA ────────────────────────────────────────────────────────────

    public void enableMfaForInvestor(String username, EnableMfaRequest request) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if ("EMAIL".equals(request.getType())) {
            validateEmailOtp(cred.getMfaOtpCode(), cred.getMfaOtpExpiry(), request.getCode());
            cred.setMfaEmailEnabled(true);
        } else if ("TOTP".equals(request.getType())) {
            if (request.getSecret() == null || !verifyTotp(request.getSecret(), request.getCode()))
                throw new RuntimeException("Invalid verification code.");
            cred.setTotpSecret(request.getSecret());
            cred.setMfaTotpEnabled(true);
        }
        cred.setMfaEnabled(true);
        cred.setMfaOtpCode(null);
        cred.setMfaOtpExpiry(null);
        credentialsRepo.save(cred);
    }

    public void enableMfaForAdmin(String email, EnableMfaRequest request) {
        AdminUser admin = adminUserRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        if ("EMAIL".equals(request.getType())) {
            validateEmailOtp(admin.getMfaOtpCode(), admin.getMfaOtpExpiry(), request.getCode());
            admin.setMfaEmailEnabled(true);
        } else if ("TOTP".equals(request.getType())) {
            if (request.getSecret() == null || !verifyTotp(request.getSecret(), request.getCode()))
                throw new RuntimeException("Invalid verification code.");
            admin.setTotpSecret(request.getSecret());
            admin.setMfaTotpEnabled(true);
        }
        admin.setMfaEnabled(true);
        admin.setMfaOtpCode(null);
        admin.setMfaOtpExpiry(null);
        adminUserRepo.save(admin);
    }

    // ── Disable MFA (per method) ──────────────────────────────────────────────

    public void disableMfaForInvestor(String username, DisableMfaRequest request) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String type = request.getType();
        String code = request.getCode().trim();
        if ("EMAIL".equals(type)) {
            if (!Boolean.TRUE.equals(cred.getMfaEmailEnabled()))
                throw new RuntimeException("Email MFA is not enabled.");
            validateEmailOtp(cred.getMfaOtpCode(), cred.getMfaOtpExpiry(), code);
            cred.setMfaEmailEnabled(false);
            cred.setMfaOtpCode(null);
            cred.setMfaOtpExpiry(null);
        } else if ("TOTP".equals(type)) {
            if (!Boolean.TRUE.equals(cred.getMfaTotpEnabled()))
                throw new RuntimeException("Authenticator MFA is not enabled.");
            if (!verifyTotp(cred.getTotpSecret(), code))
                throw new RuntimeException("Invalid verification code.");
            cred.setMfaTotpEnabled(false);
            cred.setTotpSecret(null);
        }
        cred.setMfaEnabled(Boolean.TRUE.equals(cred.getMfaEmailEnabled()) ||
                           Boolean.TRUE.equals(cred.getMfaTotpEnabled()));
        credentialsRepo.save(cred);
    }

    public void disableMfaForAdmin(String email, DisableMfaRequest request) {
        AdminUser admin = adminUserRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        String type = request.getType();
        String code = request.getCode().trim();
        if ("EMAIL".equals(type)) {
            if (!Boolean.TRUE.equals(admin.getMfaEmailEnabled()))
                throw new RuntimeException("Email MFA is not enabled.");
            validateEmailOtp(admin.getMfaOtpCode(), admin.getMfaOtpExpiry(), code);
            admin.setMfaEmailEnabled(false);
            admin.setMfaOtpCode(null);
            admin.setMfaOtpExpiry(null);
        } else if ("TOTP".equals(type)) {
            if (!Boolean.TRUE.equals(admin.getMfaTotpEnabled()))
                throw new RuntimeException("Authenticator MFA is not enabled.");
            if (!verifyTotp(admin.getTotpSecret(), code))
                throw new RuntimeException("Invalid verification code.");
            admin.setMfaTotpEnabled(false);
            admin.setTotpSecret(null);
        }
        admin.setMfaEnabled(Boolean.TRUE.equals(admin.getMfaEmailEnabled()) ||
                            Boolean.TRUE.equals(admin.getMfaTotpEnabled()));
        adminUserRepo.save(admin);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String createTrustedDevice(String username) {
        TrustedDevice device = new TrustedDevice();
        device.setUsername(username);
        device.setDeviceToken(UUID.randomUUID().toString());
        device.setCreatedAt(LocalDateTime.now());
        device.setExpiresAt(LocalDateTime.now().plusDays(TRUSTED_DEVICE_DAYS));
        trustedDeviceRepo.save(device);
        return device.getDeviceToken();
    }

    private void sendEmailOtpToInvestor(InvestorCredentials cred) {
        String otp = generateOtp();
        cred.setMfaOtpCode(otp);
        cred.setMfaOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        credentialsRepo.save(cred);
        emailService.sendMfaOtpEmail(cred.getUsername(), otp);
    }

    private void sendEmailOtpToAdmin(AdminUser admin) {
        String otp = generateOtp();
        admin.setMfaOtpCode(otp);
        admin.setMfaOtpExpiry(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        adminUserRepo.save(admin);
        emailService.sendMfaOtpEmail(admin.getEmail(), otp);
    }

    private void verifyCode(String method, String code, String storedOtp,
                            LocalDateTime otpExpiry, String totpSecret) {
        if (method == null) throw new RuntimeException("No MFA method selected. Please start login again.");
        if ("EMAIL".equals(method)) {
            validateEmailOtp(storedOtp, otpExpiry, code);
        } else if ("TOTP".equals(method)) {
            if (!verifyTotp(totpSecret, code))
                throw new RuntimeException("Invalid verification code.");
        }
    }

    private void validateEmailOtp(String storedOtp, LocalDateTime expiry, String code) {
        if (storedOtp == null || !storedOtp.equals(code))
            throw new RuntimeException("Invalid verification code.");
        if (expiry == null || expiry.isBefore(LocalDateTime.now()))
            throw new RuntimeException("Verification code has expired.");
    }

    private void validatePendingExpiry(LocalDateTime expiry) {
        if (expiry == null || expiry.isBefore(LocalDateTime.now()))
            throw new RuntimeException("MFA session expired. Please log in again.");
    }

    private void validateMethodAvailable(String method, boolean emailOn, boolean totpOn) {
        if ("EMAIL".equals(method) && !emailOn) throw new RuntimeException("Email MFA not enabled.");
        if ("TOTP".equals(method) && !totpOn)  throw new RuntimeException("Authenticator MFA not enabled.");
    }

    private boolean verifyTotp(String secret, String code) {
        try {
            CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
            return verifier.isValidCode(secret, code);
        } catch (Exception e) {
            return false;
        }
    }

    private String generateOtp() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private String buildOtpauthUrl(String account, String secret) {
        String issuer = "SmartRealtyTX";
        return "otpauth://totp/" + issuer + ":" + encode(account)
                + "?secret=" + secret
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void clearInvestorPending(InvestorCredentials cred) {
        cred.setMfaPendingToken(null);
        cred.setMfaPendingExpiry(null);
        cred.setMfaSelectedMethod(null);
        cred.setMfaOtpCode(null);
        cred.setMfaOtpExpiry(null);
    }

    private void clearAdminPending(AdminUser admin) {
        admin.setMfaPendingToken(null);
        admin.setMfaPendingExpiry(null);
        admin.setMfaSelectedMethod(null);
        admin.setMfaOtpCode(null);
        admin.setMfaOtpExpiry(null);
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
