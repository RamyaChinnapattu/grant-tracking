package com.grantora.repository;

import com.grantora.entity.ComplianceReviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplianceReviewHistoryRepository extends JpaRepository<ComplianceReviewHistory, Long> {
    List<ComplianceReviewHistory> findAllByOrderByOccurredAtDesc();
}
