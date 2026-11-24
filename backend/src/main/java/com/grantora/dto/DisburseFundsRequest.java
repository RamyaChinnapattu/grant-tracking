package com.grantora.dto;

import jakarta.validation.constraints.NotBlank;

public record DisburseFundsRequest(
    @NotBlank(message = "Payment reference / transaction ID is required")
    String paymentReference,

    @NotBlank(message = "Payment method is required")
    String paymentMethod,

    String remarks
) {}
