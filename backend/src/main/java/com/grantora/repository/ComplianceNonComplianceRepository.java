package com.grantora.repository;
import com.grantora.entity.ComplianceNonCompliance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ComplianceNonComplianceRepository extends JpaRepository<ComplianceNonCompliance, Long> {
    Optional<ComplianceNonCompliance> findByCompliance_Id(Long complianceId);
    List<ComplianceNonCompliance> findAllByOrderByDetectedAtDesc();
}
