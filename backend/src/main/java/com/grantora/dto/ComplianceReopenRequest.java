package com.grantora.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComplianceReopenRequest(
    @NotBlank(message = "A reason is required to reopen a rejected compliance submission.")
    @Size(max = 2000)
    String reason
) {}
