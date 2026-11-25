package com.grantora.dto;

import com.grantora.entity.ComplianceStatus;
import java.time.LocalDateTime;

public record MilestoneComplianceResponse(
    Long id,
    Long milestoneId,
    String milestoneTitle,
    String requirementTitle,
    String description,
    String submissionNotes,
    ComplianceStatus status,
    String documentFileName,
    String documentContentType,
    Long documentFileSize,
    boolean hasDocument,
    LocalDateTime submittedAt,
    String submittedByName,
    String verifiedByName,
    LocalDateTime verifiedAt,
    String verificationRemarks,
    boolean required
) {}
