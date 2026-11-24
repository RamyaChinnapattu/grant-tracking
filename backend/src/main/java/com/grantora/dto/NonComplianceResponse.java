package com.grantora.dto;
import com.grantora.entity.NonComplianceStatus;
import java.time.LocalDateTime;
import java.util.List;
public record NonComplianceResponse(Long id, Long complianceId, Long applicationId, Long milestoneId,
        String milestoneTitle, String beneficiaryName, String reason, String severity, NonComplianceStatus status,
        LocalDateTime detectedAt, String officerRemarks,List<NonComplianceHistoryResponse> history) {}
