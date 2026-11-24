package com.grantora.dto;
import com.grantora.entity.GrantUtilizationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record GrantUtilizationResponse(Long id,Long applicationId,Long installmentId,Integer installmentNumber,
        String schemeName,String beneficiaryName,BigDecimal installmentAmount,BigDecimal reportedAmount,String category,
        String description,List<Long> supportingComplianceIds,List<String> supportingDocumentNames,
        GrantUtilizationStatus status,LocalDateTime submittedAt,String submittedBy,LocalDateTime verifiedAt,
        String verifiedBy,String reviewRemarks,List<UtilizationHistoryResponse> history) {}
