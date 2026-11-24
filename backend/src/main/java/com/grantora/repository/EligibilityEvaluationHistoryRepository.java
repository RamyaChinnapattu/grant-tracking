package com.grantora.repository;

import com.grantora.entity.EligibilityEvaluationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EligibilityEvaluationHistoryRepository extends JpaRepository<EligibilityEvaluationHistory, Long> {
    List<EligibilityEvaluationHistory> findByApplication_IdOrderByEvaluatedAtDesc(Long applicationId);
}
