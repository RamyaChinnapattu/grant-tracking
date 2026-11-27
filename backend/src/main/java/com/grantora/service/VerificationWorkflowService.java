
package com.grantora.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grantora.dto.VerificationDecisionRequest;
import com.grantora.entity.ApplicationVerification;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.GrantApplication;
import com.grantora.entity.GrantApplicationStatus;
import com.grantora.entity.Role;
import com.grantora.entity.RoutingPriority;
import com.grantora.entity.User;
import com.grantora.entity.VerificationDecision;
import com.grantora.entity.VerificationRoutingRule;
import com.grantora.entity.VerificationStage;
import com.grantora.entity.WorkflowDeadlineConfig;
import com.grantora.repository.ApplicationDocumentRepository;
import com.grantora.repository.ApplicationVerificationRepository;
import com.grantora.repository.GrantApplicationRepository;
import com.grantora.repository.UserRepository;
import com.grantora.repository.VerificationRoutingRuleRepository;
import com.grantora.repository.WorkflowDeadlineConfigRepository;

@Service
@Transactional
public class VerificationWorkflowService {

    private static final List<GrantApplicationStatus> PENDING_REVIEW_STATUSES = List.of(
            GrantApplicationStatus.SUBMITTED,
            GrantApplicationStatus.UNDER_VERIFICATION,
            GrantApplicationStatus.FIELD_VERIFICATION,
            GrantApplicationStatus.DISTRICT_REVIEW,
            GrantApplicationStatus.FINANCE_REVIEW);

    private final GrantApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationVerificationRepository historyRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final VerificationRoutingRuleRepository routingRuleRepository;
    private WorkflowDeadlineConfigRepository deadlineConfigs;
    private WorkflowAutomationService automationService;
    private BeneficiaryEmailService beneficiaryEmailService;

    public VerificationWorkflowService(
            GrantApplicationRepository applicationRepository,
            UserRepository userRepository,
            ApplicationVerificationRepository historyRepository,
            ApplicationDocumentRepository documentRepository,
            VerificationRoutingRuleRepository routingRuleRepository) {

        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.documentRepository = documentRepository;
        this.routingRuleRepository = routingRuleRepository;
    }

    @Autowired
    public VerificationWorkflowService(
            GrantApplicationRepository a,
            UserRepository u,
            ApplicationVerificationRepository h,
            ApplicationDocumentRepository d,
            VerificationRoutingRuleRepository r,
            WorkflowDeadlineConfigRepository c) {

        this(a, u, h, d, r);
        this.deadlineConfigs = c;
    }

    @Autowired(required = false)
    public void setWorkflowAutomationService(WorkflowAutomationService automationService) {
        this.automationService = automationService;
    }

    @Autowired(required = false)
    public void setBeneficiaryEmailService(BeneficiaryEmailService beneficiaryEmailService) {
        this.beneficiaryEmailService = beneficiaryEmailService;
    }

    private void notifyBeneficiary(GrantApplication application, String keySuffix, String message) {
        if (automationService != null && application != null && application.getBeneficiary() != null) {
            User user = application.getBeneficiary().getUser();
            if (user != null) {
                String eventKey = "app:" + application.getId() + ":" + keySuffix;
                automationService.notify(user, eventKey, message, application.getId());
            }
        }
    }

