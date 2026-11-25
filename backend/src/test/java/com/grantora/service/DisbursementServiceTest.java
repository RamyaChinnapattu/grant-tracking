package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisbursementServiceTest {

    private DisbursementPlanRepository planRepository;
    private DisbursementInstallmentRepository installmentRepository;
    private DisbursementMilestoneRepository milestoneRepository;
    private MilestoneComplianceRepository complianceRepository;
    private DisbursementReminderConfigRepository reminderConfigRepository;
    private GrantApplicationRepository applicationRepository;
    private SchemeRepository schemeRepository;
    private UserRepository userRepository;
    private WorkflowAutomationService workflowAutomationService;

    private DisbursementService disbursementService;

    private User adminUser;
    private User officerUser;
    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private Scheme scheme;
    private GrantApplication application;

    @BeforeEach
    void setUp() {
        planRepository = mock(DisbursementPlanRepository.class);
        installmentRepository = mock(DisbursementInstallmentRepository.class);
        milestoneRepository = mock(DisbursementMilestoneRepository.class);
        complianceRepository = mock(MilestoneComplianceRepository.class);
        reminderConfigRepository = mock(DisbursementReminderConfigRepository.class);
        applicationRepository = mock(GrantApplicationRepository.class);
        schemeRepository = mock(SchemeRepository.class);
        userRepository = mock(UserRepository.class);
        workflowAutomationService = mock(WorkflowAutomationService.class);

        disbursementService = new DisbursementService(
                planRepository,
                installmentRepository,
                milestoneRepository,
                complianceRepository,
                reminderConfigRepository,
                applicationRepository,
                schemeRepository,
                userRepository,
                workflowAutomationService);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@grantora.gov");
        adminUser.setRole(Role.ADMIN);
        adminUser.setFullName("System Admin");

        officerUser = new User();
        officerUser.setId(2L);
        officerUser.setEmail("officer@grantora.gov");
        officerUser.setRole(Role.OFFICER);
        officerUser.setFullName("Field Officer");

        beneficiaryUser = new User();
        beneficiaryUser.setId(3L);
        beneficiaryUser.setEmail("farmer@example.com");
        beneficiaryUser.setRole(Role.BENEFICIARY);
        beneficiaryUser.setFullName("Ramesh Kumar");

        beneficiary = new Beneficiary();
        beneficiary.setId(10L);
        beneficiary.setUser(beneficiaryUser);
        beneficiary.setState("Karnataka");
        beneficiary.setDistrict("Mysuru");

        scheme = new Scheme();
        scheme.setId(20L);
        scheme.setSchemeName("Solar Agri Pump Subsidy");
        scheme.setMaximumGrantAmount(BigDecimal.valueOf(100000));
        scheme.setAllocatedFunds(BigDecimal.valueOf(5000000));

        application = new GrantApplication();
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setRequestedAmount(BigDecimal.valueOf(80000));
        application.setStatus(GrantApplicationStatus.APPROVED);
    }

    @Test
    void testCreatePlanSuccess() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(planRepository.existsByApplication_IdAndPlanStatusNot(anyLong(), any())).thenReturn(false);
        when(planRepository.save(any(DisbursementPlan.class))).thenAnswer(invocation -> {
            DisbursementPlan p = invocation.getArgument(0);
            p.setId(50L);
            return p;
        });

        InstallmentRequest i1 = new InstallmentRequest(1, "Stage 1", BigDecimal.valueOf(50), BigDecimal.valueOf(40000),
                LocalDate.now(), null);
        InstallmentRequest i2 = new InstallmentRequest(2, "Stage 2", BigDecimal.valueOf(50), BigDecimal.valueOf(40000),
                LocalDate.now().plusMonths(2), null);

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Initial approved plan", List.of(i1, i2));

        DisbursementPlanResponse response = disbursementService.createPlan(request, "admin@grantora.gov");

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(80000), response.totalApprovedAmount());
        assertEquals(2, response.installments().size());
        assertEquals(InstallmentStatus.PLANNED, response.installments().get(0).status());
        assertEquals(InstallmentStatus.PLANNED, response.installments().get(1).status());
    }

    @Test
    void testCreatePlanFailsWhenTotalExceedsApprovedGrantAmount() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(planRepository.existsByApplication_IdAndPlanStatusNot(anyLong(), any())).thenReturn(false);

        // Requested 80,000, but installments sum to 90,000
        InstallmentRequest i1 = new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(50000), LocalDate.now(),
                null);
        InstallmentRequest i2 = new InstallmentRequest(2, "Stage 2", null, BigDecimal.valueOf(40000),
                LocalDate.now().plusMonths(2), null);

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Overbudget plan", List.of(i1, i2));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(exception.getReason().contains("must equal approved grant amount"));
    }

    @Test
    void testCreatePlanFailsWhenApplicationNotApproved() {
        application.setStatus(GrantApplicationStatus.UNDER_VERIFICATION);
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Unapproved plan", List.of(
                new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(80000), LocalDate.now(), null)));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(exception.getReason().contains("APPROVED status"));
    }

    @Test
    void testPercentageScheduleMustTotalOneHundredPercent() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Bad percentages", List.of(
                new InstallmentRequest(1, "Stage 1", BigDecimal.valueOf(40), null, LocalDate.now(), null),
                new InstallmentRequest(2, "Stage 2", BigDecimal.valueOf(50), null, LocalDate.now().plusMonths(1),
                        null)));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));
        assertTrue(exception.getReason().contains("must total 100%"));
    }

    @Test
    void testDuplicateInstallmentSequenceIsRejected() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Duplicate sequence", List.of(
                new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(40000), LocalDate.now(), null),
                new InstallmentRequest(1, "Stage 1 duplicate", null, BigDecimal.valueOf(40000),
                        LocalDate.now().plusMonths(1), null)));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));
        assertTrue(exception.getReason().contains("positive and unique"));
    }

    @Test
    void testBeneficiaryScheduleQueryFiltersByAuthenticatedUser() {
        beneficiaryUser.setId(3L);
        DisbursementPlan own = new DisbursementPlan();
        own.setId(50L);
        own.setApplication(application);
        own.setScheme(scheme);
        own.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        own.setTotalDisbursedAmount(BigDecimal.ZERO);
        DisbursementPlan other = new DisbursementPlan();
        other.setId(51L);
        Beneficiary otherBeneficiary = new Beneficiary();
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setFullName("Other");
        otherBeneficiary.setUser(otherUser);
        GrantApplication otherApplication = new GrantApplication();
        otherApplication.setBeneficiary(otherBeneficiary);
        other.setApplication(otherApplication);
        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(planRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(own, other));

        List<DisbursementPlanResponse> response = disbursementService.getBeneficiaryPlans("farmer@example.com");
        assertEquals(1, response.size());
        assertEquals(50L, response.get(0).id());
    }

    @Test
    void testUpdateMilestoneProgressCompletesAt100() {
        application.setAssignedOfficer(officerUser);
        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(301L);
        milestone.setTitle("Pump Delivery Verification");
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setProgressPercentage(50);
        milestone.setNonCompliant(true);
        milestone.setNonComplianceReason("Pending inspection");
        milestone.setInstallment(installment);

        when(userRepository.findByEmail("officer@grantora.gov")).thenReturn(Optional.of(officerUser));
        when(milestoneRepository.findById(301L)).thenReturn(Optional.of(milestone));
        when(milestoneRepository.save(any(DisbursementMilestone.class))).thenAnswer(i -> i.getArgument(0));

        MilestoneProgressUpdateRequest req = new MilestoneProgressUpdateRequest(100, null, "All equipment installed");

        DisbursementMilestoneResponse response = disbursementService.updateMilestoneProgress(301L, req,
                "officer@grantora.gov");

        assertEquals(100, response.progressPercentage());
        assertEquals(MilestoneStatus.COMPLETED, response.status());
        assertFalse(response.nonCompliant());
        assertNull(response.nonComplianceReason());
    }

    @Test
    void testDisburseInstallmentFailsIfComplianceNotVerified() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setTitle("Procurement Inspection");
        milestone.setStatus(MilestoneStatus.COMPLETED);
        milestone.setProgressPercentage(100);
        milestone.setProgressPercentage(100);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setRequirementTitle("Vendor Bill");
        compliance.setStatus(ComplianceStatus.SUBMITTED); // NOT YET VERIFIED!
        milestone.addCompliance(compliance);

        installment.addMilestone(milestone);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("TXN12345678", "PFMS", "Stage 1 payment");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("All compliances must be VERIFIED before fund release"));
    }

    @Test
    void testDisburseInstallmentSuccessWhenCompliancesVerified() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);

        DisbursementInstallment installment1 = new DisbursementInstallment();
        installment1.setId(11L);
        installment1.setInstallmentNumber(1);
        installment1.setAmount(BigDecimal.valueOf(40000));
        installment1.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment1.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setTitle("Procurement Inspection");
        milestone.setStatus(MilestoneStatus.COMPLETED);
        milestone.setProgressPercentage(100);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setRequirementTitle("Vendor Bill");
        compliance.setStatus(ComplianceStatus.VERIFIED);
        milestone.addCompliance(compliance);

        installment1.addMilestone(milestone);

        DisbursementInstallment installment2 = new DisbursementInstallment();
        installment2.setId(12L);
        installment2.setInstallmentNumber(2);
        installment2.setAmount(BigDecimal.valueOf(40000));
        installment2.setStatus(InstallmentStatus.PLANNED);
        installment2.setPlan(plan);

        plan.addInstallment(installment1);
        plan.addInstallment(installment2);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment1));
        when(installmentRepository.save(any(DisbursementInstallment.class))).thenAnswer(i -> i.getArgument(0));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-2026-998877", "DIRECT_BENEFIT_TRANSFER",
                "Released after audit");

        DisbursementInstallmentResponse response = disbursementService.disburseInstallment(11L, req,
                "admin@grantora.gov");

        assertEquals(InstallmentStatus.DISBURSED, response.status());
        assertEquals("UTR-2026-998877", response.paymentReference());
        assertEquals(BigDecimal.valueOf(40000), plan.getTotalDisbursedAmount());
        assertEquals(InstallmentStatus.READY_FOR_DISBURSEMENT, installment2.getStatus());
    }

    @Test
    void testDisburseInstallmentPreventsDuplicateDisbursement() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.DISBURSED);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-ALREADY-DONE", "NEFT", "Duplicate test");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("already been disbursed"));
    }

    @Test
    void testVerifyComplianceRejectionFlagsNonCompliance() {
        application.setAssignedOfficer(officerUser);
        when(userRepository.findByEmail("officer@grantora.gov")).thenReturn(Optional.of(officerUser));
        when(userRepository.findAll()).thenReturn(List.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(404L);
        milestone.setTitle("Physical Site Construction");
        milestone.setInstallment(installment);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setId(99L);
        compliance.setRequirementTitle("Site Photos & GPS coordinates");
        compliance.setStatus(ComplianceStatus.SUBMITTED);
        compliance.setMilestone(milestone);
        milestone.addCompliance(compliance);

        when(complianceRepository.findById(99L)).thenReturn(Optional.of(compliance));
        when(complianceRepository.save(any(MilestoneCompliance.class))).thenAnswer(i -> i.getArgument(0));

        ComplianceVerifyRequest verifyReq = new ComplianceVerifyRequest(false,
                "GPS tags are missing and photos are blurry.");

        MilestoneComplianceResponse response = disbursementService.verifyCompliance(99L, verifyReq,
                "officer@grantora.gov");

        assertEquals(ComplianceStatus.REJECTED, response.status());
        assertTrue(milestone.getNonCompliant());
        assertTrue(milestone.getNonComplianceReason().contains("GPS tags are missing"));
    }

    @Test
    void testCreatePlanFailsWhenApprovedAmountExceedsSchemeMaximumGrant() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        scheme.setMaximumGrantAmount(BigDecimal.valueOf(50000));
        application.setRequestedAmount(BigDecimal.valueOf(80000));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Over grant cap", List.of(
                new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(80000), LocalDate.now(), null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("exceeds scheme maximum grant limit"));
    }

    @Test
    void testCreatePlanFailsWhenApprovedAmountExceedsSchemeAvailableBudget() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        scheme.setMaximumGrantAmount(BigDecimal.valueOf(100000));
        scheme.setAllocatedFunds(BigDecimal.valueOf(100000));
        application.setRequestedAmount(BigDecimal.valueOf(60000));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        DisbursementPlan existingPlan = new DisbursementPlan();
        existingPlan.setId(1L);
        existingPlan.setTotalApprovedAmount(BigDecimal.valueOf(60000));
        existingPlan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        when(planRepository.findByScheme_Id(scheme.getId())).thenReturn(List.of(existingPlan));

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Exceeds remaining budget", List.of(
                new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(60000), LocalDate.now(), null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("exceeds available scheme budget"));
    }

    @Test
    void testCreatePlanFailsWhenInstallmentSequencesAreNotConsecutive() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        // Sequences 1 and 3 (gap at 2)
        InstallmentRequest i1 = new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(40000), LocalDate.now(),
                null);
        InstallmentRequest i2 = new InstallmentRequest(3, "Stage 3", null, BigDecimal.valueOf(40000),
                LocalDate.now().plusMonths(2), null);

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Non consecutive", List.of(i1, i2));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("consecutive starting from 1"));
    }

    @Test
    void testDisburseInstallmentFailsWhenPriorInstallmentNotDisbursed() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment1 = new DisbursementInstallment();
        installment1.setId(11L);
        installment1.setInstallmentNumber(1);
        installment1.setAmount(BigDecimal.valueOf(40000));
        installment1.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment1.setPlan(plan);

        DisbursementInstallment installment2 = new DisbursementInstallment();
        installment2.setId(12L);
        installment2.setInstallmentNumber(2);
        installment2.setAmount(BigDecimal.valueOf(40000));
        installment2.setStatus(InstallmentStatus.PLANNED);
        installment2.setPlan(plan);

        plan.addInstallment(installment1);
        plan.addInstallment(installment2);

        when(installmentRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(installment2));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-STAGE2-EARLY", "DIRECT_BENEFIT_TRANSFER",
                "Attempting stage 2 first");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(12L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("Previous installments must be disbursed before releasing installment #2"));
    }

    @Test
    void testDisburseInstallmentFailsWhenPlanNotActive() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.CANCELLED);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.PLANNED);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-CANCELLED-PLAN", "DIRECT_BENEFIT_TRANSFER",
                "Disburse on cancelled");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("Cannot disburse installment: disbursement plan is CANCELLED"));
    }

    @Test
    void testDisburseInstallmentFailsWhenAmountExceedsApprovedGrant() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.valueOf(70000));
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(20000)); // 70k + 20k = 90k > 80k
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-OVER-CAP", "DIRECT_BENEFIT_TRANSFER", "Excess amount");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("exceeds total approved grant amount"));
    }

    @Test
    void testDisburseInstallmentFailsWhenApplicationNotApproved() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        application.setStatus(GrantApplicationStatus.FINANCE_REVIEW);

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-UNAPPROVED-APP", "DIRECT_BENEFIT_TRANSFER",
                "Unapproved app");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("Application has not received final Finance Officer approval"));
    }

    @Test
    void testDisburseInstallmentRecordsAuditLogAndUpdatesTotal() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(80000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));
        when(installmentRepository.save(any(DisbursementInstallment.class))).thenAnswer(i -> i.getArgument(0));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-AUDIT-TEST", "DIRECT_BENEFIT_TRANSFER",
                "Final single stage");

        DisbursementInstallmentResponse response = disbursementService.disburseInstallment(11L, req,
                "admin@grantora.gov");

        assertEquals(InstallmentStatus.DISBURSED, response.status());
        assertEquals(DisbursementPlanStatus.COMPLETED, plan.getPlanStatus());
        assertEquals(1, plan.getAuditHistory().size());
        assertEquals("DISBURSED", plan.getAuditHistory().get(0).getAction());
        assertTrue(plan.getAuditHistory().get(0).getDetails().contains("UTR-AUDIT-TEST"));
    }

    @Test
    void testDisburseInstallmentRejectsDuplicatePaymentReference() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(80000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);
        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));
        when(installmentRepository.existsByPaymentReference("UTR-ALREADY-USED")).thenReturn(true);

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-ALREADY-USED", "DIRECT_BENEFIT_TRANSFER",
                "Replay attack");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("already been recorded for another disbursement"));
    }

    @Test
    void testFundUtilizationSummaryExcludesCancelledPlans() {
        DisbursementPlan activePlan = new DisbursementPlan();
        activePlan.setId(1L);
        activePlan.setScheme(scheme);
        activePlan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment activeInst = new DisbursementInstallment();
        activeInst.setId(101L);
        activeInst.setAmount(BigDecimal.valueOf(50000));
        activeInst.setStatus(InstallmentStatus.PLANNED);
        activeInst.setPlan(activePlan);
        activePlan.addInstallment(activeInst);

        DisbursementPlan cancelledPlan = new DisbursementPlan();
        cancelledPlan.setId(2L);
        cancelledPlan.setScheme(scheme);
        cancelledPlan.setPlanStatus(DisbursementPlanStatus.CANCELLED);

        DisbursementInstallment cancelledInst = new DisbursementInstallment();
        cancelledInst.setId(102L);
        cancelledInst.setAmount(BigDecimal.valueOf(30000));
        cancelledInst.setStatus(InstallmentStatus.PLANNED);
        cancelledInst.setPlan(cancelledPlan);
        cancelledPlan.addInstallment(cancelledInst);

        when(schemeRepository.findAll()).thenReturn(List.of(scheme));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(application));
        when(planRepository.findAll()).thenReturn(List.of(activePlan, cancelledPlan));
        when(installmentRepository.findAll()).thenReturn(List.of(activeInst, cancelledInst));

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();

        // Total scheduled should only be activePlan's 50,000, NOT 80,000
        assertEquals(BigDecimal.valueOf(50000), summary.totalScheduledAmount());
        // Scheme breakdowns should only count 1 active plan
        assertEquals(1, summary.schemes().get(0).activePlansCount());
        assertEquals(BigDecimal.valueOf(50000), summary.schemes().get(0).scheduledAmount());
    }

    @Test
    void testDisburseInstallmentFailsWhenMilestoneNotCompleted() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(500L);
        plan.setApplication(application);
        plan.setTotalApprovedAmount(BigDecimal.valueOf(80000));
        plan.setTotalDisbursedAmount(BigDecimal.ZERO);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setInstallmentNumber(1);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);
        installment.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setTitle("Procurement Inspection");
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setProgressPercentage(50); // Not completed!
        milestone.setInstallment(installment);
        installment.addMilestone(milestone);

        plan.addInstallment(installment);

        when(installmentRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-INCOMPLETE", "DIRECT_BENEFIT_TRANSFER",
                "Early attempt");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("is not completed"));
    }

    @Test
    void testVerifyComplianceFailsWithoutDocumentData() {
        when(userRepository.findByEmail("officer@grantora.gov")).thenReturn(Optional.of(officerUser));
        application.setAssignedOfficer(officerUser);

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setInstallment(installment);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setId(55L);
        compliance.setMilestone(milestone);
        compliance.setStatus(ComplianceStatus.SUBMITTED);
        compliance.setDocumentData(null); // No document data!

        when(complianceRepository.findById(55L)).thenReturn(Optional.of(compliance));

        ComplianceVerifyRequest verifyReq = new ComplianceVerifyRequest(true, "Approving without document");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.verifyCompliance(55L, verifyReq, "officer@grantora.gov"));

        assertTrue(ex.getReason().contains("without an uploaded supporting document"));
    }

    @Test
    void testSubmitComplianceEnforcesBeneficiaryOwnership() {
        User stranger = new User();
        stranger.setId(88L);
        stranger.setEmail("stranger@example.com");
        stranger.setRole(Role.BENEFICIARY);

        when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application); // belongs to beneficiaryUser (id 3)
        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(77L);
        milestone.setInstallment(installment);

        when(milestoneRepository.findById(77L)).thenReturn(Optional.of(milestone));

        ComplianceSubmitRequest req = new ComplianceSubmitRequest("Proof", "Desc", "Notes");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.submitCompliance(77L, req, null, "stranger@example.com"));

        assertTrue(ex.getReason().contains("only for your own grant applications"));
    }

    @Test
    void testSubmitComplianceUpdatesExistingRecord() {
        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(beneficiaryUser));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);
        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(77L);
        milestone.setInstallment(installment);

        MilestoneCompliance existing = new MilestoneCompliance();
        existing.setId(99L);
        existing.setMilestone(milestone);
        existing.setRequirementTitle("Proof of Delivery");
        existing.setStatus(ComplianceStatus.REJECTED);
        milestone.addCompliance(existing);

        when(milestoneRepository.findById(77L)).thenReturn(Optional.of(milestone));
        when(complianceRepository.save(any(MilestoneCompliance.class))).thenAnswer(i -> i.getArgument(0));

        ComplianceSubmitRequest req = new ComplianceSubmitRequest("Proof of Delivery", "Corrected bill",
                "Updated invoice");

        MilestoneComplianceResponse response = disbursementService.submitCompliance(77L, req, null,
                "farmer@example.com");

        assertEquals(ComplianceStatus.SUBMITTED, response.status());
        assertEquals(99L, response.id()); // Updated same entity!
        assertEquals("Updated invoice", response.submissionNotes());
    }

    @Test
    void testComplianceDocumentDownloadAccessControl() {
        User stranger = new User();
        stranger.setId(88L);
        stranger.setEmail("stranger@example.com");
        stranger.setRole(Role.BENEFICIARY);

        when(userRepository.findByEmail("stranger@example.com")).thenReturn(Optional.of(stranger));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application); // belongs to beneficiaryUser (id 3)
        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setInstallment(installment);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setId(55L);
        compliance.setMilestone(milestone);
        compliance.setDocumentData("%PDF-confidential".getBytes());

        when(complianceRepository.findById(55L)).thenReturn(Optional.of(compliance));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                disbursementService.getComplianceEntity(55L, "stranger@example.com"));

        assertTrue(ex.getReason().contains("You do not have access to this compliance document"));
    }

    @Test
    void testGetBeneficiaryPlans_HandlesOrphanedOrIncompleteRelationshipsSafely() {
        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(beneficiaryUser));

        // 1. Valid plan belonging to farmer
        DisbursementPlan validPlan = new DisbursementPlan();
        validPlan.setId(101L);
        validPlan.setApplication(application); // beneficiaryUser id = 3

        // 2. Plan with null application
        DisbursementPlan orphanPlan1 = new DisbursementPlan();
        orphanPlan1.setId(102L);
        orphanPlan1.setApplication(null);

        // 3. Plan with application having null beneficiary
        GrantApplication appNoBen = new GrantApplication();
        ReflectionTestUtils.setField(appNoBen, "id", 201L);
        appNoBen.setBeneficiary(null);
        DisbursementPlan orphanPlan2 = new DisbursementPlan();
        orphanPlan2.setId(103L);
        orphanPlan2.setApplication(appNoBen);

        // 4. Plan with beneficiary having null user
        Beneficiary benNoUser = new Beneficiary();
        benNoUser.setId(301L);
        benNoUser.setUser(null);
        GrantApplication appNoUser = new GrantApplication();
        ReflectionTestUtils.setField(appNoUser, "id", 202L);
        appNoUser.setBeneficiary(benNoUser);
        DisbursementPlan orphanPlan3 = new DisbursementPlan();
        orphanPlan3.setId(104L);
        orphanPlan3.setApplication(appNoUser);

        // 5. Plan belonging to another user
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setEmail("other@example.com");
        Beneficiary otherBen = new Beneficiary();
        otherBen.setId(302L);
        otherBen.setUser(otherUser);
        GrantApplication otherApp = new GrantApplication();
        ReflectionTestUtils.setField(otherApp, "id", 203L);
        otherApp.setBeneficiary(otherBen);
        DisbursementPlan otherPlan = new DisbursementPlan();
        otherPlan.setId(105L);
        otherPlan.setApplication(otherApp);

        when(planRepository.findAllByOrderByCreatedAtDesc()).thenReturn(
                List.of(validPlan, orphanPlan1, orphanPlan2, orphanPlan3, otherPlan)
        );

        List<DisbursementPlanResponse> result = disbursementService.getBeneficiaryPlans("farmer@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).id());
    }

    @Test
    void createPlanUsesAdminConfiguredRequirementsInsteadOfDefaultPlaceholder() {
        ComplianceRequirementRepository reqRepo = mock(ComplianceRequirementRepository.class);
        DisbursementService serviceWithReqRepo = new DisbursementService(
                planRepository, installmentRepository, milestoneRepository, complianceRepository, reqRepo,
                reminderConfigRepository, applicationRepository, schemeRepository, userRepository, workflowAutomationService);

        ComplianceRequirement adminReq = new ComplianceRequirement();
        adminReq.setId(50L);
        adminReq.setScheme(scheme);
        adminReq.setTitle("fgrf");
        adminReq.setDescription("Admin configured requirement");
        adminReq.setRequired(true);
        adminReq.setActive(true);

        ReflectionTestUtils.setField(application, "id", 1L);
        when(userRepository.findByEmail(adminUser.getEmail())).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(planRepository.existsByApplication_Id(1L)).thenReturn(false);
        when(reqRepo.findByActiveTrue()).thenReturn(List.of(adminReq));
        when(planRepository.save(any())).thenAnswer(inv -> {
            DisbursementPlan p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        DisbursementPlanRequest req = new DisbursementPlanRequest(
                1L, "Notes",
                List.of(new InstallmentRequest(1, "Stage 1", BigDecimal.valueOf(100), BigDecimal.valueOf(80000), LocalDate.now(), List.of()))
        );

        DisbursementPlanResponse resp = serviceWithReqRepo.createPlan(req, adminUser.getEmail());
        var compliances = resp.installments().get(0).milestones().get(0).compliances();
        assertEquals(1, compliances.size());
        assertEquals("fgrf", compliances.get(0).requirementTitle());
        assertTrue(compliances.get(0).required());
    }

    @Test
    void fundUtilizationCalculatesDisbursementProgressOverCommittedAndHandlesZeroCommitted() {
        Scheme agriScheme = new Scheme();
        agriScheme.setId(20L);
        agriScheme.setSchemeName("Agriculture Development Grant");
        agriScheme.setDepartment("Agriculture");
        agriScheme.setAllocatedFunds(BigDecimal.valueOf(1000000));

        Scheme emptyScheme = new Scheme();
        emptyScheme.setId(21L);
        emptyScheme.setSchemeName("Zero Committed Scheme");
        emptyScheme.setDepartment("Education");
        emptyScheme.setAllocatedFunds(BigDecimal.valueOf(500000));

        application.setScheme(agriScheme);
        application.setRequestedAmount(BigDecimal.valueOf(45000));
        application.setStatus(GrantApplicationStatus.APPROVED);

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(1L);
        plan.setScheme(agriScheme);
        plan.setApplication(application);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment inst1 = new DisbursementInstallment();
        inst1.setPlan(plan);
        inst1.setInstallmentNumber(1);
        inst1.setAmount(BigDecimal.valueOf(17500));
        inst1.setStatus(InstallmentStatus.DISBURSED);

        DisbursementInstallment inst2 = new DisbursementInstallment();
        inst2.setPlan(plan);
        inst2.setInstallmentNumber(2);
        inst2.setAmount(BigDecimal.valueOf(27500));
        inst2.setStatus(InstallmentStatus.PLANNED);

        plan.setInstallments(List.of(inst1, inst2));

        when(schemeRepository.findAll()).thenReturn(List.of(agriScheme, emptyScheme));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(application));
        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(installmentRepository.findAll()).thenReturn(List.of(inst1, inst2));

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();
        assertEquals(0, BigDecimal.valueOf(1500000).compareTo(summary.totalAllocatedFunds()));
        assertEquals(0, BigDecimal.valueOf(45000).compareTo(summary.totalApprovedAmount()));
        assertEquals(0, BigDecimal.valueOf(17500).compareTo(summary.totalDisbursedAmount()));
        assertEquals(0, BigDecimal.valueOf(1455000).compareTo(summary.remainingAllocatedAmount()));

        SchemeFundUtilizationResponse agriRow = summary.schemes().stream()
                .filter(r -> r.schemeId().equals(20L)).findFirst().orElseThrow();
        // 17500 / 45000 * 100 = 38.89%
        assertEquals(0, new BigDecimal("38.89").compareTo(agriRow.utilizationPercentage()));

        SchemeFundUtilizationResponse zeroRow = summary.schemes().stream()
                .filter(r -> r.schemeId().equals(21L)).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(zeroRow.utilizationPercentage()));
    }

    @Test
    void fundUtilizationPreservesNullAllocatedFundsAsUnconfigured() {
        Scheme configured = new Scheme();
        configured.setId(20L);
        configured.setSchemeName("Configured Scheme");
        configured.setAllocatedFunds(BigDecimal.valueOf(65000000));

        Scheme unconfigured = new Scheme();
        unconfigured.setId(21L);
        unconfigured.setSchemeName("Agriculture Development Grant");
        unconfigured.setAllocatedFunds(null);

        when(schemeRepository.findAll()).thenReturn(List.of(configured, unconfigured));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());
        when(installmentRepository.findAll()).thenReturn(List.of());

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();
        assertEquals(0, BigDecimal.valueOf(65000000).compareTo(summary.totalAllocatedFunds()));

        SchemeFundUtilizationResponse unconfiguredRow = summary.schemes().stream()
                .filter(r -> r.schemeId().equals(21L)).findFirst().orElseThrow();
        assertNull(unconfiguredRow.allocatedFunds());
        assertNull(unconfiguredRow.remainingAmount());
    }
}
