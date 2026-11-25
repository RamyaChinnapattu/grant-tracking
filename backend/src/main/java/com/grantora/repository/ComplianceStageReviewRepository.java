package com.grantora.repository;

import com.grantora.entity.ComplianceStageReview;
import com.grantora.entity.VerificationStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceStageReviewRepository extends JpaRepository<ComplianceStageReview, Long> {

    List<ComplianceStageReview> findByCompliance_IdOrderByReviewCycleAscReviewedAtAsc(Long complianceId);

    List<ComplianceStageReview> findByCompliance_IdAndReviewCycle(Long complianceId, Integer reviewCycle);

    boolean existsByCompliance_IdAndReviewCycleAndStageAndDecision(
            Long complianceId, Integer reviewCycle, VerificationStage stage, String decision);
}
