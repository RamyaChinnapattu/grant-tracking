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
    @NotNull(message = "Max file size is required")
    @Min(value = 1024, message = "Max file size must be at least 1 KB (1024 bytes)")
    @Max(value = 10485760, message = "Max file size cannot exceed 10 MB (10485760 bytes)")
    Long maxFileSizeBytes
) {}
