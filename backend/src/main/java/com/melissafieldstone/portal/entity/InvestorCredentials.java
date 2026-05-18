package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "investor_credentials")
public class InvestorCredentials {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer credentialId;

    @OneToOne
    @JoinColumn(name = "investor_id", nullable = false)
    private Investor investor;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role = "INVESTOR";

    @Column(nullable = false)
    private Boolean isActive = true;

    private String resetToken;
    private LocalDateTime resetTokenExpiry;

    @Column(nullable = false)
    private Boolean mfaEnabled = false;

    @Column(nullable = false)
    private Boolean mfaEmailEnabled = false;

    @Column(nullable = false)
    private Boolean mfaTotpEnabled = false;

    private String mfaSelectedMethod;
    private String totpSecret;
    private String mfaOtpCode;
    private LocalDateTime mfaOtpExpiry;
    private String mfaPendingToken;
    private LocalDateTime mfaPendingExpiry;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
