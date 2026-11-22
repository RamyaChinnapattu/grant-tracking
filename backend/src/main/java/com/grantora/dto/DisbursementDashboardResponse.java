package com.grantora.dto;
import java.math.BigDecimal;
import java.util.List;
public record DisbursementDashboardResponse(BigDecimal approvedAmount,BigDecimal scheduledAmount,BigDecimal releasedAmount,
        BigDecimal pendingDisbursement,int upcomingInstallments,int delayedMilestones,double complianceCompletionRate,
        int openNonComplianceCases,BigDecimal reportedUtilization,BigDecimal verifiedUtilization,
        long totalRows,int page,int pageSize,List<DisbursementDashboardRow> rows) {}
