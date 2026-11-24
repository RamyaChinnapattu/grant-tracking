package com.grantora.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ComplianceRequirementRequest(
    @NotNull(message = "Target scheme is required") Long schemeId,
    Long milestoneId,
    Long applicationId,
    @NotBlank(message = "Requirement title is required") @Size(max=150, message = "Requirement title must not exceed 150 characters") String title,
    String description,
    boolean required,
    LocalDate submissionDeadline,
    @NotBlank(message = "Allowed file formats are required") String allowedFormats,
    @Min(value = 1, message = "Max file size must be at least 1 byte") @Max(value = 10485760, message = "Max file size cannot exceed 10 MB") long maxFileSizeBytes
) {}
