package com.grantora.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplianceReviewRequest(
    @NotBlank String decision,
    @Size(max=2000) String remarks
) {}
