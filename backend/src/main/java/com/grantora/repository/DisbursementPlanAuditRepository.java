package com.grantora.repository;

import com.grantora.entity.DisbursementPlanAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DisbursementPlanAuditRepository extends JpaRepository<DisbursementPlanAudit, Long> {
    List<DisbursementPlanAudit> findAllByOrderByOccurredAtDesc();
}
