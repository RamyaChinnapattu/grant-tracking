package com.grantora.dto;

import java.time.LocalDateTime;

public record ComplianceStageReviewResponse(
    Integer reviewCycle,
    String stage,
    String decision,
    Long reviewerId,
    String reviewerName,
    String reviewerOfficerLevel,
    String remarks,
    String documentFileName,
    Integer documentVersionNumber,
    LocalDateTime reviewedAt
) {}
