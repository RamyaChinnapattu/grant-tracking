package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DisbursementService {

    private final DisbursementPlanRepository planRepository;
    private final DisbursementInstallmentRepository installmentRepository;
    private final DisbursementMilestoneRepository milestoneRepository;
    private final MilestoneComplianceRepository complianceRepository;
    private final ComplianceRequirementRepository requirementRepository;
    private final DisbursementReminderConfigRepository reminderConfigRepository;
    private final GrantApplicationRepository applicationRepository;
    private final SchemeRepository schemeRepository;
    private final UserRepository userRepository;
    private final WorkflowAutomationService workflowAutomationService;

    public DisbursementService(
            DisbursementPlanRepository planRepository,
            DisbursementInstallmentRepository installmentRepository,
            DisbursementMilestoneRepository milestoneRepository,
            MilestoneComplianceRepository complianceRepository,
            DisbursementReminderConfigRepository reminderConfigRepository,
            GrantApplicationRepository applicationRepository,
            SchemeRepository schemeRepository,
            UserRepository userRepository,
            WorkflowAutomationService workflowAutomationService) {
        this(planRepository, installmentRepository, milestoneRepository, complianceRepository, null,
                reminderConfigRepository, applicationRepository, schemeRepository, userRepository, workflowAutomationService);
    }

    @Autowired
    public DisbursementService(
            DisbursementPlanRepository planRepository,
            DisbursementInstallmentRepository installmentRepository,
            DisbursementMilestoneRepository milestoneRepository,
            MilestoneComplianceRepository complianceRepository,
            ComplianceRequirementRepository requirementRepository,
            DisbursementReminderConfigRepository reminderConfigRepository,
            GrantApplicationRepository applicationRepository,
            SchemeRepository schemeRepository,
            UserRepository userRepository,
            WorkflowAutomationService workflowAutomationService) {
        this.planRepository = planRepository;
        this.installmentRepository = installmentRepository;
        this.milestoneRepository = milestoneRepository;
        this.complianceRepository = complianceRepository;
        this.requirementRepository = requirementRepository;
        this.reminderConfigRepository = reminderConfigRepository;
        this.applicationRepository = applicationRepository;
        this.schemeRepository = schemeRepository;
        this.userRepository = userRepository;
        this.workflowAutomationService = workflowAutomationService;
    }

    // ==========================================
    // FEATURE 1: DISBURSEMENT PLAN CONFIGURATION
    // ==========================================

    public DisbursementPlanResponse createPlan(DisbursementPlanRequest request, String adminEmail) {
        User admin = getUser(adminEmail);
        if (admin.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can configure disbursement plans.");
        }

        GrantApplication application = applicationRepository.findById(request.applicationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found."));

        if (application.getStatus() != GrantApplicationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Disbursement plans can only be created for applications with APPROVED status.");
        }

        if (planRepository.existsByApplication_Id(application.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An active disbursement plan already exists for this application.");
        }

        if (request.installments() == null || request.installments().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one installment stage is required to create a disbursement plan.");
        }

        BigDecimal approvedAmount = application.getRequestedAmount();
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approved grant amount.");
        }

        Scheme scheme = application.getScheme();
        if (scheme != null) {
            Scheme lockedScheme = scheme.getId() != null
                    ? schemeRepository.findByIdForUpdate(scheme.getId()).orElse(scheme)
                    : scheme;
            if (lockedScheme.getMaximumGrantAmount() != null && approvedAmount.compareTo(lockedScheme.getMaximumGrantAmount()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Approved grant amount (₹%s) exceeds scheme maximum grant limit of ₹%s.",
                                approvedAmount.toPlainString(), lockedScheme.getMaximumGrantAmount().toPlainString()));
            }
            if (lockedScheme.getAllocatedFunds() != null) {
                List<DisbursementPlan> existingPlans = planRepository.findByScheme_Id(lockedScheme.getId());
                BigDecimal totalCommitted = existingPlans.stream()
                        .filter(p -> p.getPlanStatus() != DisbursementPlanStatus.CANCELLED)
                        .map(DisbursementPlan::getTotalApprovedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal availableBudget = lockedScheme.getAllocatedFunds().subtract(totalCommitted);
                if (approvedAmount.compareTo(availableBudget) > 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            String.format("Approved grant amount (₹%s) exceeds available scheme budget (₹%s remaining out of ₹%s total).",
                                    approvedAmount.toPlainString(), availableBudget.toPlainString(), lockedScheme.getAllocatedFunds().toPlainString()));
                }
            }
        }

        // Validate installments
        BigDecimal sumAmounts = BigDecimal.ZERO;
        for (InstallmentRequest instReq : request.installments()) {
            if (instReq.title() == null || instReq.title().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Installment titles are required.");
            }
            if (instReq.percentage() == null && (instReq.amount() == null || instReq.amount().compareTo(BigDecimal.ZERO) <= 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Installment amounts must be greater than zero.");
            }
            if (instReq.percentage() == null) sumAmounts = sumAmounts.add(instReq.amount());
        }

        Set<Integer> sequences = new HashSet<>();
        BigDecimal sumPercentages = BigDecimal.ZERO;
        boolean hasPercentage = false;
        for (InstallmentRequest item : request.installments()) {
            if (item.installmentNumber() == null || item.installmentNumber() < 1 || !sequences.add(item.installmentNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Installment sequence numbers must be positive and unique.");
            }
            if (item.percentage() != null) { hasPercentage = true; sumPercentages = sumPercentages.add(item.percentage()); }
        }
        int totalStages = request.installments().size();
        for (int seq = 1; seq <= totalStages; seq++) {
            if (!sequences.contains(seq)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Installment sequence numbers must be consecutive starting from 1 to " + totalStages + ".");
            }
        }
        if (hasPercentage && request.installments().stream().anyMatch(item -> item.percentage() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A plan must use one installment calculation method throughout.");
        }
        if (hasPercentage && sumPercentages.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage-based installments must total 100%.");
        }
        if (hasPercentage) {
            sumAmounts = BigDecimal.ZERO;
            BigDecimal scheduled = BigDecimal.ZERO;
            int finalSequence = request.installments().stream().mapToInt(InstallmentRequest::installmentNumber).max().orElse(0);
            for (InstallmentRequest item : request.installments()) {
                if (item.percentage().compareTo(BigDecimal.ZERO) <= 0 || item.percentage().compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Installment percentages must be greater than zero and at most 100%.");
                }
                BigDecimal installmentAmount = item.installmentNumber() == finalSequence
                        ? approvedAmount.subtract(scheduled)
                        : approvedAmount.multiply(item.percentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                scheduled = scheduled.add(installmentAmount);
            }
            sumAmounts = scheduled;
        }
        if (sumAmounts.compareTo(approvedAmount) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Total scheduled installment amount (₹%s) must equal approved grant amount (₹%s).",
                            sumAmounts.toPlainString(), approvedAmount.toPlainString()));
        }

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        plan.setScheme(application.getScheme());
        plan.setTotalApprovedAmount(approvedAmount);
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        plan.setNotes(request.notes());
        plan.setCreatedBy(admin);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.addAuditEntry(new DisbursementPlanAudit(plan, admin, "CREATED", "Created disbursement schedule."));

        int instIndex = 1;
        for (InstallmentRequest instReq : request.installments()) {
            DisbursementInstallment installment = new DisbursementInstallment();
            installment.setPlan(plan);
            installment.setInstallmentNumber(instReq.installmentNumber() != null ? instReq.installmentNumber() : instIndex);
            installment.setTitle(instReq.title());
            BigDecimal installmentAmount = instReq.percentage() != null
                    ? (instReq.installmentNumber() == request.installments().stream().mapToInt(InstallmentRequest::installmentNumber).max().orElse(0)
                        ? approvedAmount.subtract(request.installments().stream()
                            .filter(item -> item.percentage() != null && item.installmentNumber() < instReq.installmentNumber())
                            .map(item -> approvedAmount.multiply(item.percentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                            .reduce(BigDecimal.ZERO, BigDecimal::add))
                        : approvedAmount.multiply(instReq.percentage()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    : instReq.amount();
            installment.setAmount(installmentAmount);

            BigDecimal pct = instReq.percentage();
            if (pct == null || pct.compareTo(BigDecimal.ZERO) <= 0) {
                pct = installmentAmount.multiply(BigDecimal.valueOf(100))
                        .divide(approvedAmount, 2, RoundingMode.HALF_UP);
            }
            installment.setPercentage(pct);
            installment.setPlannedReleaseDate(instReq.plannedReleaseDate() != null
                    ? instReq.plannedReleaseDate()
                    : LocalDate.now().plusMonths(instIndex - 1L));

            installment.setStatus(InstallmentStatus.PLANNED);

            if (instReq.milestones() != null && !instReq.milestones().isEmpty()) {
                for (MilestoneRequest mReq : instReq.milestones()) {
                    DisbursementMilestone milestone = createMilestoneEntity(installment, mReq);
                    installment.addMilestone(milestone);
                }
            } else {
                // Generate a default milestone for this installment
                DisbursementMilestone milestone = new DisbursementMilestone();
                milestone.setInstallment(installment);
                milestone.setTitle("Stage " + installment.getInstallmentNumber() + " Deliverables & Utilization");
                milestone.setDescription("Execution and compliance submission for Stage " + installment.getInstallmentNumber());
                milestone.setDueDate(installment.getPlannedReleaseDate());
                milestone.setAmount(installment.getAmount());
                milestone.setStatus(MilestoneStatus.PLANNED);
                milestone.setProgressPercentage(0);
                milestone.setNonCompliant(false);

                List<ComplianceRequirement> adminReqs = findMatchingRequirementsForApplication(scheme, application);
                if (!adminReqs.isEmpty()) {
                    for (ComplianceRequirement req : adminReqs) {
                        MilestoneCompliance compliance = new MilestoneCompliance();
                        compliance.setMilestone(milestone);
                        compliance.setRequirement(req);
                        compliance.setRequirementTitle(req.getTitle());
                        compliance.setDescription(req.getDescription());
                        compliance.setRequired(req.isRequired());
                        compliance.setSubmissionDeadline(req.getSubmissionDeadline() != null ? req.getSubmissionDeadline() : milestone.getDueDate());
                        compliance.setStatus(ComplianceStatus.PENDING);
                        milestone.addCompliance(compliance);
                    }
                } else {
                    MilestoneCompliance compliance = new MilestoneCompliance();
                    compliance.setMilestone(milestone);
                    compliance.setRequirementTitle("Stage " + installment.getInstallmentNumber() + " Utilization Certificate & Invoices");
                    compliance.setDescription("Submit expenditure verification documents and progress report");
                    compliance.setStatus(ComplianceStatus.PENDING);
                    milestone.addCompliance(compliance);
                }

                installment.addMilestone(milestone);
            }

            plan.addInstallment(installment);
            instIndex++;
        }

        DisbursementPlan saved = planRepository.save(plan);

        // Notify beneficiary
        if (application.getBeneficiary() != null && application.getBeneficiary().getUser() != null) {
            workflowAutomationService.notify(
                    application.getBeneficiary().getUser(),
                    "disbursement_plan_created:" + saved.getId(),
                    "Disbursement plan of ₹" + approvedAmount.toPlainString() + " has been configured for Application #" + application.getId(),
                    application.getId()
            );
        }

        return toPlanResponse(saved);
    }

    private DisbursementMilestone createMilestoneEntity(DisbursementInstallment installment, MilestoneRequest mReq) {
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setInstallment(installment);
        milestone.setTitle(mReq.title());
        milestone.setDescription(mReq.description());
        milestone.setDueDate(mReq.dueDate() != null ? mReq.dueDate() : installment.getPlannedReleaseDate());
        milestone.setAmount(mReq.amount() != null ? mReq.amount() : installment.getAmount());
        milestone.setStatus(MilestoneStatus.PLANNED);
        milestone.setProgressPercentage(0);
        milestone.setNonCompliant(false);

        if (mReq.requirementTitles() != null && !mReq.requirementTitles().isEmpty()) {
            for (String reqTitle : mReq.requirementTitles()) {
                if (reqTitle != null && !reqTitle.isBlank()) {
                    MilestoneCompliance compliance = new MilestoneCompliance();
                    compliance.setMilestone(milestone);
                    compliance.setRequirementTitle(reqTitle.trim());
                    compliance.setStatus(ComplianceStatus.PENDING);
                    milestone.addCompliance(compliance);
                }
            }
        } else {
            Scheme planScheme = installment.getPlan() != null ? installment.getPlan().getScheme() : null;
            GrantApplication planApp = installment.getPlan() != null ? installment.getPlan().getApplication() : null;
            List<ComplianceRequirement> adminReqs = findMatchingRequirementsForApplication(planScheme, planApp);
            if (!adminReqs.isEmpty()) {
                for (ComplianceRequirement req : adminReqs) {
                    MilestoneCompliance compliance = new MilestoneCompliance();
                    compliance.setMilestone(milestone);
                    compliance.setRequirement(req);
                    compliance.setRequirementTitle(req.getTitle());
                    compliance.setDescription(req.getDescription());
                    compliance.setRequired(req.isRequired());
                    compliance.setSubmissionDeadline(req.getSubmissionDeadline() != null ? req.getSubmissionDeadline() : milestone.getDueDate());
                    compliance.setStatus(ComplianceStatus.PENDING);
                    milestone.addCompliance(compliance);
                }
            } else {
                MilestoneCompliance defaultCompliance = new MilestoneCompliance();
                defaultCompliance.setMilestone(milestone);
                defaultCompliance.setRequirementTitle("Milestone Completion Certificate");
                defaultCompliance.setStatus(ComplianceStatus.PENDING);
                milestone.addCompliance(defaultCompliance);
            }
        }

        return milestone;
    }

    private List<ComplianceRequirement> findMatchingRequirementsForApplication(Scheme scheme, GrantApplication application) {
        if (requirementRepository == null || scheme == null) {
            return Collections.emptyList();
        }
        return requirementRepository.findByActiveTrue().stream()
                .filter(r -> r != null && r.getScheme() != null && Objects.equals(r.getScheme().getId(), scheme.getId()))
                .filter(r -> r.getApplication() == null || (application != null && Objects.equals(r.getApplication().getId(), application.getId())))
                .filter(r -> r.getMilestone() == null)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DisbursementPlanResponse> getAllPlans(Pageable pageable) {
        return planRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toPlanResponse);
    }

    @Transactional(readOnly = true)
    public List<DisbursementPlanResponse> getAllPlansList() {
        return planRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toPlanResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DisbursementPlanResponse> getBeneficiaryPlans(String email) {
        User user = getUser(email);
        if (user.getRole() != Role.BENEFICIARY) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Beneficiary access required.");
        return planRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> p != null
                        && p.getApplication() != null
                        && p.getApplication().getBeneficiary() != null
                        && p.getApplication().getBeneficiary().getUser() != null
                        && Objects.equals(p.getApplication().getBeneficiary().getUser().getId(), user.getId()))
                .map(this::toPlanResponse).collect(Collectors.toList());
    }

    public DisbursementPlanResponse deactivatePlan(Long id, String email) {
        User admin = getUser(email);
        if (admin.getRole() != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator access required.");
        DisbursementPlan plan = planRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement plan not found."));
        if (plan.getInstallments().stream().anyMatch(i -> i.getStatus() == InstallmentStatus.DISBURSED))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Plans with released installments cannot be deactivated.");
        plan.setPlanStatus(DisbursementPlanStatus.CANCELLED);
        plan.addAuditEntry(new DisbursementPlanAudit(plan, admin, "DEACTIVATED", "Plan deactivated before any installment release."));
        return toPlanResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public DisbursementPlanResponse getPlanById(Long id) {
        DisbursementPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disbursement plan not found."));
        return toPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public DisbursementPlanResponse getPlanByApplicationId(Long applicationId) {
        DisbursementPlan plan = planRepository.findByApplication_Id(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Disbursement plan not found for application #" + applicationId));
        return toPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<GrantApplicationResponse> getEligibleApplicationsWithoutPlan() {
        List<GrantApplication> approvedApps = applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> a.getStatus() == GrantApplicationStatus.APPROVED)
                .filter(a -> !planRepository.existsByApplication_Id(a.getId()))
                .collect(Collectors.toList());

        return approvedApps.stream().map(this::toAppResponse).collect(Collectors.toList());
    }

    // ==========================================
    // FEATURE 2: MILESTONE SCHEDULING & PROGRESS
    // ==========================================

    @Transactional(readOnly = true)
    public List<DisbursementMilestoneResponse> getAllMilestones() {
        return milestoneRepository.findAllDetailed().stream()
                .map(this::toMilestoneResponse)
                .collect(Collectors.toList());
    }

    public DisbursementMilestoneResponse updateMilestoneProgress(
            Long milestoneId, MilestoneProgressUpdateRequest request, String userEmail) {
        DisbursementMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found."));

        User user = getUser(userEmail);
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.OFFICER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers or admins can update milestone progress.");
        }
        if (user.getRole() == Role.OFFICER) {
            DisbursementInstallment inst = milestone.getInstallment();
            DisbursementPlan plan = inst != null ? inst.getPlan() : null;
            GrantApplication app = plan != null ? plan.getApplication() : null;
            User assignedOfficer = app != null ? app.getAssignedOfficer() : null;
            if (assignedOfficer == null || !Objects.equals(assignedOfficer.getId(), user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This application is outside your assigned workflow.");
            }
        }

        if (request.progressPercentage() != null) {
            milestone.setProgressPercentage(request.progressPercentage());
            if (request.progressPercentage() >= 100) {
                milestone.setStatus(MilestoneStatus.COMPLETED);
                milestone.setCompletedAt(LocalDateTime.now());
                milestone.setNonCompliant(false);
                milestone.setNonComplianceReason(null);
            } else if (request.progressPercentage() > 0 && milestone.getStatus() == MilestoneStatus.PLANNED) {
                milestone.setStatus(MilestoneStatus.PENDING);
            }
        }

        if (request.status() != null) {
            milestone.setStatus(request.status());
            if (request.status() == MilestoneStatus.COMPLETED) {
                milestone.setProgressPercentage(100);
                milestone.setCompletedAt(LocalDateTime.now());
                milestone.setNonCompliant(false);
                milestone.setNonComplianceReason(null);
            }
        }

        DisbursementMilestone saved = milestoneRepository.save(milestone);
        return toMilestoneResponse(saved);
    }

    // ==========================================
    // FEATURE 3 & 5: COMPLIANCE TRACKING & FLAGGING
    // ==========================================

    @Transactional(readOnly = true)
    public List<MilestoneComplianceResponse> getCompliancesByMilestone(Long milestoneId) {
        return complianceRepository.findByMilestone_Id(milestoneId).stream()
                .map(this::toComplianceResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MilestoneComplianceResponse> getAllCompliances() {
        return complianceRepository.findAll().stream()
                .map(this::toComplianceResponse)
                .collect(Collectors.toList());
    }

    public MilestoneComplianceResponse submitCompliance(
            Long milestoneId,
            ComplianceSubmitRequest request,
            MultipartFile file,
            String userEmail) {
        DisbursementMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Milestone not found."));

        User user = getUser(userEmail);
        GrantApplication app = milestone.getInstallment() != null && milestone.getInstallment().getPlan() != null
                ? milestone.getInstallment().getPlan().getApplication() : null;
        User appBeneficiaryUser = app != null && app.getBeneficiary() != null
                ? app.getBeneficiary().getUser() : null;

        if (user.getRole() == Role.BENEFICIARY) {
            if (appBeneficiaryUser == null || !Objects.equals(appBeneficiaryUser.getId(), user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can submit evidence only for your own grant applications.");
            }
        } else if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned beneficiary or an administrator can submit compliance evidence.");
        }

        String reqTitle = request.requirementTitle() != null && !request.requirementTitle().isBlank()
                ? request.requirementTitle().trim()
                : "Proof of Compliance";

        // Find existing compliance requirement for this milestone or create a new one
        MilestoneCompliance compliance = milestone.getCompliances().stream()
                .filter(c -> c.getRequirementTitle().equalsIgnoreCase(reqTitle)
                        || milestone.getCompliances().size() == 1)
                .findFirst()
                .orElse(null);

        if (compliance == null) {
            compliance = new MilestoneCompliance();
            compliance.setMilestone(milestone);
            compliance.setRequirementTitle(reqTitle);
            milestone.addCompliance(compliance);
        } else {
            if (compliance.getStatus() == ComplianceStatus.VERIFIED
                    || compliance.getStatus() == ComplianceStatus.APPROVED
                    || compliance.getStatus() == ComplianceStatus.WAIVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This compliance requirement is already approved/closed and cannot accept new submissions.");
            }
        }

        compliance.setDescription(request.description() != null ? request.description() : compliance.getDescription());
        compliance.setSubmissionNotes(request.submissionNotes());
        compliance.setStatus(ComplianceStatus.SUBMITTED);
        compliance.setSubmittedAt(LocalDateTime.now());
        compliance.setSubmittedBy(user);
        compliance.setVerifiedBy(null);
        compliance.setVerifiedAt(null);
        compliance.setVerificationRemarks(null);

        if (file != null && !file.isEmpty()) {
            validateComplianceDocument(file);
            compliance.setDocumentFileName(safeFileName(file.getOriginalFilename()));
            compliance.setDocumentContentType(file.getContentType());
            compliance.setDocumentFileSize(file.getSize());
            try {
                compliance.setDocumentData(file.getBytes());
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read uploaded document.");
            }
        }

        ComplianceReviewHistory history = new ComplianceReviewHistory();
        history.setActor(user); history.setAction("SUBMITTED"); history.setRemarks(request.submissionNotes());
        history.setDocumentFileName(compliance.getDocumentFileName()); compliance.addHistory(history);

        // If milestone was marked non-compliant due to missing submission or rejection, re-evaluate on resubmission
        if (milestone.getNonCompliant() && milestone.getNonComplianceReason() != null) {
            milestone.setNonCompliant(false);
            milestone.setNonComplianceReason(null);
            milestoneRepository.save(milestone);
        }

        MilestoneCompliance saved = complianceRepository.save(compliance);
        return toComplianceResponse(saved);
    }

    @Transactional(readOnly = true)
    public byte[] getComplianceDocumentBytes(Long complianceId) {
        MilestoneCompliance compliance = getComplianceEntity(complianceId);
        if (compliance.getDocumentData() == null || compliance.getDocumentData().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No document attached to this compliance record.");
        }
        return compliance.getDocumentData();
    }

    @Transactional(readOnly = true)
    public MilestoneCompliance getComplianceEntity(Long complianceId, String userEmail) {
        MilestoneCompliance compliance = complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));

        User actor = getUser(userEmail);
        DisbursementMilestone milestone = compliance.getMilestone();
        DisbursementInstallment inst = milestone != null ? milestone.getInstallment() : null;
        DisbursementPlan plan = inst != null ? inst.getPlan() : null;
        GrantApplication app = plan != null ? plan.getApplication() : null;

        User benUser = app != null && app.getBeneficiary() != null ? app.getBeneficiary().getUser() : null;
        User assignedOfficer = app != null ? app.getAssignedOfficer() : null;

        boolean allowed = actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.BENEFICIARY && benUser != null && Objects.equals(benUser.getId(), actor.getId()))
                || (actor.getRole() == Role.OFFICER && assignedOfficer != null && Objects.equals(assignedOfficer.getId(), actor.getId()));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this compliance document.");
        }
        return compliance;
    }

    @Transactional(readOnly = true)
    public MilestoneCompliance getComplianceEntity(Long complianceId) {
        return complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));
    }

    public MilestoneComplianceResponse verifyCompliance(
            Long complianceId, ComplianceVerifyRequest request, String officerEmail) {
        User officer = getUser(officerEmail);
        if (officer.getRole() != Role.ADMIN && officer.getRole() != Role.OFFICER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only officers or admins can verify compliance.");
        }

        MilestoneCompliance compliance = complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));

        DisbursementMilestone milestone = compliance.getMilestone();
        if (officer.getRole() == Role.OFFICER) {
            DisbursementInstallment inst = milestone != null ? milestone.getInstallment() : null;
            DisbursementPlan plan = inst != null ? inst.getPlan() : null;
            GrantApplication app = plan != null ? plan.getApplication() : null;
            User assignedOfficer = app != null ? app.getAssignedOfficer() : null;
            if (assignedOfficer == null || !Objects.equals(assignedOfficer.getId(), officer.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This application is outside your assigned workflow.");
            }
        }

        compliance.setVerifiedBy(officer);
        compliance.setVerifiedAt(LocalDateTime.now());
        compliance.setVerificationRemarks(request.remarks());

        if (Boolean.TRUE.equals(request.approved())) {
            if (compliance.getDocumentData() == null || compliance.getDocumentData().length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve compliance evidence without an uploaded supporting document.");
            }
            compliance.setStatus(ComplianceStatus.VERIFIED);

            // Check if all compliances of milestone are now VERIFIED
            boolean allVerified = milestone.getCompliances().stream()
                    .allMatch(c -> !c.isRequired() || c.getId().equals(compliance.getId())
                            || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.APPROVED
                            || c.getStatus() == ComplianceStatus.WAIVED);

            if (allVerified) {
                milestone.setNonCompliant(false);
                milestone.setNonComplianceReason(null);
                milestone.setStatus(MilestoneStatus.COMPLETED);
                milestone.setProgressPercentage(100);
                milestone.setCompletedAt(LocalDateTime.now());
            } else {
                long totalReq = milestone.getCompliances().stream().filter(MilestoneCompliance::isRequired).count();
                long doneReq = milestone.getCompliances().stream().filter(c ->
                        c.isRequired() && (c.getId().equals(compliance.getId()) || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.APPROVED || c.getStatus() == ComplianceStatus.WAIVED)
                ).count();
                int progress = totalReq > 0 ? (int) ((doneReq * 100) / totalReq) : 50;
                milestone.setProgressPercentage(progress);
                if (milestone.getStatus() == MilestoneStatus.PLANNED) {
                    milestone.setStatus(MilestoneStatus.PENDING);
                }
            }
        } else {
            // Flag as rejected and mark milestone non-compliant
            compliance.setStatus(ComplianceStatus.REJECTED);
            milestone.setNonCompliant(true);
            milestone.setNonComplianceReason("Compliance rejected: " + (request.remarks() != null ? request.remarks() : "Evidence does not satisfy criteria"));
            if (milestone.getStatus() == MilestoneStatus.COMPLETED) {
                milestone.setStatus(MilestoneStatus.PENDING);
                milestone.setProgressPercentage(50);
            }

            // Alert admin and officer
            for (User admin : userRepository.findAll()) {
                if (admin.getRole() == Role.ADMIN) {
                    workflowAutomationService.notify(
                            admin,
                            "compliance_rejected:" + compliance.getId(),
                            "Non-Compliance Alert: Compliance rejected for Milestone #" + milestone.getId() + " (" + milestone.getTitle() + ")",
                            milestone.getInstallment().getPlan().getApplication().getId()
                    );
                }
            }
        }

        ComplianceReviewHistory history = new ComplianceReviewHistory();
        history.setActor(officer); history.setAction(Boolean.TRUE.equals(request.approved()) ? "APPROVED" : "REJECTED");
        history.setRemarks(request.remarks()); compliance.addHistory(history);

        milestoneRepository.save(milestone);
        MilestoneCompliance saved = complianceRepository.save(compliance);
        return toComplianceResponse(saved);
    }

    // ==========================================
    // SECURITY & FINANCIAL SAFETY: DISBURSEMENT RELEASE
    // ==========================================

    public DisbursementInstallmentResponse disburseInstallment(
            Long installmentId, DisburseFundsRequest request, String adminEmail) {
        User admin = getUser(adminEmail);
        if (admin.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only authorized administrators can disburse funds.");
        }

        DisbursementInstallment installment = installmentRepository.findByIdForUpdate(installmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installment not found."));

        DisbursementPlan plan = installment.getPlan();
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Installment is not associated with a disbursement plan.");
        }

        if (plan.getPlanStatus() != DisbursementPlanStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot disburse installment: disbursement plan is " + plan.getPlanStatus());
        }

        if (plan.getApplication() != null && plan.getApplication().getStatus() != GrantApplicationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application has not received final Finance Officer approval.");
        }

        if (installment.getStatus() == InstallmentStatus.DISBURSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Installment has already been disbursed. Duplicate payment release is prohibited.");
        }

        // Installment sequence enforcement: prior installments must already be DISBURSED
        if (installment.getInstallmentNumber() != null && installment.getInstallmentNumber() > 1) {
            boolean priorUndisbursed = plan.getInstallments().stream()
                    .filter(inst -> inst.getInstallmentNumber() < installment.getInstallmentNumber())
                    .anyMatch(inst -> inst.getStatus() != InstallmentStatus.DISBURSED);
            if (priorUndisbursed) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Previous installments must be disbursed before releasing installment #" + installment.getInstallmentNumber());
            }
        }

        // Over-disbursement cap check
        BigDecimal newTotalDisbursed = plan.getTotalDisbursedAmount().add(installment.getAmount());
        if (newTotalDisbursed.compareTo(plan.getTotalApprovedAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Disbursement amount exceeds total approved grant amount of ₹%s.",
                            plan.getTotalApprovedAmount().toPlainString()));
        }

        // FINANCIAL SAFETY: Ensure all active milestone compliances are VERIFIED and milestones are COMPLETED with 100% progress
        for (DisbursementMilestone m : installment.getMilestones()) {
            List<MilestoneCompliance> activeCompliances = m.getCompliances() == null
                    ? Collections.emptyList()
                    : m.getCompliances().stream()
                        .filter(c -> c != null && (c.getRequirement() == null || c.getRequirement().isActive()))
                        .toList();
            boolean allCompliancesSatisfied = !activeCompliances.isEmpty() && activeCompliances.stream().allMatch(c ->
                    !c.isRequired() || c.getStatus() == ComplianceStatus.APPROVED || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.WAIVED
            );
            if (allCompliancesSatisfied && !Boolean.TRUE.equals(m.getNonCompliant())) {
                if (m.getStatus() != MilestoneStatus.COMPLETED || m.getProgressPercentage() == null || m.getProgressPercentage() < 100) {
                    m.setStatus(MilestoneStatus.COMPLETED);
                    m.setProgressPercentage(100);
                    if (m.getCompletedAt() == null) {
                        m.setCompletedAt(LocalDateTime.now());
                    }
                    milestoneRepository.save(m);
                }
            }

            if (m.getStatus() != MilestoneStatus.COMPLETED || (m.getProgressPercentage() != null && m.getProgressPercentage() < 100)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Cannot release disbursement: Milestone '%s' is not completed (current status: %s, progress: %d%%). All milestones must be COMPLETED with 100%% progress before release.",
                                m.getTitle(), m.getStatus(), m.getProgressPercentage() != null ? m.getProgressPercentage() : 0));
            }
            if (m.getNonCompliant()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Cannot release disbursement: Milestone '%s' is flagged as non-compliant (%s).",
                                m.getTitle(), m.getNonComplianceReason()));
            }
            for (MilestoneCompliance c : activeCompliances) {
                if (c.isRequired() && c.getStatus() != ComplianceStatus.VERIFIED
                        && c.getStatus() != ComplianceStatus.APPROVED && c.getStatus() != ComplianceStatus.WAIVED) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            String.format("Cannot release disbursement: Compliance '%s' is in status '%s'. All compliances must be VERIFIED before fund release.",
                                    c.getRequirementTitle(), c.getStatus()));
                }
            }
        }

        if (request.paymentReference() == null || request.paymentReference().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid payment transaction reference / UTR is strictly required to record disbursement.");
        }
        String paymentRef = request.paymentReference().trim();
        if (installmentRepository.existsByPaymentReference(paymentRef)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    String.format("Payment transaction reference '%s' has already been recorded for another disbursement.", paymentRef));
        }

        installment.setStatus(InstallmentStatus.DISBURSED);
        installment.setActualDisbursementDate(LocalDateTime.now());
        installment.setPaymentReference(paymentRef);
        installment.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod().trim() : "DIRECT_BENEFIT_TRANSFER");
        installment.setDisbursedBy(admin);
        installment.setDisbursementRemarks(request.remarks());

        // Update plan disbursed totals
        plan.setTotalDisbursedAmount(newTotalDisbursed);
        plan.addAuditEntry(new DisbursementPlanAudit(
                plan,
                admin,
                "DISBURSED",
                String.format("Disbursed installment #%d (₹%s) with payment reference: %s",
                        installment.getInstallmentNumber(),
                        installment.getAmount().toPlainString(),
                        installment.getPaymentReference())
        ));

        // Check if all installments are disbursed
        boolean allDisbursed = plan.getInstallments().stream()
                .allMatch(inst -> inst.getId().equals(installment.getId()) || inst.getStatus() == InstallmentStatus.DISBURSED);

        if (allDisbursed) {
            plan.setPlanStatus(DisbursementPlanStatus.COMPLETED);
        } else {
            // Activate the next scheduled installment if in PLANNED status
            plan.getInstallments().stream()
                    .filter(inst -> inst.getInstallmentNumber() > installment.getInstallmentNumber())
                    .min(Comparator.comparing(DisbursementInstallment::getInstallmentNumber))
                    .ifPresent(nextInst -> {
                        if (nextInst.getStatus() == InstallmentStatus.PLANNED) {
                            nextInst.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
                            installmentRepository.save(nextInst);
                        }
                    });
        }
        planRepository.save(plan);
        DisbursementInstallment saved = installmentRepository.save(installment);

        // Notify beneficiary
        GrantApplication app = plan.getApplication();
        if (app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
            workflowAutomationService.notify(
                    app.getBeneficiary().getUser(),
                    "disbursement_released:" + saved.getId(),
                    String.format("Grant disbursement of ₹%s (Stage %d) has been authorized. Transaction Ref: %s",
                            saved.getAmount().toPlainString(), saved.getInstallmentNumber(), saved.getPaymentReference()),
                    app.getId()
            );
        }

        return toInstallmentResponse(saved);
    }

    // ==========================================
    // FEATURE 4: REMINDER AUTOMATION & SCHEDULER
    // ==========================================

    public DisbursementReminderConfig getReminderConfig() {
        return reminderConfigRepository.findById(1L)
                .orElseGet(() -> {
                    DisbursementReminderConfig config = new DisbursementReminderConfig();
                    return reminderConfigRepository.save(config);
                });
    }

    public DisbursementReminderConfig updateReminderConfig(ReminderConfigDto dto, String adminEmail) {
        User admin = getUser(adminEmail);
        if (admin.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }

        DisbursementReminderConfig config = getReminderConfig();
        if (dto.upcomingDaysBefore() != null && dto.upcomingDaysBefore() > 0) {
            config.setUpcomingDaysBefore(dto.upcomingDaysBefore());
        }
        if (dto.autoFlagOverdue() != null) {
            config.setAutoFlagOverdue(dto.autoFlagOverdue());
        }
        if (dto.enabled() != null) {
            config.setEnabled(dto.enabled());
        }
        config.setUpdatedAt(LocalDateTime.now());
        return reminderConfigRepository.save(config);
    }

    // ==========================================
    // FEATURE 6: FUND UTILIZATION DASHBOARD
    // ==========================================

    @Transactional(readOnly = true)
    public FundUtilizationSummaryResponse getFundUtilizationSummary() {
        List<Scheme> allSchemes = schemeRepository.findAll();
        List<GrantApplication> approvedApps = applicationRepository.findAllByOrderByIdDesc().stream()
                .filter(a -> a.getStatus() == GrantApplicationStatus.APPROVED)
                .collect(Collectors.toList());

        List<DisbursementPlan> allPlans = planRepository.findAll();
        List<DisbursementInstallment> allInstallments = installmentRepository.findAll();

        BigDecimal totalApproved = approvedApps.stream()
                .map(GrantApplication::getRequestedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalScheduled = allInstallments.stream()
                .filter(inst -> (inst.getPlan() == null || inst.getPlan().getPlanStatus() != DisbursementPlanStatus.CANCELLED)
                        && inst.getStatus() != InstallmentStatus.CANCELLED)
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDisbursed = allInstallments.stream()
                .filter(inst -> inst.getStatus() == InstallmentStatus.DISBURSED
                        && (inst.getPlan() == null || inst.getPlan().getPlanStatus() != DisbursementPlanStatus.CANCELLED))
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllocated = BigDecimal.ZERO;
        List<SchemeFundUtilizationResponse> schemeBreakdowns = new ArrayList<>();

        for (Scheme s : allSchemes) {
            BigDecimal schemeAllocated = s.getAllocatedFunds() != null ? s.getAllocatedFunds() : BigDecimal.ZERO;
            totalAllocated = totalAllocated.add(schemeAllocated);

            List<GrantApplication> sApps = approvedApps.stream()
                    .filter(a -> a.getScheme() != null && Objects.equals(a.getScheme().getId(), s.getId()))
                    .collect(Collectors.toList());

            BigDecimal sApproved = sApps.stream()
                    .map(GrantApplication::getRequestedAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<DisbursementPlan> sPlans = allPlans.stream()
                    .filter(p -> p.getScheme() != null && Objects.equals(p.getScheme().getId(), s.getId()) && p.getPlanStatus() != DisbursementPlanStatus.CANCELLED)
                    .collect(Collectors.toList());

            BigDecimal sScheduled = sPlans.stream()
                    .flatMap(p -> p.getInstallments().stream())
                    .filter(i -> i.getStatus() != InstallmentStatus.CANCELLED)
                    .map(DisbursementInstallment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sDisbursed = sPlans.stream()
                    .flatMap(p -> p.getInstallments().stream())
                    .filter(i -> i.getStatus() == InstallmentStatus.DISBURSED)
                    .map(DisbursementInstallment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sRemaining = schemeAllocated.subtract(sApproved).max(BigDecimal.ZERO);
            BigDecimal sUtilizationPct = sApproved.compareTo(BigDecimal.ZERO) > 0
                    ? sDisbursed.multiply(BigDecimal.valueOf(100)).divide(sApproved, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            schemeBreakdowns.add(new SchemeFundUtilizationResponse(
                    s.getId(),
                    s.getSchemeName(),
                    s.getDepartment(),
                    schemeAllocated,
                    sApproved,
                    sScheduled,
                    sDisbursed,
                    sRemaining,
                    sUtilizationPct,
                    sPlans.size()
            ));
        }

        BigDecimal remainingAllocated = totalAllocated.subtract(totalApproved).max(BigDecimal.ZERO);
        BigDecimal remainingApproved = totalApproved.subtract(totalDisbursed).max(BigDecimal.ZERO);

        BigDecimal overallUtilization = totalApproved.compareTo(BigDecimal.ZERO) > 0
                ? totalDisbursed.multiply(BigDecimal.valueOf(100)).divide(totalApproved, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Installment status breakdown
        Map<InstallmentStatus, List<DisbursementInstallment>> byStatus = allInstallments.stream()
                .collect(Collectors.groupingBy(DisbursementInstallment::getStatus));

        List<InstallmentStatusBreakdownResponse> statusBreakdowns = new ArrayList<>();
        for (InstallmentStatus st : InstallmentStatus.values()) {
            List<DisbursementInstallment> list = byStatus.getOrDefault(st, Collections.emptyList());
            long count = list.size();
            BigDecimal amount = list.stream()
                    .map(DisbursementInstallment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            statusBreakdowns.add(new InstallmentStatusBreakdownResponse(st.name(), count, amount));
        }

        return new FundUtilizationSummaryResponse(
                totalAllocated,
                totalApproved,
                totalScheduled,
                totalDisbursed,
                remainingAllocated,
                remainingApproved,
                overallUtilization,
                schemeBreakdowns,
                statusBreakdowns
        );
    }

    // ==========================================
    // FEATURE 7: REGIONAL ANALYTICS
    // ==========================================

    @Transactional(readOnly = true)
    public List<RegionalAnalyticsResponse> getRegionalAnalytics() {
        List<GrantApplication> allApplications = applicationRepository.findAllByOrderByIdDesc();
        List<DisbursementPlan> allPlans = planRepository.findAll();
        List<DisbursementMilestone> allMilestones = milestoneRepository.findAll();

        Map<String, Map<String, List<GrantApplication>>> regionTree = new HashMap<>();

        for (GrantApplication app : allApplications) {
            String state = "Unknown State";
            String district = "Unknown District";
            if (app.getBeneficiary() != null) {
                if (app.getBeneficiary().getState() != null && !app.getBeneficiary().getState().isBlank()) {
                    state = app.getBeneficiary().getState().trim();
                }
                if (app.getBeneficiary().getDistrict() != null && !app.getBeneficiary().getDistrict().isBlank()) {
                    district = app.getBeneficiary().getDistrict().trim();
                }
            }

            regionTree.computeIfAbsent(state, k -> new HashMap<>())
                    .computeIfAbsent(district, k -> new ArrayList<>())
                    .add(app);
        }

        List<RegionalAnalyticsResponse> results = new ArrayList<>();

        for (Map.Entry<String, Map<String, List<GrantApplication>>> stateEntry : regionTree.entrySet()) {
            String state = stateEntry.getKey();
            for (Map.Entry<String, List<GrantApplication>> distEntry : stateEntry.getValue().entrySet()) {
                String district = distEntry.getKey();
                List<GrantApplication> apps = distEntry.getValue();

                long totalApps = apps.size();
                List<GrantApplication> approved = apps.stream()
                        .filter(a -> a.getStatus() == GrantApplicationStatus.APPROVED)
                        .collect(Collectors.toList());
                long approvedCount = approved.size();

                long rejectedCount = apps.stream()
                        .filter(a -> a.getStatus() == GrantApplicationStatus.REJECTED)
                        .count();

                BigDecimal approvedAmount = approved.stream()
                        .map(GrantApplication::getRequestedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Set<Long> appIds = apps.stream().map(GrantApplication::getId).collect(Collectors.toSet());

                BigDecimal disbursedAmount = allPlans.stream()
                        .filter(p -> p.getPlanStatus() != DisbursementPlanStatus.CANCELLED && appIds.contains(p.getApplication().getId()))
                        .map(DisbursementPlan::getTotalDisbursedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal pendingDisbursed = approvedAmount.subtract(disbursedAmount);
                if (pendingDisbursed.compareTo(BigDecimal.ZERO) < 0) {
                    pendingDisbursed = BigDecimal.ZERO;
                }

                List<DisbursementMilestone> regMilestones = allMilestones.stream()
                        .filter(m -> m.getInstallment() != null && m.getInstallment().getPlan() != null
                                && appIds.contains(m.getInstallment().getPlan().getApplication().getId()))
                        .collect(Collectors.toList());

                long totalMilestones = regMilestones.size();
                long verifiedMilestones = regMilestones.stream()
                        .filter(m -> m.getStatus() == MilestoneStatus.COMPLETED && !m.getNonCompliant())
                        .count();

                BigDecimal complianceRate = totalMilestones > 0
                        ? BigDecimal.valueOf(verifiedMilestones).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalMilestones), 2, RoundingMode.HALF_UP)
                        : BigDecimal.valueOf(100);

                BigDecimal fundUtilRate = approvedAmount.compareTo(BigDecimal.ZERO) > 0
                        ? disbursedAmount.multiply(BigDecimal.valueOf(100)).divide(approvedAmount, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                results.add(new RegionalAnalyticsResponse(
                        state,
                        district,
                        totalApps,
                        approvedCount,
                        rejectedCount,
                        approvedAmount,
                        disbursedAmount,
                        pendingDisbursed,
                        verifiedMilestones,
                        totalMilestones,
                        complianceRate,
                        fundUtilRate
                ));
            }
        }

        results.sort(Comparator.comparing(RegionalAnalyticsResponse::state)
                .thenComparing(RegionalAnalyticsResponse::district));

        return results;
    }

    // ==========================================
    // MAPPERS & UTILITIES
    // ==========================================

    private DisbursementPlanResponse toPlanResponse(DisbursementPlan plan) {
        GrantApplication app = plan.getApplication();
        Beneficiary ben = app != null ? app.getBeneficiary() : null;
        Scheme scheme = plan.getScheme();

        List<DisbursementInstallment> installments = plan.getInstallments() == null ? Collections.emptyList() : plan.getInstallments();

        BigDecimal scheduledTotal = installments.stream()
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal approved = plan.getTotalApprovedAmount() != null ? plan.getTotalApprovedAmount() : BigDecimal.ZERO;
        BigDecimal disbursed = plan.getTotalDisbursedAmount() != null ? plan.getTotalDisbursedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = approved.subtract(disbursed);

        List<DisbursementInstallmentResponse> instResponses = installments.stream()
                .map(this::toInstallmentResponse)
                .collect(Collectors.toList());

        return new DisbursementPlanResponse(
                plan.getId(),
                app != null ? app.getId() : null,
                scheme != null ? scheme.getId() : null,
                scheme != null ? scheme.getSchemeName() : "Unknown Scheme",
                scheme != null ? scheme.getDepartment() : "General",
                ben != null ? ben.getId() : null,
                ben != null && ben.getUser() != null ? ben.getUser().getFullName() : "Beneficiary",
                ben != null ? ben.getState() : null,
                ben != null ? ben.getDistrict() : null,
                approved,
                disbursed,
                scheduledTotal,
                remaining,
                plan.getPlanStatus(),
                plan.getNotes(),
                plan.getCreatedBy() != null ? plan.getCreatedBy().getFullName() : "Admin",
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                instResponses
        );
    }

    private DisbursementInstallmentResponse toInstallmentResponse(DisbursementInstallment inst) {
        List<DisbursementMilestone> milestones = inst.getMilestones() == null ? Collections.emptyList() : inst.getMilestones();

        List<DisbursementMilestoneResponse> milestoneResponses = milestones.stream()
                .map(this::toMilestoneResponse)
                .collect(Collectors.toList());

        boolean allVerified = !milestones.isEmpty() && milestones.stream().allMatch(m -> {
            List<MilestoneCompliance> activeCompliances = m.getCompliances() == null
                    ? Collections.emptyList()
                    : m.getCompliances().stream()
                        .filter(c -> c != null && (c.getRequirement() == null || c.getRequirement().isActive()))
                        .toList();
            return !Boolean.TRUE.equals(m.getNonCompliant()) && activeCompliances.stream().allMatch(c -> !c.isRequired()
                    || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.APPROVED
                    || c.getStatus() == ComplianceStatus.WAIVED);
        });

        return new DisbursementInstallmentResponse(
                inst.getId(),
                inst.getPlan() != null ? inst.getPlan().getId() : null,
                inst.getInstallmentNumber(),
                inst.getTitle(),
                inst.getPercentage(),
                inst.getAmount(),
                inst.getPlannedReleaseDate(),
                inst.getActualDisbursementDate(),
                inst.getStatus(),
                inst.getPaymentReference(),
                inst.getPaymentMethod(),
                inst.getDisbursedBy() != null ? inst.getDisbursedBy().getFullName() : null,
                inst.getDisbursementRemarks(),
                milestoneResponses,
                allVerified
        );
    }

    private DisbursementMilestoneResponse toMilestoneResponse(DisbursementMilestone m) {
        DisbursementInstallment inst = m.getInstallment();
        DisbursementPlan plan = inst != null ? inst.getPlan() : null;
        GrantApplication app = plan != null ? plan.getApplication() : null;
        Beneficiary ben = app != null ? app.getBeneficiary() : null;

        List<MilestoneCompliance> allCompliances = m.getCompliances() == null ? Collections.emptyList() : m.getCompliances();
        List<MilestoneCompliance> activeCompliances = allCompliances.stream()
                .filter(c -> c != null && (c.getRequirement() == null || c.getRequirement().isActive()))
                .toList();

        List<MilestoneComplianceResponse> compResponses = activeCompliances.stream()
                .map(this::toComplianceResponse)
                .collect(Collectors.toList());

        boolean allSatisfied = !activeCompliances.isEmpty() && activeCompliances.stream().allMatch(c ->
                !c.isRequired() || c.getStatus() == ComplianceStatus.APPROVED
                        || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.WAIVED
        );
        MilestoneStatus status = m.getStatus();
        Integer progress = m.getProgressPercentage();
        if (allSatisfied && !Boolean.TRUE.equals(m.getNonCompliant())) {
            status = MilestoneStatus.COMPLETED;
            progress = 100;
        }

        return new DisbursementMilestoneResponse(
                m.getId(),
                inst != null ? inst.getId() : null,
                inst != null ? inst.getInstallmentNumber() : null,
                inst != null ? inst.getTitle() : null,
                plan != null ? plan.getId() : null,
                app != null ? app.getId() : null,
                plan != null && plan.getScheme() != null ? plan.getScheme().getSchemeName() : null,
                ben != null && ben.getUser() != null ? ben.getUser().getFullName() : null,
                m.getTitle(),
                m.getDescription(),
                m.getDueDate(),
                m.getAmount(),
                status,
                progress,
                m.getNonCompliant(),
                m.getNonComplianceReason(),
                m.getCompletedAt(),
                compResponses
        );
    }

    private MilestoneComplianceResponse toComplianceResponse(MilestoneCompliance c) {
        DisbursementMilestone m = c.getMilestone();
        return new MilestoneComplianceResponse(
                c.getId(),
                m != null ? m.getId() : null,
                m != null ? m.getTitle() : null,
                c.getRequirementTitle(),
                c.getDescription(),
                c.getSubmissionNotes(),
                c.getStatus(),
                c.getDocumentFileName(),
                c.getDocumentContentType(),
                c.getDocumentFileSize(),
                c.getDocumentData() != null && c.getDocumentData().length > 0,
                c.getSubmittedAt(),
                c.getSubmittedBy() != null ? c.getSubmittedBy().getFullName() : null,
                c.getVerifiedBy() != null ? c.getVerifiedBy().getFullName() : null,
                c.getVerifiedAt(),
                c.getVerificationRemarks(),
                c.isRequired()
        );
    }

    private GrantApplicationResponse toAppResponse(GrantApplication a) {
        Beneficiary b = a.getBeneficiary();
        BeneficiaryVerificationDetails benDetails = null;
        if (b != null) {
            benDetails = new BeneficiaryVerificationDetails(
                    b.getUser() != null ? b.getUser().getEmail() : null,
                    b.getUser() != null ? b.getUser().getPhone() : null,
                    b.getDateOfBirth(),
                    b.getAnnualIncome(),
                    b.getAddress(),
                    b.getState(),
                    b.getDistrict(),
                    b.getCategory()
            );
        }

        return new GrantApplicationResponse(
                a.getId(),
                b != null ? b.getId() : null,
                b != null && b.getUser() != null ? b.getUser().getFullName() : null,
                a.getScheme() != null ? a.getScheme().getId() : null,
                a.getScheme() != null ? a.getScheme().getSchemeName() : null,
                a.getApplicationDetails(),
                a.getRequestedAmount(),
                a.getStatus(),
                a.getSubmittedAt(),
                a.getEligibilityEvaluation(),
                a.getWorkflowStage() != null ? a.getWorkflowStage().name() : null,
                a.getAssignedOfficer() != null ? a.getAssignedOfficer().getId() : null,
                a.getAssignedOfficer() != null ? a.getAssignedOfficer().getFullName() : null,
                a.getRoutingPriority() != null ? a.getRoutingPriority().name() : "NORMAL",
                a.isEscalated(),
                List.of(),
                List.of(),
                false,
                List.of(),
                benDetails,
                a.getEligibilityEvaluation() != null ? a.getEligibilityEvaluation().eligibilityThreshold() : null
        );
    }

    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private void validateComplianceDocument(MultipartFile file) {
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document size cannot exceed 10MB.");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String extension = fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        try {
            byte[] header = file.getBytes();
            boolean pdf = extension.equals("pdf") && contentType.equals("application/pdf")
                    && new String(header, 0, Math.min(5, header.length), StandardCharsets.US_ASCII).startsWith("%PDF-");
            boolean png = extension.equals("png") && contentType.equals("image/png")
                    && header.length >= 8 && (header[0] & 0xff) == 0x89
                    && header[1] == 0x50 && header[2] == 0x4e && header[3] == 0x47;
            boolean jpeg = (extension.equals("jpg") || extension.equals("jpeg"))
                    && contentType.equals("image/jpeg") && header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8;
            if (pdf || png || jpeg) {
                return;
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to inspect uploaded document.", e);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only valid PDF, PNG, and JPEG documents are allowed.");
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "compliance_document";
        }
        String fileName = originalName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        return fileName.isBlank() ? "compliance_document" : fileName;
    }
}
