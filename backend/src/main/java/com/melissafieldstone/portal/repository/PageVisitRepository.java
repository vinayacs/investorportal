package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.PageVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PageVisitRepository extends JpaRepository<PageVisit, Long> {
    List<PageVisit> findTop200ByOrderByVisitedAtDesc();
}
