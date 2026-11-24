package com.grantora.dto;

import jakarta.validation.constraints.NotNull;

public record ComplianceVerifyRequest(
    @NotNull(message = "Verification decision is required")
    Boolean approved,

    String remarks
) {}
