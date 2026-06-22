package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "investor_login_logs")
public class InvestorLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer logId;

    @ManyToOne
    @JoinColumn(name = "investor_id")
    private Investor investor;

    @Column(nullable = false)
    private LocalDateTime loginTimestamp = LocalDateTime.now();

    private String ipAddress;
    private String city;
    private String country;
    private String region;

    @Column(nullable = false)
    private String status;

    private String failureReason;

    private String action;
}
