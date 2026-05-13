package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer investmentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate anticipatedEndDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(columnDefinition = "TEXT")
    private String currentUpdate;

    @Column(nullable = false)
    private Integer projectProgress = 0;

    @OneToMany(mappedBy = "investment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<InvestmentDocument> documents = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "investor_investments",
        joinColumns = @JoinColumn(name = "investment_id"),
        inverseJoinColumns = @JoinColumn(name = "investor_id")
    )
    private List<Investor> investors = new ArrayList<>();
}
