package com.grantora.repository;

import com.grantora.entity.ComplianceDocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplianceDocumentVersionRepository extends JpaRepository<ComplianceDocumentVersion, Long> {

    List<ComplianceDocumentVersion> findByCompliance_IdOrderByVersionNumberAsc(Long complianceId);

    Optional<ComplianceDocumentVersion> findByCompliance_IdAndVersionNumber(Long complianceId, Integer versionNumber);
}
