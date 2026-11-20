package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                workflowAutomationService
        );

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

        InstallmentRequest i1 = new InstallmentRequest(1, "Stage 1", BigDecimal.valueOf(50), BigDecimal.valueOf(40000), LocalDate.now(), null);
        InstallmentRequest i2 = new InstallmentRequest(2, "Stage 2", BigDecimal.valueOf(50), BigDecimal.valueOf(40000), LocalDate.now().plusMonths(2), null);

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Initial approved plan", List.of(i1, i2));

        DisbursementPlanResponse response = disbursementService.createPlan(request, "admin@grantora.gov");

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(80000), response.totalApprovedAmount());
        assertEquals(2, response.installments().size());
        assertEquals(InstallmentStatus.READY_FOR_DISBURSEMENT, response.installments().get(0).status());
        assertEquals(InstallmentStatus.PLANNED, response.installments().get(1).status());
    }

    @Test
    void testCreatePlanFailsWhenTotalExceedsApprovedGrantAmount() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(planRepository.existsByApplication_IdAndPlanStatusNot(anyLong(), any())).thenReturn(false);

        // Requested 80,000, but installments sum to 90,000
        InstallmentRequest i1 = new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(50000), LocalDate.now(), null);
        InstallmentRequest i2 = new InstallmentRequest(2, "Stage 2", null, BigDecimal.valueOf(40000), LocalDate.now().plusMonths(2), null);

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Overbudget plan", List.of(i1, i2));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(exception.getReason().contains("exceeds approved grant amount"));
    }

    @Test
    void testCreatePlanFailsWhenApplicationNotApproved() {
        application.setStatus(GrantApplicationStatus.UNDER_VERIFICATION);
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));

        DisbursementPlanRequest request = new DisbursementPlanRequest(100L, "Unapproved plan", List.of(
                new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(80000), LocalDate.now(), null)
        ));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () ->
                disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(exception.getReason().contains("APPROVED status"));
    }

    @Test
    void testUpdateMilestoneProgressCompletesAt100() {
        when(userRepository.findByEmail("officer@grantora.gov")).thenReturn(Optional.of(officerUser));

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(301L);
        milestone.setTitle("Pump Delivery Verification");
        milestone.setStatus(MilestoneStatus.PENDING);
        milestone.setProgressPercentage(50);
        milestone.setNonCompliant(true);
        milestone.setNonComplianceReason("Pending inspection");

        when(milestoneRepository.findById(301L)).thenReturn(Optional.of(milestone));
        when(milestoneRepository.save(any(DisbursementMilestone.class))).thenAnswer(i -> i.getArgument(0));

        MilestoneProgressUpdateRequest req = new MilestoneProgressUpdateRequest(100, null, "All equipment installed");

        DisbursementMilestoneResponse response = disbursementService.updateMilestoneProgress(301L, req, "officer@grantora.gov");

        assertEquals(100, response.progressPercentage());
        assertEquals(MilestoneStatus.COMPLETED, response.status());
        assertFalse(response.nonCompliant());
        assertNull(response.nonComplianceReason());
    }

    @Test
    void testDisburseInstallmentFailsIfComplianceNotVerified() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setAmount(BigDecimal.valueOf(40000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setTitle("Procurement Inspection");
        milestone.setStatus(MilestoneStatus.PENDING);

        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setRequirementTitle("Vendor Bill");
        compliance.setStatus(ComplianceStatus.SUBMITTED); // NOT YET VERIFIED!
        milestone.addCompliance(compliance);

        installment.addMilestone(milestone);

        when(installmentRepository.findById(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("TXN12345678", "PFMS", "Stage 1 payment");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

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

        when(installmentRepository.findById(11L)).thenReturn(Optional.of(installment1));
        when(installmentRepository.save(any(DisbursementInstallment.class))).thenAnswer(i -> i.getArgument(0));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-2026-998877", "DIRECT_BENEFIT_TRANSFER", "Released after audit");

        DisbursementInstallmentResponse response = disbursementService.disburseInstallment(11L, req, "admin@grantora.gov");

        assertEquals(InstallmentStatus.DISBURSED, response.status());
        assertEquals("UTR-2026-998877", response.paymentReference());
        assertEquals(BigDecimal.valueOf(40000), plan.getTotalDisbursedAmount());
        assertEquals(InstallmentStatus.READY_FOR_DISBURSEMENT, installment2.getStatus());
    }

    @Test
    void testDisburseInstallmentPreventsDuplicateDisbursement() {
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setId(11L);
        installment.setStatus(InstallmentStatus.DISBURSED);

        when(installmentRepository.findById(11L)).thenReturn(Optional.of(installment));

        DisburseFundsRequest req = new DisburseFundsRequest("UTR-ALREADY-DONE", "NEFT", "Duplicate test");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                disbursementService.disburseInstallment(11L, req, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("already been disbursed"));
    }

    @Test
    void testVerifyComplianceRejectionFlagsNonCompliance() {
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

        ComplianceVerifyRequest verifyReq = new ComplianceVerifyRequest(false, "GPS tags are missing and photos are blurry.");

        MilestoneComplianceResponse response = disbursementService.verifyCompliance(99L, verifyReq, "officer@grantora.gov");

        assertEquals(ComplianceStatus.REJECTED, response.status());
        assertTrue(milestone.getNonCompliant());
        assertTrue(milestone.getNonComplianceReason().contains("GPS tags are missing"));
    }
}
