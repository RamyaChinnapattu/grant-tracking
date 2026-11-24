package com.grantora.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BeneficiaryRequest(
        LocalDate dateOfBirth,
        BigDecimal annualIncome,
        String address,
        String state,
        String district,
        String category,
        String phone,
        String fullName
) {
    public BeneficiaryRequest(
            LocalDate dateOfBirth,
            BigDecimal annualIncome,
            String address,
            String state,
            String district,
            String category,
            String phone) {
        this(dateOfBirth, annualIncome, address, state, district, category, phone, null);
    }
}
