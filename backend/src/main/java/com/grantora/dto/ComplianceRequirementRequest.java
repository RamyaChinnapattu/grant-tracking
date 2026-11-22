package com.grantora.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ComplianceRequirementRequest(
    @NotNull Long schemeId,
    Long milestoneId,
    Long applicationId,
    @NotBlank @Size(max=150) String title,
    String description,
    boolean required,
    @NotNull LocalDate submissionDeadline,
    @NotBlank String allowedFormats,
    @Min(1) @Max(10485760) long maxFileSizeBytes
) {}
