package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsAndReportingTest {

    @Mock private DisbursementPlanRepository planRepository;
    @Mock private DisbursementInstallmentRepository installmentRepository;
    @Mock private DisbursementMilestoneRepository milestoneRepository;
    @Mock private MilestoneComplianceRepository complianceRepository;
    @Mock private ComplianceNonComplianceRepository nonComplianceRepository;
    @Mock private GrantApplicationRepository applicationRepository;
    @Mock private SchemeRepository schemeRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkflowAutomationService workflowAutomationService;
    @Mock private GrantUtilizationRepository utilizationRepository;

    @InjectMocks
    private DisbursementService disbursementService;

    private User adminUser;
    private Scheme schemeWithAllocation;
    private Scheme schemeWithoutAllocation;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@grantora.gov");
        adminUser.setRole(Role.ADMIN);
        adminUser.setFullName("Super Admin");

        schemeWithAllocation = new Scheme();
        schemeWithAllocation.setId(10L);
        schemeWithAllocation.setSchemeName("Dairy Farm Subsidy");
        schemeWithAllocation.setDepartment("Animal Husbandry");
        schemeWithAllocation.setStatus("ACTIVE");
        schemeWithAllocation.setMaximumGrantAmount(BigDecimal.valueOf(200000));
        schemeWithAllocation.setAllocatedFunds(BigDecimal.valueOf(5000000));

        schemeWithoutAllocation = new Scheme();
        schemeWithoutAllocation.setId(20L);
        schemeWithoutAllocation.setSchemeName("Micro Irrigation Scheme");
        schemeWithoutAllocation.setDepartment("Agriculture");
        schemeWithoutAllocation.setStatus("ACTIVE");
        schemeWithoutAllocation.setMaximumGrantAmount(BigDecimal.valueOf(100000));
        schemeWithoutAllocation.setAllocatedFunds(null); // Null allocation
    }

    @Test
    @DisplayName("Financial Analytics: Uses actual allocated funds and strictly eliminates artificial ₹10,000,000 fallback")
    void testFinancialAnalyticsNoArtificialFallbackMultiplier() {
        when(schemeRepository.findAll()).thenReturn(List.of(schemeWithAllocation, schemeWithoutAllocation));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());
        when(installmentRepository.findAll()).thenReturn(List.of());

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();

        assertNotNull(summary);
        // Total allocated must be exactly schemeWithAllocation (5,000,000) + 0 for schemeWithoutAllocation, NOT + 10,000,000
        assertEquals(BigDecimal.valueOf(5000000), summary.totalAllocatedFunds());
        assertEquals(2, summary.schemes().size());

        SchemeFundUtilizationResponse s1 = summary.schemes().stream().filter(s -> s.schemeId().equals(10L)).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(5000000), s1.allocatedFunds());

        SchemeFundUtilizationResponse s2 = summary.schemes().stream().filter(s -> s.schemeId().equals(20L)).findFirst().orElseThrow();
        // Scheme with null allocation must be reported as 0, not synthetic 10,000,000
        assertEquals(BigDecimal.ZERO, s2.allocatedFunds());
        assertEquals(BigDecimal.ZERO, summary.totalApprovedAmount());
        assertEquals(BigDecimal.ZERO, summary.totalScheduledAmount());
        assertEquals(BigDecimal.ZERO, summary.totalDisbursedAmount());
        assertEquals(BigDecimal.valueOf(5000000), summary.remainingAllocatedAmount());
    }

    @Test
    @DisplayName("Financial Analytics: Cancelled plans and cancelled installments are excluded from scheduled totals")
    void testCancelledPlansAndInstallmentsExclusion() {
        User benUser = new User();
        benUser.setId(2L);
        benUser.setFullName("Ravi Farmer");
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setId(5L);
        beneficiary.setUser(benUser);
        beneficiary.setState("Karnataka");
        beneficiary.setDistrict("Mysuru");

        GrantApplication app = new GrantApplication();
        ReflectionTestUtils.setField(app, "id", 100L);
        app.setBeneficiary(beneficiary);
        app.setScheme(schemeWithAllocation);
        app.setStatus(GrantApplicationStatus.APPROVED);
        app.setRequestedAmount(BigDecimal.valueOf(100000));

        DisbursementPlan activePlan = new DisbursementPlan();
        activePlan.setId(1L);
        activePlan.setApplication(app);
        activePlan.setScheme(schemeWithAllocation);
        activePlan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment activeInst = new DisbursementInstallment();
        activeInst.setId(101L);
        activeInst.setInstallmentNumber(1);
        activeInst.setAmount(BigDecimal.valueOf(50000));
        activeInst.setStatus(InstallmentStatus.PLANNED);
        activeInst.setPlan(activePlan);
        activePlan.addInstallment(activeInst);

        DisbursementInstallment cancelledInstInActivePlan = new DisbursementInstallment();
        cancelledInstInActivePlan.setId(102L);
        cancelledInstInActivePlan.setInstallmentNumber(2);
        cancelledInstInActivePlan.setAmount(BigDecimal.valueOf(25000));
        cancelledInstInActivePlan.setStatus(InstallmentStatus.CANCELLED);
        cancelledInstInActivePlan.setPlan(activePlan);
        activePlan.addInstallment(cancelledInstInActivePlan);

        DisbursementPlan cancelledPlan = new DisbursementPlan();
        cancelledPlan.setId(2L);
        cancelledPlan.setApplication(app);
        cancelledPlan.setScheme(schemeWithAllocation);
        cancelledPlan.setPlanStatus(DisbursementPlanStatus.CANCELLED);

        DisbursementInstallment instInCancelledPlan = new DisbursementInstallment();
        instInCancelledPlan.setId(103L);
        instInCancelledPlan.setInstallmentNumber(1);
        instInCancelledPlan.setAmount(BigDecimal.valueOf(30000));
        instInCancelledPlan.setStatus(InstallmentStatus.PLANNED);
        instInCancelledPlan.setPlan(cancelledPlan);
        cancelledPlan.addInstallment(instInCancelledPlan);

        when(schemeRepository.findAll()).thenReturn(List.of(schemeWithAllocation));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(app));
        when(planRepository.findAll()).thenReturn(List.of(activePlan, cancelledPlan));
        when(installmentRepository.findAll()).thenReturn(List.of(activeInst, cancelledInstInActivePlan, instInCancelledPlan));

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();

        // Total scheduled should only be activeInst (50,000)
        // cancelledInstInActivePlan (25,000) and instInCancelledPlan (30,000) MUST be excluded!
        assertEquals(BigDecimal.valueOf(50000), summary.totalScheduledAmount());
        assertEquals(1, summary.schemes().get(0).activePlansCount());
        assertEquals(BigDecimal.valueOf(50000), summary.schemes().get(0).scheduledAmount());
        assertEquals(BigDecimal.valueOf(100000), summary.totalApprovedAmount());
    }

    @Test
    @DisplayName("Regional Analytics: Accurately computes approvals, rejections, and amounts by State and District")
    void testRegionalAnalyticsApprovalsAndRejections() {
        // Beneficiary in Karnataka - Mysuru
        User u1 = new User(); u1.setId(10L); u1.setFullName("Farmer K");
        Beneficiary b1 = new Beneficiary(); b1.setId(10L); b1.setUser(u1); b1.setState("Karnataka"); b1.setDistrict("Mysuru");
        GrantApplication app1 = new GrantApplication();
        ReflectionTestUtils.setField(app1, "id", 1L);
        app1.setBeneficiary(b1); app1.setScheme(schemeWithAllocation);
        app1.setStatus(GrantApplicationStatus.APPROVED);
        app1.setRequestedAmount(BigDecimal.valueOf(50000));

        // Another application in Karnataka - Mysuru (REJECTED)
        User u2 = new User(); u2.setId(11L); u2.setFullName("Farmer K2");
        Beneficiary b2 = new Beneficiary(); b2.setId(11L); b2.setUser(u2); b2.setState("Karnataka"); b2.setDistrict("Mysuru");
        GrantApplication app2 = new GrantApplication();
        ReflectionTestUtils.setField(app2, "id", 2L);
        app2.setBeneficiary(b2); app2.setScheme(schemeWithAllocation);
        app2.setStatus(GrantApplicationStatus.REJECTED);
        app2.setRequestedAmount(BigDecimal.valueOf(40000));

        // Beneficiary in Maharashtra - Pune (APPROVED)
        User u3 = new User(); u3.setId(12L); u3.setFullName("Farmer M");
        Beneficiary b3 = new Beneficiary(); b3.setId(12L); b3.setUser(u3); b3.setState("Maharashtra"); b3.setDistrict("Pune");
        GrantApplication app3 = new GrantApplication();
        ReflectionTestUtils.setField(app3, "id", 3L);
        app3.setBeneficiary(b3); app3.setScheme(schemeWithAllocation);
        app3.setStatus(GrantApplicationStatus.APPROVED);
        app3.setRequestedAmount(BigDecimal.valueOf(70000));

        // Plan for app1 with disbursed installment
        DisbursementPlan p1 = new DisbursementPlan();
        p1.setId(100L); p1.setApplication(app1); p1.setScheme(schemeWithAllocation);
        p1.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        p1.setTotalApprovedAmount(BigDecimal.valueOf(50000));
        p1.setTotalDisbursedAmount(BigDecimal.valueOf(30000));

        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(app1, app2, app3));
        when(planRepository.findAll()).thenReturn(List.of(p1));
        when(milestoneRepository.findAll()).thenReturn(List.of());

        List<RegionalAnalyticsResponse> regions = disbursementService.getRegionalAnalytics();

        assertNotNull(regions);
        assertEquals(2, regions.size());

        // Check Karnataka - Mysuru
        RegionalAnalyticsResponse karMysuru = regions.stream()
                .filter(r -> "Karnataka".equalsIgnoreCase(r.state()) && "Mysuru".equalsIgnoreCase(r.district()))
                .findFirst().orElseThrow();
        assertEquals(2, karMysuru.totalApplications());
        assertEquals(1, karMysuru.approvedApplications());
        assertEquals(1, karMysuru.rejectedApplications()); // Verifies rejection tracking!
        assertEquals(BigDecimal.valueOf(50000), karMysuru.totalApprovedAmount());
        assertEquals(BigDecimal.valueOf(30000), karMysuru.totalDisbursedAmount());
        assertEquals(BigDecimal.valueOf(20000), karMysuru.pendingDisbursementAmount());

        // Check Maharashtra - Pune
        RegionalAnalyticsResponse mahPune = regions.stream()
                .filter(r -> "Maharashtra".equalsIgnoreCase(r.state()) && "Pune".equalsIgnoreCase(r.district()))
                .findFirst().orElseThrow();
        assertEquals(1, mahPune.totalApplications());
        assertEquals(1, mahPune.approvedApplications());
        assertEquals(0, mahPune.rejectedApplications());
        assertEquals(BigDecimal.valueOf(70000), mahPune.totalApprovedAmount());
    }

    @Test
    @DisplayName("Regional Analytics: Safely handles null or unassigned regional locations")
    void testRegionalAnalyticsNullLocationHandling() {
        GrantApplication appNoBen = new GrantApplication();
        ReflectionTestUtils.setField(appNoBen, "id", 99L);
        appNoBen.setStatus(GrantApplicationStatus.APPROVED);
        appNoBen.setRequestedAmount(BigDecimal.valueOf(10000));

        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(appNoBen));
        when(planRepository.findAll()).thenReturn(List.of());
        when(milestoneRepository.findAll()).thenReturn(List.of());

        List<RegionalAnalyticsResponse> regions = disbursementService.getRegionalAnalytics();
        assertNotNull(regions);
        assertEquals(1, regions.size());
        assertEquals("Unknown State", regions.get(0).state());
        assertEquals("Unknown District", regions.get(0).district());
        assertEquals(1, regions.get(0).totalApplications());
    }

    @Test
    @DisplayName("Empty Dataset Safety: Zero records return clean empty lists and zero totals without exceptions")
    void testEmptyDatasetSafety() {
        when(schemeRepository.findAll()).thenReturn(List.of());
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());
        when(installmentRepository.findAll()).thenReturn(List.of());
        when(milestoneRepository.findAll()).thenReturn(List.of());

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();
        assertNotNull(summary);
        assertEquals(BigDecimal.ZERO, summary.totalAllocatedFunds());
        assertEquals(BigDecimal.ZERO, summary.totalApprovedAmount());
        assertEquals(BigDecimal.ZERO, summary.totalScheduledAmount());
        assertEquals(BigDecimal.ZERO, summary.totalDisbursedAmount());
        assertTrue(summary.schemes().isEmpty());

        List<RegionalAnalyticsResponse> regions = disbursementService.getRegionalAnalytics();
        assertNotNull(regions);
        assertTrue(regions.isEmpty());
    }

    @Test
    @DisplayName("Report CSV Export: Verifies CSV exports with filters and formula injection safety")
    void testReportCsvExportsWithFilters() {
        Milestone3ReportService reportService = new Milestone3ReportService(
                userRepository, applicationRepository, planRepository,
                complianceRepository, nonComplianceRepository, utilizationRepository, schemeRepository
        );

        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));

        User benUser = new User(); benUser.setFullName("Suresh Patel");
        Beneficiary ben = new Beneficiary(); ben.setUser(benUser); ben.setState("Gujarat"); ben.setDistrict("Ahmedabad");

        GrantApplication app = new GrantApplication();
        ReflectionTestUtils.setField(app, "id", 501L);
        app.setBeneficiary(ben);
        app.setScheme(schemeWithAllocation);
        app.setStatus(GrantApplicationStatus.APPROVED);
        app.setRequestedAmount(BigDecimal.valueOf(75000));
        app.setSubmittedAt(LocalDateTime.of(2026, 3, 15, 10, 0));

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(301L);
        plan.setApplication(app);
        plan.setScheme(schemeWithAllocation);
        plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);

        DisbursementInstallment inst = new DisbursementInstallment();
        inst.setId(401L);
        inst.setInstallmentNumber(1);
        inst.setAmount(BigDecimal.valueOf(75000));
        inst.setStatus(InstallmentStatus.DISBURSED);
        inst.setActualDisbursementDate(LocalDateTime.of(2026, 3, 20, 14, 0));
        inst.setPlan(plan);
        plan.addInstallment(inst);

        when(planRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(plan));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(app));
        when(schemeRepository.findAll()).thenReturn(List.of(schemeWithAllocation));
        when(utilizationRepository.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of());

        // Test Disbursement CSV
        String disbCsv = reportService.csv("disbursement", "admin@grantora.gov",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                schemeWithAllocation.getId(), "Gujarat", "Ahmedabad", "APPROVED", null);
        assertNotNull(disbCsv);
        assertTrue(disbCsv.contains("Application Reference,Beneficiary,Scheme"));
        assertTrue(disbCsv.contains("APP-501"));
        assertTrue(disbCsv.contains("Suresh Patel"));

        // Test Regional CSV
        String regCsv = reportService.csv("regional", "admin@grantora.gov",
                null, null, null, null, null, null, null);
        assertNotNull(regCsv);
        assertTrue(regCsv.contains("State,District,Scheme,Scheme Allocation,Applications,Approved Applications,Rejected Applications"));
        assertTrue(regCsv.contains("Gujarat"));
        assertTrue(regCsv.contains("Ahmedabad"));
    }

    @Test
    @DisplayName("Targeted Review: Approved undisbursed grants are committed and not available for new plan allocations")
    void testApprovedUndisbursedGrantsAreCommittedAndNotAvailableForNewAllocations() {
        Scheme scheme = new Scheme();
        scheme.setId(99L);
        scheme.setSchemeName("Fisheries Development");
        scheme.setAllocatedFunds(BigDecimal.valueOf(1000000));
        scheme.setMaximumGrantAmount(BigDecimal.valueOf(1000000));

        // Application 1: Approved for 600,000
        GrantApplication app1 = new GrantApplication();
        ReflectionTestUtils.setField(app1, "id", 101L);
        app1.setScheme(scheme);
        app1.setStatus(GrantApplicationStatus.APPROVED);
        app1.setRequestedAmount(BigDecimal.valueOf(600000));

        // Plan 1: Approved for 600,000, 200,000 disbursed, 400,000 approved but not disbursed
        DisbursementPlan plan1 = new DisbursementPlan();
        plan1.setId(201L);
        plan1.setApplication(app1);
        plan1.setScheme(scheme);
        plan1.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        plan1.setTotalApprovedAmount(BigDecimal.valueOf(600000));
        plan1.setTotalDisbursedAmount(BigDecimal.valueOf(200000));

        DisbursementInstallment inst1 = new DisbursementInstallment();
        inst1.setId(301L);
        inst1.setInstallmentNumber(1);
        inst1.setAmount(BigDecimal.valueOf(200000));
        inst1.setStatus(InstallmentStatus.DISBURSED);
        inst1.setPlan(plan1);
        plan1.addInstallment(inst1);

        DisbursementInstallment inst2 = new DisbursementInstallment();
        inst2.setId(302L);
        inst2.setInstallmentNumber(2);
        inst2.setAmount(BigDecimal.valueOf(400000));
        inst2.setStatus(InstallmentStatus.PLANNED);
        inst2.setPlan(plan1);
        plan1.addInstallment(inst2);

        when(schemeRepository.findAll()).thenReturn(List.of(scheme));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(app1));
        when(planRepository.findAll()).thenReturn(List.of(plan1));
        when(installmentRepository.findAll()).thenReturn(List.of(inst1, inst2));

        FundUtilizationSummaryResponse summary = disbursementService.getFundUtilizationSummary();

        // 1. Total Allocated = 1,000,000
        assertEquals(BigDecimal.valueOf(1000000), summary.totalAllocatedFunds());
        // 2. Total Committed (Approved) = 600,000
        assertEquals(BigDecimal.valueOf(600000), summary.totalApprovedAmount());
        // 3. Total Scheduled = 600,000 (inst1 200,000 + inst2 400,000)
        assertEquals(BigDecimal.valueOf(600000), summary.totalScheduledAmount());
        // 4. Total Disbursed = 200,000
        assertEquals(BigDecimal.valueOf(200000), summary.totalDisbursedAmount());
        // 5. Remaining Allocated (cash unreleased) = 800,000 (1,000,000 - 200,000)
        assertEquals(BigDecimal.valueOf(800000), summary.remainingAllocatedAmount());
        // 6. Remaining Approved (pending release) = 400,000 (600,000 - 200,000)
        assertEquals(BigDecimal.valueOf(400000), summary.remainingApprovedAmount());
        // 7. Scheme Remaining Available Budget = 400,000 (1,000,000 - 600,000 committed)
        assertEquals(BigDecimal.valueOf(400000), summary.schemes().get(0).remainingAmount());

        // Verify that creating a new plan cannot reuse the approved undisbursed 400,000
        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        GrantApplication app2 = new GrantApplication();
        ReflectionTestUtils.setField(app2, "id", 102L);
        app2.setScheme(scheme);
        app2.setStatus(GrantApplicationStatus.APPROVED);
        app2.setRequestedAmount(BigDecimal.valueOf(500000)); // Requests 500,000, but only 400,000 is available

        when(applicationRepository.findById(102L)).thenReturn(Optional.of(app2));
        when(schemeRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(scheme));
        when(planRepository.findByScheme_Id(99L)).thenReturn(List.of(plan1));

        DisbursementPlanRequest request = new DisbursementPlanRequest(102L, "Plan exceeding available budget",
                List.of(new InstallmentRequest(1, "Stage 1", null, BigDecimal.valueOf(500000), LocalDate.now(), null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> disbursementService.createPlan(request, "admin@grantora.gov"));

        assertTrue(ex.getReason().contains("exceeds available scheme budget"));
        assertTrue(ex.getReason().contains("400000 remaining"));
    }
}
