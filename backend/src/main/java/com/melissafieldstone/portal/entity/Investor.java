package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "investors")
public class Investor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer investorId;

    @Column(nullable = false)
    private String firstName;

    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_address_2")
    private String emailAddress2;
    private String phone;
    private String mailingAddress;
    private String beneficiaryName;
    private String beneficiaryPhone;
    private Integer numberOfShares;
    private String investmentSource;
    private String llcName;
    private String majorId;

    @Column(precision = 10, scale = 4)
    private BigDecimal companyShareHolding;

    private LocalDateTime updateDateTime;

    @ManyToMany(mappedBy = "investors", fetch = FetchType.LAZY)
    private List<Investment> investments = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void onUpdate() {
        this.updateDateTime = LocalDateTime.now();
    }
}
