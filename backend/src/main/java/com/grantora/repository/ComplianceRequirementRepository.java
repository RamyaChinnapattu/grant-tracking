package com.grantora.repository;
import com.grantora.entity.ComplianceRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ComplianceRequirementRepository extends JpaRepository<ComplianceRequirement, Long> {
    List<ComplianceRequirement> findAllByOrderByCreatedAtDesc();
    List<ComplianceRequirement> findByActiveTrue();
}
