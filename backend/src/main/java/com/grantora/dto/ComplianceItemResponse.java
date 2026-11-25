package com.grantora.dto;

import com.grantora.entity.ComplianceStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ComplianceItemResponse(
        Long id, Long requirementId, Long milestoneId, Long installmentId, String milestoneTitle,
        Long applicationId, Long schemeId, String schemeName, String beneficiaryName, String requirementTitle,
        String description, boolean required, LocalDate submissionDeadline, String allowedFormats, long maxFileSizeBytes,
        ComplianceStatus status, String documentFileName, String documentContentType, Long documentFileSize,
        boolean hasDocument, LocalDateTime submittedAt, String submittedByName, String verifiedByName,
        LocalDateTime verifiedAt, String verificationRemarks, List<ComplianceHistoryResponse> reviewHistory,
        Integer targetStage, boolean uploadAllowed,
        Integer currentReviewCycle, String currentReviewStage, List<ComplianceStageReviewResponse> stageReviews,
        List<ComplianceDocumentVersionResponse> documentVersions) {}
