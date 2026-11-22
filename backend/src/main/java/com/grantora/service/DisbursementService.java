package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
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
        this.planRepository = planRepository;
        this.installmentRepository = installmentRepository;
        this.milestoneRepository = milestoneRepository;
        this.complianceRepository = complianceRepository;
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

                MilestoneCompliance compliance = new MilestoneCompliance();
                compliance.setMilestone(milestone);
                compliance.setRequirementTitle("Stage " + installment.getInstallmentNumber() + " Utilization Certificate & Invoices");
                compliance.setDescription("Submit expenditure verification documents and progress report");
                compliance.setStatus(ComplianceStatus.PENDING);
                milestone.addCompliance(compliance);

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
            MilestoneCompliance defaultCompliance = new MilestoneCompliance();
            defaultCompliance.setMilestone(milestone);
            defaultCompliance.setRequirementTitle("Milestone Completion Certificate");
            defaultCompliance.setStatus(ComplianceStatus.PENDING);
            milestone.addCompliance(defaultCompliance);
        }

        return milestone;
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
                .filter(p -> p.getApplication().getBeneficiary().getUser().getId().equals(user.getId()))
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
        if(user.getRole()==Role.OFFICER && (milestone.getInstallment().getPlan().getApplication().getAssignedOfficer()==null
                || !Objects.equals(milestone.getInstallment().getPlan().getApplication().getAssignedOfficer().getId(),user.getId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This application is outside your assigned workflow.");

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

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setMilestone(milestone);
        compliance.setRequirementTitle(request.requirementTitle() != null && !request.requirementTitle().isBlank()
                ? request.requirementTitle().trim()
                : "Proof of Compliance");
        compliance.setDescription(request.description());
        compliance.setSubmissionNotes(request.submissionNotes());
        compliance.setStatus(ComplianceStatus.SUBMITTED);
        compliance.setSubmittedAt(LocalDateTime.now());
        compliance.setSubmittedBy(user);

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
        MilestoneCompliance saved = complianceRepository.save(compliance);

        // If milestone was marked non-compliant due to missing submission, re-evaluate
        if (milestone.getNonCompliant() && "Compliance evidence overdue".equalsIgnoreCase(milestone.getNonComplianceReason())) {
            milestone.setNonCompliant(false);
            milestone.setNonComplianceReason(null);
            milestoneRepository.save(milestone);
        }

        return toComplianceResponse(saved);
    }

    @Transactional(readOnly = true)
    public byte[] getComplianceDocumentBytes(Long complianceId) {
        MilestoneCompliance compliance = complianceRepository.findById(complianceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compliance record not found."));

        if (compliance.getDocumentData() == null || compliance.getDocumentData().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No document attached to this compliance record.");
        }
        return compliance.getDocumentData();
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
        if(officer.getRole()==Role.OFFICER && (milestone.getInstallment().getPlan().getApplication().getAssignedOfficer()==null
                || !Objects.equals(milestone.getInstallment().getPlan().getApplication().getAssignedOfficer().getId(),officer.getId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This application is outside your assigned workflow.");

        compliance.setVerifiedBy(officer);
        compliance.setVerifiedAt(LocalDateTime.now());
        compliance.setVerificationRemarks(request.remarks());

        if (Boolean.TRUE.equals(request.approved())) {
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
            }
        } else {
            // Flag as rejected and mark milestone non-compliant
            compliance.setStatus(ComplianceStatus.REJECTED);
            milestone.setNonCompliant(true);
            milestone.setNonComplianceReason("Compliance rejected: " + (request.remarks() != null ? request.remarks() : "Evidence does not satisfy criteria"));

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

        DisbursementInstallment installment = installmentRepository.findById(installmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Installment not found."));

        if (installment.getStatus() == InstallmentStatus.DISBURSED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Installment has already been disbursed. Duplicate payment release is prohibited.");
        }

        // FINANCIAL SAFETY: Ensure all milestone compliances are VERIFIED
        for (DisbursementMilestone m : installment.getMilestones()) {
            if (m.getNonCompliant()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Cannot release disbursement: Milestone '%s' is flagged as non-compliant (%s).",
                                m.getTitle(), m.getNonComplianceReason()));
            }
            for (MilestoneCompliance c : m.getCompliances()) {
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

        installment.setStatus(InstallmentStatus.DISBURSED);
        installment.setActualDisbursementDate(LocalDateTime.now());
        installment.setPaymentReference(request.paymentReference().trim());
        installment.setPaymentMethod(request.paymentMethod() != null ? request.paymentMethod().trim() : "DIRECT_BENEFIT_TRANSFER");
        installment.setDisbursedBy(admin);
        installment.setDisbursementRemarks(request.remarks());

        // Update plan disbursed totals
        DisbursementPlan plan = installment.getPlan();
        BigDecimal newTotalDisbursed = plan.getTotalDisbursedAmount().add(installment.getAmount());
        plan.setTotalDisbursedAmount(newTotalDisbursed);

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
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDisbursed = allInstallments.stream()
                .filter(inst -> inst.getStatus() == InstallmentStatus.DISBURSED)
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAllocated = BigDecimal.ZERO;
        List<SchemeFundUtilizationResponse> schemeBreakdowns = new ArrayList<>();

        for (Scheme s : allSchemes) {
            BigDecimal schemeAllocated = s.getAllocatedFunds();
            if (schemeAllocated == null || schemeAllocated.compareTo(BigDecimal.ZERO) <= 0) {
                // If not set, calculate default based on maximum grant cap * estimated capacity or default ₹10,000,000
                schemeAllocated = s.getMaximumGrantAmount() != null
                        ? s.getMaximumGrantAmount().multiply(BigDecimal.valueOf(50))
                        : BigDecimal.valueOf(10000000);
            }
            totalAllocated = totalAllocated.add(schemeAllocated);

            List<GrantApplication> sApps = approvedApps.stream()
                    .filter(a -> a.getScheme().getId().equals(s.getId()))
                    .collect(Collectors.toList());

            BigDecimal sApproved = sApps.stream()
                    .map(GrantApplication::getRequestedAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<DisbursementPlan> sPlans = allPlans.stream()
                    .filter(p -> p.getScheme().getId().equals(s.getId()))
                    .collect(Collectors.toList());

            BigDecimal sScheduled = sPlans.stream()
                    .flatMap(p -> p.getInstallments().stream())
                    .map(DisbursementInstallment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sDisbursed = sPlans.stream()
                    .flatMap(p -> p.getInstallments().stream())
                    .filter(i -> i.getStatus() == InstallmentStatus.DISBURSED)
                    .map(DisbursementInstallment::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sRemaining = schemeAllocated.subtract(sDisbursed);
            BigDecimal sUtilizationPct = schemeAllocated.compareTo(BigDecimal.ZERO) > 0
                    ? sDisbursed.multiply(BigDecimal.valueOf(100)).divide(schemeAllocated, 2, RoundingMode.HALF_UP)
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

        BigDecimal remainingAllocated = totalAllocated.subtract(totalDisbursed);
        BigDecimal remainingApproved = totalApproved.subtract(totalDisbursed);

        BigDecimal overallUtilization = totalAllocated.compareTo(BigDecimal.ZERO) > 0
                ? totalDisbursed.multiply(BigDecimal.valueOf(100)).divide(totalAllocated, 2, RoundingMode.HALF_UP)
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

                BigDecimal approvedAmount = approved.stream()
                        .map(GrantApplication::getRequestedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Set<Long> appIds = apps.stream().map(GrantApplication::getId).collect(Collectors.toSet());

                BigDecimal disbursedAmount = allPlans.stream()
                        .filter(p -> appIds.contains(p.getApplication().getId()))
                        .map(DisbursementPlan::getTotalDisbursedAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal pendingDisbursed = approvedAmount.subtract(disbursedAmount);
                if (pendingDisbursed.compareTo(BigDecimal.ZERO) < 0) {
                    pendingDisbursed = BigDecimal.ZERO;
                }

                List<DisbursementMilestone> regMilestones = allMilestones.stream()
                        .filter(m -> appIds.contains(m.getInstallment().getPlan().getApplication().getId()))
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

        BigDecimal scheduledTotal = plan.getInstallments().stream()
                .map(DisbursementInstallment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal remaining = plan.getTotalApprovedAmount().subtract(plan.getTotalDisbursedAmount());

        List<DisbursementInstallmentResponse> instResponses = plan.getInstallments().stream()
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
                plan.getTotalApprovedAmount(),
                plan.getTotalDisbursedAmount(),
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
        List<DisbursementMilestoneResponse> milestoneResponses = inst.getMilestones().stream()
                .map(this::toMilestoneResponse)
                .collect(Collectors.toList());

        boolean allVerified = !inst.getMilestones().isEmpty() && inst.getMilestones().stream().allMatch(m ->
                !m.getNonCompliant() && m.getCompliances().stream().allMatch(c -> !c.isRequired()
                        || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.APPROVED
                        || c.getStatus() == ComplianceStatus.WAIVED)
        );

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

        List<MilestoneComplianceResponse> compResponses = m.getCompliances().stream()
                .map(this::toComplianceResponse)
                .collect(Collectors.toList());

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
                m.getStatus(),
                m.getProgressPercentage(),
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
                c.getVerificationRemarks()
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
