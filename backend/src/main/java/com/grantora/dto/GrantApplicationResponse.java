
package com.grantora.dto;

import com.grantora.entity.GrantApplicationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GrantApplicationResponse(
                Long id,
                Long beneficiaryId,
                String beneficiaryName,
                Long schemeId,
                String schemeName,
                String applicationDetails,
                BigDecimal requestedAmount,
                GrantApplicationStatus status,
                LocalDateTime submittedAt,
                EligibilityEvaluationResult eligibilityEvaluation,
                String workflowStage,
                Long assignedOfficerId,
                String assignedOfficerName,
                String routingPriority,
                boolean escalated,
                List<VerificationHistoryResponse> verificationHistory,
                List<GrantApplicationStatusHistoryResponse> statusHistory,
                boolean withdrawalAllowed,
                List<ApplicationDocumentResponse> documents,
                BeneficiaryVerificationDetails beneficiaryDetails,
                BigDecimal eligibilityThreshold,
                ApplicationDisbursementSummary disbursementSummary) {

        public GrantApplicationResponse(
                        Long id,
                        Long beneficiaryId,
                        String beneficiaryName,
                        Long schemeId,
                        String schemeName,
                        String applicationDetails,
                        BigDecimal requestedAmount,
                        GrantApplicationStatus status,
                        LocalDateTime submittedAt,
                        EligibilityEvaluationResult eligibilityEvaluation,
                        String workflowStage,
                        Long assignedOfficerId,
                        String assignedOfficerName,
                        String routingPriority,
                        boolean escalated,
                        List<VerificationHistoryResponse> verificationHistory,
                        List<GrantApplicationStatusHistoryResponse> statusHistory,
                        boolean withdrawalAllowed,
                        List<ApplicationDocumentResponse> documents,
                        BeneficiaryVerificationDetails beneficiaryDetails,
                        BigDecimal eligibilityThreshold) {
                this(
                                id,
                                beneficiaryId,
                                beneficiaryName,
                                schemeId,
                                schemeName,
                                applicationDetails,
                                requestedAmount,
                                status,
                                submittedAt,
                                eligibilityEvaluation,
                                workflowStage,
                                assignedOfficerId,
                                assignedOfficerName,
                                routingPriority,
                                escalated,
                                verificationHistory,
                                statusHistory,
                                withdrawalAllowed,
                                documents,
                                beneficiaryDetails,
                                eligibilityThreshold,
                                null);
        }
}
