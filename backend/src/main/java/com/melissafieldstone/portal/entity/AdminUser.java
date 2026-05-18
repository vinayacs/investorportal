package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer adminId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

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

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
