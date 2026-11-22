package com.grantora.dto;

import com.grantora.entity.NonComplianceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NonComplianceResolutionRequest(@NotNull NonComplianceStatus status, @Size(max=2000) String remarks) {}