    public GrantApplication submit(GrantApplication application, String email) {

        User beneficiary = getUser(email);

        if (application.getStatus() == GrantApplicationStatus.DRAFT) {

            GrantApplicationStatus previousStatus = application.getStatus();
            VerificationRoutingRule rule = matchingRule(application);

            User fieldOfficer = selectOfficer(
                    application, VerificationStage.FIELD, null);

            if (fieldOfficer == null) {
                throw conflict(
                        "No active Field Officer is configured for the beneficiary's state and district. Please configure a matching officer before submitting.");
            }

            application.setWorkflowStage(VerificationStage.FIELD);
            application.setAssignedOfficer(fieldOfficer);

            application.setRoutingPriority(
                    rule == null ? RoutingPriority.NORMAL : rule.getPriority());

            application.setEscalated(false);
            application.setStatus(GrantApplicationStatus.SUBMITTED);

            startTask(application, VerificationStage.FIELD);

            record(
                    application,
                    VerificationStage.FIELD,
                    "SUBMITTED",
                    beneficiary,
                    application.getAssignedOfficer(),
                    "Application submitted for field verification.",
                    previousStatus,
                    application.getStatus(),
                    VerificationStage.FIELD);

            record(
                    application,
                    VerificationStage.FIELD,
                    "ASSIGNED",
                    null,
                    application.getAssignedOfficer(),
                    "Automatically assigned based on beneficiary jurisdiction.",
                    application.getStatus(),
                    application.getStatus(),
                    VerificationStage.FIELD);

            GrantApplication saved = applicationRepository.save(application);
            String schemeName = (saved.getScheme() != null && saved.getScheme().getSchemeName() != null)
                    ? saved.getScheme().getSchemeName()
                    : "grant";
            notifyBeneficiary(
                    saved,
                    "submitted",
                    "Your grant application #" + saved.getId() + " for " + schemeName
                            + " has been submitted for field verification.");
            return saved;

        } else if (application.getStatus() == GrantApplicationStatus.NEEDS_CORRECTION
                && application.getWorkflowStage() != null) {

            GrantApplicationStatus previousStatus = application.getStatus();

            if (!isEligibleOfficer(
                    application.getAssignedOfficer(),
                    application,
                    application.getWorkflowStage())) {

                User replacementOfficer = selectOfficer(
                        application,
                        application.getWorkflowStage(),
                        null);

                if (replacementOfficer == null) {
                    throw conflict(
                            "No active officer is configured for the current verification stage and beneficiary jurisdiction. Please configure a matching officer before resubmitting.");
                }

                application.setAssignedOfficer(replacementOfficer);
            }

            VerificationRoutingRule rule = matchingRule(application);

            application.setRoutingPriority(
                    rule == null ? RoutingPriority.NORMAL : rule.getPriority());

            application.setEscalated(false);
            application.setStatus(GrantApplicationStatus.SUBMITTED);

            startTask(application, application.getWorkflowStage());

            record(
                    application,
                    application.getWorkflowStage(),
                    "RESUBMITTED",
                    beneficiary,
                    application.getAssignedOfficer(),
                    "Beneficiary submitted requested corrections.",
                    previousStatus,
                    application.getStatus(),
                    application.getWorkflowStage());

            GrantApplication saved = applicationRepository.save(application);
            notifyBeneficiary(
                    saved,
                    "resubmitted:" + System.currentTimeMillis(),
                    "Your corrections for application #" + saved.getId() + " have been resubmitted for verification.");
            return saved;

        } else {
            throw conflict("Only a draft or returned application can be submitted.");
        }
    }

