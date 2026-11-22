package com.grantora.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record GrantUtilizationRequest(@NotNull Long applicationId,@NotNull Long installmentId,
        @NotNull @DecimalMin(value="0.01") BigDecimal reportedAmount,@NotBlank @Size(max=100) String category,
        @Size(max=5000) String description,List<Long> supportingComplianceIds) {}
