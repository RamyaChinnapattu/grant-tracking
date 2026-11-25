package com.grantora.dto;
import java.time.LocalDate;
public record ComplianceRequirementResponse(Long id, Long schemeId, String schemeName, Long milestoneId,
        Long applicationId, Integer targetStage, String title, String description, boolean required, LocalDate submissionDeadline,
        String allowedFormats, long maxFileSizeBytes, boolean active) {}