    @Transactional(readOnly = true)
    public List<GrantApplication> getQueue(String email, boolean admin) {

        if (admin) {
            return applicationRepository.findAllByOrderByIdDesc().stream()
                    .filter(this::isAwaitingReview)
                    .sorted(queueOrder())
                    .toList();
        }

        User officer = requireOfficer(getUser(email));

        return applicationRepository
                .findByAssignedOfficer_IdAndStatusInOrderByIdDesc(
                        officer.getId(), PENDING_REVIEW_STATUSES)
                .stream()
                .filter(this::isAwaitingReview)
                .filter(application -> isEligibleOfficer(
                        officer, application, application.getWorkflowStage()))
                .sorted(queueOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GrantApplication> getHistory(String email, boolean admin) {

        return applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(application -> application.getWorkflowStage() != null)
                .filter(application -> canView(application, email, admin))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean canView(
            GrantApplication application,
            String email,
            boolean admin) {

        if (admin) {
            return true;
        }

        User user = getUser(email);

        if (user.getRole() != Role.OFFICER
                || !Boolean.TRUE.equals(user.getActive())) {
            return false;
        }

        if (isAwaitingReview(application)) {
            return application.getAssignedOfficer() != null
                    && Objects.equals(
                            application.getAssignedOfficer().getId(),
                            user.getId())
                    && isEligibleOfficer(
                            user, application, application.getWorkflowStage());
        }

        if (application.getAssignedOfficer() != null
                && Objects.equals(
                        application.getAssignedOfficer().getId(), user.getId())
                && application.getWorkflowStage() != null
                && isWithinStageJurisdiction(
                        user, application, application.getWorkflowStage())) {
            return true;
        }

        if (user.getOfficerLevel() == null) {
            return false;
        }

        if (application.getWorkflowStage() != null
                && isWithinStageJurisdiction(
                        user, application, application.getWorkflowStage())) {
            return true;
        }

        return historyRepository
                .findByApplication_IdOrderByCreatedAtAsc(application.getId())
                .stream()
                .anyMatch(item -> user.getOfficerLevel().equals(item.getStage().name())
                        && isWithinStageJurisdiction(
                                user, application, item.getStage()));
    }

    public GrantApplication decide(
            Long applicationId,
            String email,
            boolean admin,
            VerificationDecisionRequest request) {

        GrantApplication application = findApplication(applicationId);
        User actor = getUser(email);

        requireDecisionAccess(application, actor, admin);

        VerificationStage stage = application.getWorkflowStage();
        VerificationDecision decision = request.decision();

        validateDecision(stage, decision, request.remarks());

        if (stage == VerificationStage.FIELD
                && decision == VerificationDecision.VERIFIED) {

            if (!request.identityVerified()
                    || !request.addressVerified()
                    || !request.documentsVerified()
                    || !request.applicationDetailsVerified()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Confirm identity, address, documents, and application details before verification.");
            }

            if (documentRepository.countByApplication_Id(applicationId) == 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "At least one supporting document must be uploaded before field verification.");
            }
        }

        GrantApplicationStatus previousStatus = application.getStatus();
        User currentAssignee = application.getAssignedOfficer();

        VerificationStage assignedStage = null;
        User nextOfficer = null;

        if (decision == VerificationDecision.NEEDS_CORRECTION) {

            application.setStatus(GrantApplicationStatus.NEEDS_CORRECTION);

        } else if (decision == VerificationDecision.REJECTED) {

            application.setStatus(GrantApplicationStatus.REJECTED);
            application.setAssignedOfficer(null);
            application.setTaskCompletedAt(LocalDateTime.now());

        } else if (stage == VerificationStage.FIELD) {

            application.setTaskCompletedAt(LocalDateTime.now());

            assignedStage = VerificationStage.DISTRICT;
            nextOfficer = advance(application, assignedStage);

        } else if (stage == VerificationStage.DISTRICT) {

            application.setTaskCompletedAt(LocalDateTime.now());

            // Every application proceeds to Finance.
            assignedStage = VerificationStage.FINANCE;
            nextOfficer = advance(application, assignedStage);

        } else if (stage == VerificationStage.FINANCE) {

            // Verify prior stages were completed before granting final finance approval
            List<ApplicationVerification> history = historyRepository
                    .findByApplication_IdOrderByCreatedAtAsc(applicationId);
            if (history != null && !history.isEmpty()) {
                boolean hasFieldVerification = history.stream()
                        .anyMatch(h -> h.getStage() == VerificationStage.FIELD
                                && ("VERIFIED".equalsIgnoreCase(h.getAction())
                                        || VerificationDecision.VERIFIED.name().equalsIgnoreCase(h.getAction())));
                boolean hasDistrictApproval = history.stream()
                        .anyMatch(h -> h.getStage() == VerificationStage.DISTRICT
                                && ("APPROVED".equalsIgnoreCase(h.getAction())
                                        || VerificationDecision.APPROVED.name().equalsIgnoreCase(h.getAction())));
                if (!hasFieldVerification || !hasDistrictApproval) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Cannot grant Finance approval: Field verification and District review must be completed first.");
                }
            }

            application.setTaskCompletedAt(LocalDateTime.now());
            application.setStatus(GrantApplicationStatus.APPROVED);
            application.setAssignedOfficer(null);
        }

        recordChecklist(
                application,
                stage,
                decision,
                actor,
                currentAssignee,
                request,
                previousStatus,
                application.getStatus(),
                isFinalStatus(application.getStatus())
                        ? null
                        : application.getWorkflowStage());

        if (assignedStage != null) {
            record(
                    application,
                    assignedStage,
                    "ASSIGNED",
                    null,
                    nextOfficer,
                    "Automatically advanced to "
                            + assignedStage.name().toLowerCase() + " review.",
                    application.getStatus(),
                    application.getStatus(),
                    assignedStage);
        }

        GrantApplication saved = applicationRepository.save(application);

        if (decision == VerificationDecision.NEEDS_CORRECTION) {
            notifyBeneficiary(
                    saved,
                    "correction:" + System.currentTimeMillis(),
                    "Action Required: Corrections requested on application #" + saved.getId() + ". Remarks: " + request.remarks());
            if (beneficiaryEmailService != null) {
                beneficiaryEmailService.sendCorrectionRequiredEmail(saved, request.remarks());
            }
        } else if (decision == VerificationDecision.REJECTED) {
            notifyBeneficiary(
                    saved,
                    "rejected:" + System.currentTimeMillis(),
                    "Your grant application #" + saved.getId() + " was not approved. Remarks: " + request.remarks());
            if (beneficiaryEmailService != null) {
                beneficiaryEmailService.sendFinalDecisionEmail(saved, false, null, request.remarks());
            }
        } else if (stage == VerificationStage.FIELD) {
            notifyBeneficiary(
                    saved,
                    "field_verified:" + System.currentTimeMillis(),
                    "Field verification completed for application #" + saved.getId() + ". It has advanced to District Review.");
            if (beneficiaryEmailService != null) {
                beneficiaryEmailService.sendStatusUpdateEmail(saved, VerificationStage.FIELD,
                        "Field verification completed for application #" + saved.getId() + ". It has advanced to District Review.");
            }
        } else if (stage == VerificationStage.DISTRICT) {
            notifyBeneficiary(
                    saved,
                    "district_verified:" + System.currentTimeMillis(),
                    "District review completed for application #" + saved.getId() + ". It has advanced to Finance Approval.");
            if (beneficiaryEmailService != null) {
                beneficiaryEmailService.sendStatusUpdateEmail(saved, VerificationStage.DISTRICT,
                        "District review completed for application #" + saved.getId() + ". It has advanced to Finance Approval.");
            }
        } else if (stage == VerificationStage.FINANCE) {
            notifyBeneficiary(
                    saved,
                    "approved:" + System.currentTimeMillis(),
                    "Congratulations! Your grant application #" + saved.getId() + " has received final Finance Approval and is queued for disbursement.");
            if (beneficiaryEmailService != null) {
                beneficiaryEmailService.sendFinalDecisionEmail(saved, true, saved.getRequestedAmount(), request.remarks());
            }
        }

        return saved;
    }

    public GrantApplication assign(
            Long applicationId,
            Long officerId,
            String email,
            boolean admin) {

        if (!admin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Admin access required.");
        }

        GrantApplication application = findApplication(applicationId);

        if (!isAwaitingReview(application)) {
            throw conflict("Only an active verification application can be assigned.");
        }

        User actor = getUser(email);

        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Officer not found."));

        if (!isEligibleOfficer(
                officer, application, application.getWorkflowStage())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The selected officer must be active and match the current stage and jurisdiction.");
        }

        GrantApplicationStatus status = application.getStatus();

        application.setAssignedOfficer(officer);

        if (application.getTaskAssignedAt() == null) {
            startTask(application, application.getWorkflowStage());
        }

        application.setEscalated(false);

        record(
                application,
                application.getWorkflowStage(),
                "ASSIGNED",
                actor,
                officer,
                "Application assigned by Admin.",
                status,
                status,
                application.getWorkflowStage());

        return applicationRepository.save(application);
    }

    public GrantApplication escalate(
            Long applicationId,
            String email,
            boolean admin) {

        GrantApplication application = findApplication(applicationId);
        User actor = getUser(email);

        if (!isAwaitingReview(application)) {
            throw conflict("Only an active verification application can be escalated.");
        }

        if (!admin) {
            requireDecisionAccess(application, actor, false);
        }

        GrantApplicationStatus status = application.getStatus();
        User previous = application.getAssignedOfficer();

        User next = selectOfficer(
                application, application.getWorkflowStage(), previous);

        if (next == null) {
            throw conflict(
                    "No alternate active officer is configured for this stage and jurisdiction.");
        }

        application.setAssignedOfficer(next);
        application.setEscalated(true);

        record(
                application,
                application.getWorkflowStage(),
                "ESCALATED",
                actor,
                next,
                "Application escalated and reassigned within the current verification level.",
                status,
                status,
                application.getWorkflowStage());

        return applicationRepository.save(application);
    }

    private User advance(
            GrantApplication application,
            VerificationStage nextStage) {

        User nextOfficer = selectOfficer(application, nextStage, null);

        if (nextOfficer == null) {
            throw conflict(
                    "No active "
                            + nextStage.name()
                            + " Officer is configured for the beneficiary's jurisdiction. The application cannot advance until a matching officer is configured.");
        }

        application.setWorkflowStage(nextStage);
        application.setAssignedOfficer(nextOfficer);
        application.setEscalated(false);
        application.setStatus(GrantApplicationStatus.UNDER_VERIFICATION);

        startTask(application, nextStage);

        return nextOfficer;
    }

    private void startTask(
            GrantApplication application,
            VerificationStage stage) {

        int days = 5;

        if (deadlineConfigs != null) {
            WorkflowDeadlineConfig config = deadlineConfigs.findById(1L)
                    .orElseGet(() -> deadlineConfigs.save(new WorkflowDeadlineConfig()));

            days = config.days(stage);
        }

        LocalDateTime now = LocalDateTime.now();

        application.setTaskAssignedAt(now);
        application.setTaskDueAt(now.plusDays(days));
        application.setTaskCompletedAt(null);
    }

    private void requireDecisionAccess(
            GrantApplication application,
            User actor,
            boolean admin) {

        if (!isAwaitingReview(application)) {
            throw conflict("This application is not awaiting verification.");
        }

        if (application.getBeneficiary() != null
                && application.getBeneficiary().getUser() != null
                && Objects.equals(application.getBeneficiary().getUser().getId(), actor.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Officers cannot verify or approve their own grant applications.");
        }

        if (admin) {
            if (application.getWorkflowStage() != VerificationStage.FINANCE) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Admin can make a final decision only at the Finance review stage.");
            }

            return;
        }

        User officer = requireOfficer(actor);

        String expectedLevel = application.getWorkflowStage().name();

        if (!expectedLevel.equals(officer.getOfficerLevel())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This officer is not authorized for this review level.");
        }

        if (application.getAssignedOfficer() == null
                || !Objects.equals(
                        application.getAssignedOfficer().getId(),
                        officer.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "This application is assigned to another officer.");
        }

        if (!isWithinStageJurisdiction(
                officer, application, application.getWorkflowStage())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Application is outside the officer's jurisdiction.");
        }
    }

    private void validateDecision(
            VerificationStage stage,
            VerificationDecision decision,
            String remarks) {

        boolean allowed = switch (stage) {

            case FIELD ->
                decision == VerificationDecision.VERIFIED
                        || decision == VerificationDecision.REJECTED
                        || decision == VerificationDecision.NEEDS_CORRECTION;

            case DISTRICT ->
                decision == VerificationDecision.APPROVED
                        || decision == VerificationDecision.REJECTED
                        || decision == VerificationDecision.NEEDS_CORRECTION;

            case FINANCE ->
                decision == VerificationDecision.APPROVED
                        || decision == VerificationDecision.REJECTED;
        };

        if (!allowed) {
            throw conflict(
                    "Decision is not valid for the current verification level.");
        }

        if ((decision == VerificationDecision.REJECTED
                || decision == VerificationDecision.NEEDS_CORRECTION)
                && (remarks == null || remarks.isBlank())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Remarks are required for rejection or correction.");
        }
    }

    private User selectOfficer(
            GrantApplication application,
            VerificationStage stage,
            User exclude) {

        Beneficiary beneficiary = application.getBeneficiary();

        List<User> allEligible = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.OFFICER)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> stage.name().equals(user.getOfficerLevel()))
                .filter(user -> same(
                        user.getJurisdictionState(), beneficiary.getState()))
                .filter(user -> stage == VerificationStage.FINANCE
                        || same(
                                user.getJurisdictionDistrict(),
                                beneficiary.getDistrict()))
                .sorted(Comparator.comparing(User::getId))
                .toList();

