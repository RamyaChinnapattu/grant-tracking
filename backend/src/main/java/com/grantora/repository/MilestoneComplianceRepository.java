package com.grantora.repository;

import com.grantora.entity.ComplianceStatus;
import com.grantora.entity.MilestoneCompliance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MilestoneComplianceRepository extends JpaRepository<MilestoneCompliance, Long> {

    List<MilestoneCompliance> findByMilestone_Id(Long milestoneId);

    List<MilestoneCompliance> findByMilestone_Installment_Id(Long installmentId);

    List<MilestoneCompliance> findByStatus(ComplianceStatus status);

    long countByMilestone_Installment_IdAndStatusNot(Long installmentId, ComplianceStatus status);

    long countByMilestone_IdAndStatusNot(Long milestoneId, ComplianceStatus status);
}
