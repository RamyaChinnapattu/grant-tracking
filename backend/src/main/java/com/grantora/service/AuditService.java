package com.grantora.service;

import com.grantora.dto.AuditLogResponse;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditService {

    private final SystemAuditLogRepository systemAuditLogRepository;
    private final GrantApplicationStatusHistoryRepository statusHistoryRepository;
    private final ApplicationVerificationRepository verificationRepository;
    private final DisbursementPlanAuditRepository planAuditRepository;
    private final ComplianceReviewHistoryRepository complianceReviewHistoryRepository;
    private final GrantApplicationRepository applicationRepository;

    public AuditService(
            SystemAuditLogRepository systemAuditLogRepository,
            GrantApplicationStatusHistoryRepository statusHistoryRepository,
            ApplicationVerificationRepository verificationRepository,
            DisbursementPlanAuditRepository planAuditRepository,
            ComplianceReviewHistoryRepository complianceReviewHistoryRepository,
            GrantApplicationRepository applicationRepository) {
        this.systemAuditLogRepository = systemAuditLogRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.verificationRepository = verificationRepository;
        this.planAuditRepository = planAuditRepository;
        this.complianceReviewHistoryRepository = complianceReviewHistoryRepository;
        this.applicationRepository = applicationRepository;
    }

    public void recordAudit(
            String action,
            String category,
            String targetEntity,
            Long targetId,
            String details) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorEmail = (auth != null && auth.getName() != null) ? auth.getName() : "system";
        String actorRole = "SYSTEM";
        if (auth != null && auth.getAuthorities() != null && !auth.getAuthorities().isEmpty()) {
            actorRole = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }

        SystemAuditLog log = new SystemAuditLog();
        log.setActorEmail(actorEmail);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setCategory(category);
        log.setTargetEntity(targetEntity);
        log.setTargetId(targetId);
        log.setDetails(details);
        log.setTimestamp(LocalDateTime.now());

        systemAuditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> getUnifiedAuditLogs() {
        List<AuditLogResponse> unifiedLogs = new ArrayList<>();

        // 1. System Administrative & Scheme Audits
        List<SystemAuditLog> systemLogs = systemAuditLogRepository.findAllByOrderByTimestampDesc();
        for (SystemAuditLog sys : systemLogs) {
            unifiedLogs.add(new AuditLogResponse(
                    "SYS-" + sys.getId(),
                    sys.getCategory(),
                    sys.getAction(),
                    null,
                    null,
                    null,
                    sys.getActorEmail(),
                    sys.getActorEmail(),
                    sys.getActorRole(),
                    null,
                    null,
                    sys.getTargetEntity() + (sys.getTargetId() != null ? " #" + sys.getTargetId() : ""),
                    sys.getTargetId(),
                    sys.getDetails(),
                    sys.getTimestamp()
            ));
        }

        // Cache applications for metadata resolution to prevent N+1 queries
        Map<Long, GrantApplication> appCache = applicationRepository.findAll()
                .stream()
                .collect(Collectors.toMap(GrantApplication::getId, a -> a, (a1, a2) -> a1));

        // Set to track deduplication of verification decisions
        Set<String> verificationDecisionKeys = new HashSet<>();

        // 2. Application Verification History (Field, District, Finance Reviews & Escalations)
        List<ApplicationVerification> verifications = verificationRepository.findAllByOrderByCreatedAtDesc();
        for (ApplicationVerification ver : verifications) {
            Long appId = ver.getApplication() != null ? ver.getApplication().getId() : null;
            GrantApplication app = appId != null ? appCache.get(appId) : null;
            String schemeName = (app != null && app.getScheme() != null) ? app.getScheme().getSchemeName() : null;
            String beneficiaryName = resolveBeneficiaryName(app);

            String actorName = ver.getActor() != null ? ver.getActor().getFullName() : "Reviewing Officer";
            String actorEmail = ver.getActor() != null ? ver.getActor().getEmail() : null;
            String actorRole = ver.getActor() != null && ver.getActor().getOfficerLevel() != null
                    ? ver.getActor().getOfficerLevel()
                    : (ver.getActor() != null ? ver.getActor().getRole().name() : (ver.getStage() != null ? ver.getStage().name() : "OFFICER"));

            // Record verification deduplication key
            if (appId != null && ver.getCreatedAt() != null) {
                verificationDecisionKeys.add(appId + "-" + ver.getAction() + "-" + ver.getCreatedAt().toLocalDate());
            }

            unifiedLogs.add(new AuditLogResponse(
                    "VER-" + ver.getId(),
                    "VERIFICATION",
                    ver.getAction() != null ? ver.getAction() : (ver.getStage() != null ? ver.getStage().name() + "_VERIFICATION" : "VERIFICATION"),
                    appId,
                    schemeName,
                    beneficiaryName,
                    actorName,
                    actorEmail,
                    actorRole,
                    ver.getPreviousStatus() != null ? ver.getPreviousStatus().name() : null,
                    ver.getNewStatus() != null ? ver.getNewStatus().name() : null,
                    "Application #" + appId,
                    appId,
                    ver.getRemarks(),
                    ver.getCreatedAt()
            ));
        }

        // 3. Application Lifecycle Status History (Created, Submitted, Resubmitted, Withdrawn, etc.)
        List<GrantApplicationStatusHistory> statusHistories = statusHistoryRepository.findAllByOrderByCreatedAtDesc();
        for (GrantApplicationStatusHistory hist : statusHistories) {
            Long appId = hist.getApplicationId();
            // Deduplicate if already recorded in verification history
            String dedupKey = appId + "-" + hist.getAction() + "-" + (hist.getCreatedAt() != null ? hist.getCreatedAt().toLocalDate() : "");
            if (verificationDecisionKeys.contains(dedupKey)) {
                continue;
            }

            GrantApplication app = appId != null ? appCache.get(appId) : null;
            String schemeName = (app != null && app.getScheme() != null) ? app.getScheme().getSchemeName() : null;
            String beneficiaryName = resolveBeneficiaryName(app);

            String actorName = hist.getActor() != null ? hist.getActor().getFullName() : "System";
            String actorEmail = hist.getActor() != null ? hist.getActor().getEmail() : null;
            String actorRole = hist.getActor() != null ? hist.getActor().getRole().name() : "SYSTEM";

            unifiedLogs.add(new AuditLogResponse(
                    "APP-" + hist.getId(),
                    "APPLICATION",
                    hist.getAction(),
                    appId,
                    schemeName,
                    beneficiaryName,
                    actorName,
                    actorEmail,
                    actorRole,
                    hist.getFromStatus() != null ? hist.getFromStatus().name() : null,
                    hist.getToStatus() != null ? hist.getToStatus().name() : null,
                    "Application #" + appId,
                    appId,
                    hist.getRemarks(),
                    hist.getCreatedAt()
            ));
        }

        // 4. Financial Disbursement Plan Audits (Plan Created, Deactivated, Installment Released)
        List<DisbursementPlanAudit> planAudits = planAuditRepository.findAllByOrderByOccurredAtDesc();
        for (DisbursementPlanAudit planAudit : planAudits) {
            DisbursementPlan plan = planAudit.getPlan();
            GrantApplication app = plan != null ? plan.getApplication() : null;
            Long appId = app != null ? app.getId() : null;
            String schemeName = (app != null && app.getScheme() != null) ? app.getScheme().getSchemeName() : null;
            String beneficiaryName = resolveBeneficiaryName(app);

            String actorName = planAudit.getActor() != null ? planAudit.getActor().getFullName() : "Finance Officer";
            String actorEmail = planAudit.getActor() != null ? planAudit.getActor().getEmail() : null;
            String actorRole = planAudit.getActor() != null ? planAudit.getActor().getRole().name() : "ADMIN";

            unifiedLogs.add(new AuditLogResponse(
                    "FIN-" + planAudit.getId(),
                    "FINANCIAL",
                    planAudit.getAction(),
                    appId,
                    schemeName,
                    beneficiaryName,
                    actorName,
                    actorEmail,
                    actorRole,
                    null,
                    null,
                    "Disbursement Plan #" + (plan != null ? plan.getId() : ""),
                    plan != null ? plan.getId() : null,
                    planAudit.getDetails(),
                    planAudit.getOccurredAt()
            ));
        }

        // 5. Compliance Review History (Submission, Review, Approval, Rejection, Waiver)
        List<ComplianceReviewHistory> complianceHistories = complianceReviewHistoryRepository.findAllByOrderByOccurredAtDesc();
        for (ComplianceReviewHistory compHist : complianceHistories) {
            MilestoneCompliance comp = compHist.getCompliance();
            DisbursementMilestone milestone = comp != null ? comp.getMilestone() : null;
            DisbursementInstallment installment = milestone != null ? milestone.getInstallment() : null;
            DisbursementPlan plan = installment != null ? installment.getPlan() : null;
            GrantApplication app = plan != null ? plan.getApplication() : null;

            Long appId = app != null ? app.getId() : null;
            String schemeName = (app != null && app.getScheme() != null) ? app.getScheme().getSchemeName() : null;
            String beneficiaryName = resolveBeneficiaryName(app);

            String actorName = compHist.getActor() != null ? compHist.getActor().getFullName() : "Officer";
            String actorEmail = compHist.getActor() != null ? compHist.getActor().getEmail() : null;
            String actorRole = compHist.getActor() != null ? compHist.getActor().getRole().name() : "OFFICER";

            String remarks = compHist.getRemarks();
            if (compHist.getDocumentFileName() != null && !compHist.getDocumentFileName().isBlank()) {
                remarks = (remarks != null ? remarks + " " : "") + "[File: " + compHist.getDocumentFileName() + "]";
            }

            unifiedLogs.add(new AuditLogResponse(
                    "COMP-" + compHist.getId(),
                    "COMPLIANCE",
                    compHist.getAction(),
                    appId,
                    schemeName,
                    beneficiaryName,
                    actorName,
                    actorEmail,
                    actorRole,
                    null,
                    null,
                    "Compliance #" + (comp != null ? comp.getId() : "") + (milestone != null ? " (" + milestone.getTitle() + ")" : ""),
                    comp != null ? comp.getId() : null,
                    remarks,
                    compHist.getOccurredAt()
            ));
        }

        // Sort chronologically descending
        unifiedLogs.sort((a, b) -> {
            if (a.timestamp() == null && b.timestamp() == null) return 0;
            if (a.timestamp() == null) return 1;
            if (b.timestamp() == null) return -1;
            int timeCompare = b.timestamp().compareTo(a.timestamp());
            if (timeCompare != 0) return timeCompare;
            return b.id().compareTo(a.id());
        });

        return unifiedLogs;
    }

    private String resolveBeneficiaryName(GrantApplication app) {
        if (app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
            return app.getBeneficiary().getUser().getFullName();
        }
        return null;
    }
}