        if (exclude == null) {
            if (allEligible.isEmpty()) {
                return null;
            }

            return allEligible.get(
                    Math.floorMod(
                            application.getId().intValue(),
                            allEligible.size()));
        }

        List<User> alternatives = allEligible.stream()
                .filter(user -> !Objects.equals(
                        user.getId(), exclude.getId()))
                .toList();

        if (alternatives.isEmpty()) {
            return null;
        }

        return alternatives.get(
                Math.floorMod(
                        application.getId().intValue(),
                        alternatives.size()));
    }

    private boolean isWithinStageJurisdiction(
            User user,
            GrantApplication application,
            VerificationStage stage) {

        if (user.getOfficerLevel() == null
                || !stage.name().equals(user.getOfficerLevel())) {
            return false;
        }

        Beneficiary beneficiary = application.getBeneficiary();

        return same(
                user.getJurisdictionState(), beneficiary.getState())
                && (stage == VerificationStage.FINANCE
                        || same(
                                user.getJurisdictionDistrict(),
                                beneficiary.getDistrict()));
    }

    private boolean isEligibleOfficer(
            User officer,
            GrantApplication application,
            VerificationStage stage) {

        return officer != null
                && officer.getRole() == Role.OFFICER
                && Boolean.TRUE.equals(officer.getActive())
                && isWithinStageJurisdiction(
                        officer, application, stage);
    }

    private VerificationRoutingRule matchingRule(
            GrantApplication application) {

        BigDecimal configuredScore = application.getEligibilityEvaluation() == null
                ? BigDecimal.ZERO
                : application.getEligibilityEvaluation().scoreOutOf100();

        BigDecimal score = configuredScore == null
                ? BigDecimal.ZERO
                : configuredScore;

        BigDecimal amount = application.getRequestedAmount();

        return routingRuleRepository.findAllByOrderByIdAsc().stream()
                .filter(rule -> score.compareTo(rule.getMinimumScore()) >= 0
                        && score.compareTo(rule.getMaximumScore()) <= 0)
                .filter(rule -> amount.compareTo(rule.getMinimumAmount()) >= 0
                        && (rule.getMaximumAmount() == null
                                || amount.compareTo(
                                        rule.getMaximumAmount()) <= 0))
                .findFirst()
                .orElse(null);
    }

    private void record(
            GrantApplication application,
            VerificationStage stage,
            String action,
            User actor,
            User assignedOfficer,
            String remarks,
            GrantApplicationStatus previousStatus,
            GrantApplicationStatus newStatus,
            VerificationStage nextStage) {

        ApplicationVerification entry = new ApplicationVerification();

        entry.setApplication(application);
        entry.setStage(stage);
        entry.setPreviousStatus(previousStatus);
        entry.setNewStatus(newStatus);
        entry.setNextStage(nextStage);
        entry.setAction(action);
        entry.setActor(actor);
        entry.setAssignedOfficer(assignedOfficer);

        entry.setRemarks(
                remarks == null || remarks.isBlank()
                        ? null
                        : remarks.trim());

        historyRepository.save(entry);
    }

    private void recordChecklist(
            GrantApplication application,
            VerificationStage stage,
            VerificationDecision decision,
            User actor,
            User assignedOfficer,
            VerificationDecisionRequest request,
            GrantApplicationStatus previousStatus,
            GrantApplicationStatus newStatus,
            VerificationStage nextStage) {

        ApplicationVerification entry = new ApplicationVerification();

        entry.setApplication(application);
        entry.setStage(stage);
        entry.setPreviousStatus(previousStatus);
        entry.setNewStatus(newStatus);
        entry.setNextStage(nextStage);
        entry.setAction(decision.name());
        entry.setActor(actor);
        entry.setAssignedOfficer(assignedOfficer);

        entry.setRemarks(
                request.remarks() == null || request.remarks().isBlank()
                        ? null
                        : request.remarks().trim());

        entry.setIdentityVerified(request.identityVerified());
        entry.setAddressVerified(request.addressVerified());
        entry.setDocumentsVerified(request.documentsVerified());
        entry.setApplicationDetailsVerified(
                request.applicationDetailsVerified());

        historyRepository.save(entry);
    }

    private boolean isAwaitingReview(GrantApplication application) {

        if (application.getWorkflowStage() == null
                || !PENDING_REVIEW_STATUSES.contains(application.getStatus())) {
            return false;
        }

        return application.getStatus() == GrantApplicationStatus.SUBMITTED
                || application.getStatus() == GrantApplicationStatus.UNDER_VERIFICATION
                || application.getStatus() == statusFor(application.getWorkflowStage());
    }

    private boolean isFinalStatus(GrantApplicationStatus status) {
        return status == GrantApplicationStatus.APPROVED
                || status == GrantApplicationStatus.REJECTED;
    }

    private GrantApplication findApplication(Long id) {
        return applicationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found"));
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    private User requireOfficer(User user) {
        if (user.getRole() != Role.OFFICER
                || user.getOfficerLevel() == null
                || !Boolean.TRUE.equals(user.getActive())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "An active verification officer is required.");
        }

        return user;
    }

    private boolean same(String left, String right) {

        if (left == null || right == null) {
            return false;
        }

        String normalizedLeft = left
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase(Locale.ROOT);

        String normalizedRight = right
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase(Locale.ROOT);

        return normalizedLeft.equals(normalizedRight);
    }

    private Comparator<GrantApplication> queueOrder() {
        return Comparator.comparing(
                GrantApplication::isEscalated).reversed()
                .thenComparing(
                        application -> application.getRoutingPriority() == null
                                ? RoutingPriority.NORMAL.ordinal()
                                : application.getRoutingPriority().ordinal(),
                        Comparator.reverseOrder())
                .thenComparing(
                        GrantApplication::getId,
                        Comparator.reverseOrder());
    }

    private GrantApplicationStatus statusFor(VerificationStage stage) {
        return switch (stage) {
            case FIELD -> GrantApplicationStatus.FIELD_VERIFICATION;
            case DISTRICT -> GrantApplicationStatus.DISTRICT_REVIEW;
            case FINANCE -> GrantApplicationStatus.FINANCE_REVIEW;
        };
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message);
    }
}
