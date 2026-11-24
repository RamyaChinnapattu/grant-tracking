package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrantoraMilestone2EndToEndIntegrationTest {

    private SchemeRepository schemeRepository;
    private BeneficiaryRepository beneficiaryRepository;
    private GrantApplicationRepository applicationRepository;
    private GrantApplicationStatusHistoryRepository statusHistoryRepository;
    private ApplicationVerificationRepository verificationRepository;
    private ApplicationDocumentRepository documentRepository;
    private UserRepository userRepository;
    private VerificationRoutingRuleRepository routingRules;
    private WorkflowEscalationRepository escalationRepository;
    private WorkflowDeadlineConfigRepository deadlineConfigRepository;
    private InAppNotificationRepository notificationRepository;

    private DisbursementPlanRepository planRepository;
    private DisbursementInstallmentRepository installmentRepository;
    private DisbursementMilestoneRepository milestoneRepository;
    private MilestoneComplianceRepository complianceRepository;
    private DisbursementReminderConfigRepository reminderConfigRepository;

    private SchemeService schemeService;
    private BeneficiaryService beneficiaryService;
    private EligibilityScoringService eligibilityScoringService;
    private VerificationWorkflowService verificationWorkflowService;
    private WorkflowAutomationService workflowAutomationService;
    private GrantApplicationService applicationService;
    private DisbursementService disbursementService;

    private User adminUser;
    private User beneficiaryUser;
    private User fieldOfficerUser;
    private User districtOfficerUser;
    private User financeOfficerUser;

    private Beneficiary beneficiary;
    private Scheme scheme;

    @BeforeEach
    void setUp() throws Exception {
        schemeRepository = mock(SchemeRepository.class);
        beneficiaryRepository = mock(BeneficiaryRepository.class);
        applicationRepository = mock(GrantApplicationRepository.class);
        statusHistoryRepository = mock(GrantApplicationStatusHistoryRepository.class);
        verificationRepository = mock(ApplicationVerificationRepository.class);
        documentRepository = mock(ApplicationDocumentRepository.class);
        userRepository = mock(UserRepository.class);
        routingRules = mock(VerificationRoutingRuleRepository.class);
        escalationRepository = mock(WorkflowEscalationRepository.class);
        deadlineConfigRepository = mock(WorkflowDeadlineConfigRepository.class);
        notificationRepository = mock(InAppNotificationRepository.class);

        planRepository = mock(DisbursementPlanRepository.class);
        installmentRepository = mock(DisbursementInstallmentRepository.class);
        milestoneRepository = mock(DisbursementMilestoneRepository.class);
        complianceRepository = mock(MilestoneComplianceRepository.class);
        reminderConfigRepository = mock(DisbursementReminderConfigRepository.class);

        eligibilityScoringService = new EligibilityScoringService();
        schemeService = new SchemeService(schemeRepository, beneficiaryRepository, eligibilityScoringService);
        beneficiaryService = new BeneficiaryService(beneficiaryRepository, userRepository);

        verificationWorkflowService = new VerificationWorkflowService(
                applicationRepository,
                userRepository,
                verificationRepository,
                documentRepository,
                routingRules);
        verificationWorkflowService.setWorkflowEscalationRepository(escalationRepository);

        workflowAutomationService = new WorkflowAutomationService(
                deadlineConfigRepository,
                applicationRepository,
                escalationRepository,
                notificationRepository,
                userRepository);
        verificationWorkflowService.setWorkflowAutomationService(workflowAutomationService);

        applicationService = new GrantApplicationService(
                applicationRepository,
                statusHistoryRepository,
                beneficiaryRepository,
                schemeRepository,
                eligibilityScoringService,
                verificationWorkflowService,
                verificationRepository,
                documentRepository,
                userRepository,
                null);

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

        // Users
        adminUser = createUser(1L, "admin@grantora.gov", Role.ADMIN, null, null, null);
        beneficiaryUser = createUser(2L, "beneficiary@citizen.org", Role.BENEFICIARY, null, "Karnataka", "Bengaluru Urban");
        fieldOfficerUser = createUser(10L, "field.officer@grantora.gov", Role.FIELD_OFFICER, "FIELD", "Karnataka", "Bengaluru Urban");
        districtOfficerUser = createUser(20L, "district.officer@grantora.gov", Role.DISTRICT_OFFICER, "DISTRICT", "Karnataka", "Bengaluru Urban");
        financeOfficerUser = createUser(30L, "finance.officer@grantora.gov", Role.FINANCE_OFFICER, "FINANCE", "Karnataka", null);

        when(userRepository.findByEmail("admin@grantora.gov")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmail("beneficiary@citizen.org")).thenReturn(Optional.of(beneficiaryUser));
        when(userRepository.findByEmail("field.officer@grantora.gov")).thenReturn(Optional.of(fieldOfficerUser));
        when(userRepository.findByEmail("district.officer@grantora.gov")).thenReturn(Optional.of(districtOfficerUser));
        when(userRepository.findByEmail("finance.officer@grantora.gov")).thenReturn(Optional.of(financeOfficerUser));
        when(userRepository.findAll()).thenReturn(List.of(fieldOfficerUser, districtOfficerUser, financeOfficerUser, adminUser));

        // Default mock behaviors
        when(applicationRepository.save(any(GrantApplication.class))).thenAnswer(inv -> inv.getArgument(0));
        when(verificationRepository.save(any(ApplicationVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(GrantApplicationStatusHistory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Complete 17-Step End-to-End Workflow: Scheme Creation -> Verification (Field, District, Finance) -> Disbursement")
    void testComplete17StepEndToEndWorkflow() throws Exception {
        // Step 1: Admin creates and activates a grant scheme
        scheme = new Scheme();
        setId(scheme, 100L);
        scheme.setSchemeName("National Agricultural Solar Subsidy");
        scheme.setStatus("ACTIVE");
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(65);
        scheme.setMaximumAnnualIncome(new BigDecimal("500000.00"));
        scheme.setEligibleCategories(Set.of("GENERAL", "OBC", "SC", "ST"));
        scheme.setEligibilityThreshold(new BigDecimal("75.00"));
        scheme.setMaximumGrantAmount(new BigDecimal("200000.00"));
        scheme.setAllocatedFunds(new BigDecimal("10000000.00"));

        SchemeEligibilityQuestion q1 = new SchemeEligibilityQuestion();
        setId(q1, 1L);
        q1.setQuestionText("Owns agricultural land?");
        q1.setQuestionType("YES_NO");
        q1.setRequired(true);
        q1.setMaximumPoints(new BigDecimal("50.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)));

        SchemeEligibilityQuestion q2 = new SchemeEligibilityQuestion();
        setId(q2, 2L);
        q2.setQuestionText("Grid electricity deficit in area?");
        q2.setQuestionType("YES_NO");
        q2.setRequired(true);
        q2.setMaximumPoints(new BigDecimal("50.00"));
        q2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)));

        scheme.setEligibilityQuestions(List.of(q1, q2));
        when(schemeRepository.findById(100L)).thenReturn(Optional.of(scheme));

        // Step 2: Beneficiary registers and maintains their profile
        beneficiary = new Beneficiary();
        setId(beneficiary, 200L);
        beneficiary.setUser(beneficiaryUser);
        beneficiary.setDateOfBirth(LocalDate.of(1990, 5, 15)); // Age 36 (within 18-65)
        beneficiary.setAnnualIncome(new BigDecimal("250000.00")); // <= 500000
        beneficiary.setCategory("OBC");
        beneficiary.setState("Karnataka");
        beneficiary.setDistrict("Bengaluru Urban");
        beneficiary.setAddress("Village 1, Taluk North");

        when(beneficiaryRepository.findByUser_Email("beneficiary@citizen.org")).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.findByUser_Id(2L)).thenReturn(Optional.of(beneficiary));
        when(beneficiaryRepository.findById(200L)).thenReturn(Optional.of(beneficiary));

        // Step 3: Beneficiary selects the active scheme & verifies eligibility preview
        EligibilityEvaluationResult preview = schemeService.evaluateEligibility(
                100L, 200L, "beneficiary@citizen.org",
                List.of(new EligibilityAnswer(1L, "YES"), new EligibilityAnswer(2L, "YES")));
        assertEquals("ELIGIBLE", preview.eligibilityStatus());
        assertEquals(new BigDecimal("100.00"), preview.scoreOutOf100());

        // Step 4: Beneficiary completes and submits an application draft
        GrantApplication application = new GrantApplication();
        setId(application, 500L);
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setApplicationDetails("Valid agricultural solar grant details");
        application.setRequestedAmount(new BigDecimal("150000.00"));
        application.setStatus(GrantApplicationStatus.DRAFT);
        application.setEligibilityAnswers(List.of(
                new EligibilityAnswer(1L, "YES"),
                new EligibilityAnswer(2L, "YES")));

        when(applicationRepository.findById(500L)).thenReturn(Optional.of(application));
        when(applicationRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(application));

        // Step 7: Document completeness check (verify upload required before submission)
        when(documentRepository.countByApplication_Id(500L)).thenReturn(0L);
        assertThrows(ResponseStatusException.class, () ->
                applicationService.submitDraft(500L, "beneficiary@citizen.org"));

        // Add valid supporting document
        ApplicationDocument doc = new ApplicationDocument();
        setId(doc, 700L);
        doc.setApplication(application);
        doc.setFileName("land_deed.pdf");
        doc.setContentType("application/pdf");
        doc.setFileSize(2048L);
        doc.setContent("%PDF-1.4 sample content".getBytes());
        doc.setUploadedBy(beneficiaryUser);
        setField(doc, "uploadedAt", LocalDateTime.now());

        when(documentRepository.countByApplication_Id(500L)).thenReturn(1L);
        when(documentRepository.findByApplication_Id(500L)).thenReturn(List.of(doc));

        // Steps 5, 6, 8, 9: Submit draft -> backend evaluates rules, calculates weighted score, validates docs, moves to Level 1
        GrantApplicationResponse submitResponse = applicationService.submitDraft(500L, "beneficiary@citizen.org");
        assertNotNull(submitResponse);
        assertEquals(GrantApplicationStatus.PENDING_FIELD_VERIFICATION, application.getStatus());
        assertEquals(VerificationStage.FIELD, application.getWorkflowStage());
        assertNotNull(application.getAssignedOfficer());
        assertEquals(fieldOfficerUser.getId(), application.getAssignedOfficer().getId());

        // Step 10: Field Officer reviews application and records findings (Level 1)
        VerificationDecisionRequest fieldDecision = new VerificationDecisionRequest(
                VerificationDecision.VERIFIED,
                "Inspected agricultural land; solar installation site confirmed viable.",
                true,  // identityVerified
                true,  // addressVerified
                true,  // documentsVerified
                true,  // applicationDetailsVerified
                null, null, null, null
        );
        GrantApplicationResponse l1Response = applicationService.decide(500L, "field.officer@grantora.gov", false, fieldDecision);

        // Step 11: Approved application moves to District Officer verification (Level 2)
        assertEquals(GrantApplicationStatus.PENDING_DISTRICT_VERIFICATION, application.getStatus());
        assertEquals(VerificationStage.DISTRICT, application.getWorkflowStage());
        assertEquals(districtOfficerUser.getId(), application.getAssignedOfficer().getId());

        // Step 12: District Officer reviews Field report & eligibility findings and records approval (Level 2)
        VerificationDecisionRequest districtDecision = new VerificationDecisionRequest(
                VerificationDecision.APPROVED,
                "Reviewed Field Officer findings and applicant credentials; recommended for solar subsidy.",
                false, false, false, false,
                null, null, null, null
        );
        GrantApplicationResponse l2Response = applicationService.decide(500L, "district.officer@grantora.gov", false, districtDecision);

        // Step 13: Approved application moves to Finance Officer verification (Level 3)
        assertEquals(GrantApplicationStatus.PENDING_FINANCE_VERIFICATION, application.getStatus());
        assertEquals(VerificationStage.FINANCE, application.getWorkflowStage());

        // Steps 14 & 15: Finance Officer reviews grant amount, financial compliance & budget availability, records approval
        VerificationDecisionRequest financeDecision = new VerificationDecisionRequest(
                VerificationDecision.APPROVED,
                "Budget availability verified under FY26 Solar Allocation. Compliant with financial ceiling.",
                false, false, false, false,
                null, null,
                true, // financialComplianceVerified
                true  // budgetAvailable
        );
        GrantApplicationResponse l3Response = applicationService.decide(500L, "finance.officer@grantora.gov", false, financeDecision);

        assertEquals(GrantApplicationStatus.FINANCE_APPROVED, application.getStatus());
        assertTrue(application.getStatus().isApproved());

        // Step 16: Finance-approved application moves to disbursement scheduling
        InstallmentRequest inst1 = new InstallmentRequest(1, "Initial Site Preparation & Equipment Advance", new BigDecimal("50.00"), new BigDecimal("75000.00"), LocalDate.now().plusDays(30), List.of());
        InstallmentRequest inst2 = new InstallmentRequest(2, "Completion & Commissioning Balance", new BigDecimal("50.00"), new BigDecimal("75000.00"), LocalDate.now().plusDays(60), List.of());
        DisbursementPlanRequest planRequest = new DisbursementPlanRequest(500L, "Milestone 2 solar disbursement plan", List.of(inst1, inst2));

        when(planRepository.save(any(DisbursementPlan.class))).thenAnswer(inv -> {
            DisbursementPlan plan = inv.getArgument(0);
            setId(plan, 900L);
            return plan;
        });

        DisbursementPlanResponse planResponse = disbursementService.createPlan(planRequest, "admin@grantora.gov");
        assertNotNull(planResponse);
        assertEquals(500L, planResponse.applicationId());
        assertEquals(new BigDecimal("150000.00"), planResponse.totalApprovedAmount());
        assertEquals(2, planResponse.installments().size());

        // Step 17: Application status and history correctly reflected
        when(applicationRepository.findByBeneficiary_IdOrderByIdDesc(200L)).thenReturn(List.of(application));
        List<GrantApplicationResponse> myApps = applicationService.getMyApplications("beneficiary@citizen.org");
        assertEquals(1, myApps.size());
        assertEquals(GrantApplicationStatus.FINANCE_APPROVED, myApps.get(0).status());

        when(applicationRepository.findAllByStatusOrderByIdDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(application)));
        Page<GrantApplicationResponse> adminApps = applicationService.getAdminApplications(
                GrantApplicationStatus.FINANCE_APPROVED, PageRequest.of(0, 10));
        assertNotNull(adminApps);
        assertEquals(1, adminApps.getTotalElements());
        assertEquals(GrantApplicationStatus.FINANCE_APPROVED, adminApps.getContent().get(0).status());
    }

    @Test
    @DisplayName("Verification Rejections at each level: Field, District, and Finance Officers")
    void testRejectionsAtEachLevel() throws Exception {
        // Level 1 Field Officer Rejection
        GrantApplication appL1 = createTestApplication(501L, VerificationStage.FIELD, GrantApplicationStatus.PENDING_FIELD_VERIFICATION, fieldOfficerUser);
        when(applicationRepository.findById(501L)).thenReturn(Optional.of(appL1));
        when(applicationRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(appL1));

        VerificationDecisionRequest fieldReject = new VerificationDecisionRequest(
                VerificationDecision.REJECTED, "Land deed does not match beneficiary identity.", false, false, false, false,
                "IDENTITY_MISMATCH", null, null, null);
        applicationService.decide(501L, "field.officer@grantora.gov", false, fieldReject);
        assertEquals(GrantApplicationStatus.REJECTED, appL1.getStatus());

        // Level 2 District Officer Rejection
        GrantApplication appL2 = createTestApplication(502L, VerificationStage.DISTRICT, GrantApplicationStatus.PENDING_DISTRICT_VERIFICATION, districtOfficerUser);
        when(applicationRepository.findById(502L)).thenReturn(Optional.of(appL2));
        when(applicationRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(appL2));

        VerificationDecisionRequest districtReject = new VerificationDecisionRequest(
                VerificationDecision.REJECTED, "Applicant income documents show discrepancy exceeding scheme ceiling.", false, false, false, false,
                "INCOME_EXCEEDED", null, null, null);
        applicationService.decide(502L, "district.officer@grantora.gov", false, districtReject);
        assertEquals(GrantApplicationStatus.REJECTED, appL2.getStatus());

        // Level 3 Finance Officer Rejection
        GrantApplication appL3 = createTestApplication(503L, VerificationStage.FINANCE, GrantApplicationStatus.PENDING_FINANCE_VERIFICATION, financeOfficerUser);
        when(applicationRepository.findById(503L)).thenReturn(Optional.of(appL3));
        when(applicationRepository.findByIdForUpdate(503L)).thenReturn(Optional.of(appL3));

        VerificationDecisionRequest financeReject = new VerificationDecisionRequest(
                VerificationDecision.REJECTED, "Exhausted department budget allocation for this quarter.", false, false, false, false,
                "BUDGET_EXHAUSTED", null, false, false);
        applicationService.decide(503L, "finance.officer@grantora.gov", false, financeReject);
        assertEquals(GrantApplicationStatus.FINANCE_REJECTED, appL3.getStatus());
        assertTrue(appL3.getStatus().isRejected());
    }

    @Test
    @DisplayName("Correction Request at each level and Beneficiary Resubmission")
    void testCorrectionRequestAndResubmission() throws Exception {
        // District Officer requests correction
        GrantApplication app = createTestApplication(504L, VerificationStage.DISTRICT, GrantApplicationStatus.PENDING_DISTRICT_VERIFICATION, districtOfficerUser);
        when(applicationRepository.findById(504L)).thenReturn(Optional.of(app));
        when(applicationRepository.findByIdForUpdate(504L)).thenReturn(Optional.of(app));

        VerificationDecisionRequest correctionRequest = new VerificationDecisionRequest(
                VerificationDecision.NEEDS_CORRECTION, "Please re-upload a clear copy of the electricity bill.", false, false, false, false,
                null, "BLURRY_DOCUMENT", null, null);
        applicationService.decide(504L, "district.officer@grantora.gov", false, correctionRequest);
        assertEquals(GrantApplicationStatus.CORRECTION_REQUIRED, app.getStatus());
        assertEquals(VerificationStage.DISTRICT, app.getWorkflowStage()); // Stage preserved

        // Beneficiary resubmission resets status to PENDING_DISTRICT_VERIFICATION
        when(documentRepository.countByApplication_Id(504L)).thenReturn(1L);
        applicationService.submitDraft(504L, "beneficiary@citizen.org");
        assertEquals(GrantApplicationStatus.PENDING_DISTRICT_VERIFICATION, app.getStatus());
    }

    @Test
    @DisplayName("Role-Based Security: Beneficiary & Cross-Officer Unauthorized Decisions Blocked")
    void testUnauthorizedDecisionsBlocked() throws Exception {
        GrantApplication app = createTestApplication(505L, VerificationStage.FIELD, GrantApplicationStatus.PENDING_FIELD_VERIFICATION, fieldOfficerUser);
        when(applicationRepository.findById(505L)).thenReturn(Optional.of(app));
        when(applicationRepository.findByIdForUpdate(505L)).thenReturn(Optional.of(app));

        VerificationDecisionRequest decision = new VerificationDecisionRequest(
                VerificationDecision.VERIFIED, "Self verification attempt.", true, true, true, true, null, null, null, null);

        // 1. Beneficiary cannot make verification decisions
        assertThrows(ResponseStatusException.class, () ->
                applicationService.decide(505L, "beneficiary@citizen.org", false, decision));

        // 2. District Officer cannot perform Level 1 Field decision
        assertThrows(ResponseStatusException.class, () ->
                applicationService.decide(505L, "district.officer@grantora.gov", false, decision));

        // 3. Finance Officer cannot perform Level 1 Field decision
        assertThrows(ResponseStatusException.class, () ->
                applicationService.decide(505L, "finance.officer@grantora.gov", false, decision));

        // 4. Another Field Officer outside assigned district/jurisdiction cannot decide
        User otherFieldOfficer = createUser(99L, "other.field@grantora.gov", Role.FIELD_OFFICER, "FIELD", "OtherState", "OtherDistrict");
        when(userRepository.findByEmail("other.field@grantora.gov")).thenReturn(Optional.of(otherFieldOfficer));
        assertThrows(ResponseStatusException.class, () ->
                applicationService.decide(505L, "other.field@grantora.gov", false, decision));
    }

    @Test
    @DisplayName("Escalation Automation & Duplicate Escalation Prevention")
    void testEscalationAndDuplicatePrevention() throws Exception {
        GrantApplication overdueApp = createTestApplication(506L, VerificationStage.FIELD, GrantApplicationStatus.PENDING_FIELD_VERIFICATION, fieldOfficerUser);
        overdueApp.setTaskDueAt(LocalDateTime.now().minusDays(5));
        when(applicationRepository.findById(506L)).thenReturn(Optional.of(overdueApp));
        when(applicationRepository.findByIdForUpdate(506L)).thenReturn(Optional.of(overdueApp));
        when(applicationRepository.findAllByOrderByIdDesc()).thenReturn(List.of(overdueApp));

        // Initial escalation
        when(escalationRepository.existsByApplication_IdAndStageAndResolvedFalse(506L, VerificationStage.FIELD))
                .thenReturn(false);

        workflowAutomationService.detectOverdue();
        verify(escalationRepository, atLeastOnce()).save(any(WorkflowEscalation.class));
        assertTrue(overdueApp.isEscalated());

        // Duplicate escalation check: when an open escalation already exists, no second escalation is created
        WorkflowEscalation existingEscalation = new WorkflowEscalation();
        existingEscalation.setApplication(overdueApp);
        existingEscalation.setStage(VerificationStage.FIELD);
        existingEscalation.setAssignedOfficer(fieldOfficerUser);
        when(escalationRepository.findByApplication_IdAndStageAndResolvedFalse(506L, VerificationStage.FIELD))
                .thenReturn(List.of(existingEscalation));

        // Manual escalation attempt on already escalated application is rejected
        assertThrows(ResponseStatusException.class, () ->
                applicationService.escalate(506L, "admin@grantora.gov", true));
    }

    // Helper methods
    private User createUser(Long id, String email, Role role, String officerLevel, String state, String district) throws Exception {
        User u = new User();
        setId(u, id);
        u.setEmail(email);
        u.setRole(role);
        u.setFullName("User " + id);
        u.setOfficerLevel(officerLevel);
        u.setJurisdictionState(state);
        u.setJurisdictionDistrict(district);
        u.setActive(true);
        return u;
    }

    private GrantApplication createTestApplication(Long id, VerificationStage stage, GrantApplicationStatus status, User officer) throws Exception {
        GrantApplication app = new GrantApplication();
        setId(app, id);
        Beneficiary ben = new Beneficiary();
        setId(ben, 200L);
        ben.setUser(beneficiaryUser);
        ben.setDateOfBirth(LocalDate.of(1990, 5, 15));
        ben.setAnnualIncome(new BigDecimal("50000.00"));
        ben.setCategory("OBC");
        ben.setState("Karnataka");
        ben.setDistrict("Bengaluru Urban");
        app.setBeneficiary(ben);

        Scheme sc = new Scheme();
        setId(sc, 100L);
        sc.setSchemeName("Solar Subsidy");
        sc.setStatus("ACTIVE");
        sc.setMinimumAge(18);
        sc.setMaximumAge(65);
        sc.setMaximumAnnualIncome(new BigDecimal("500000.00"));
        sc.setEligibleCategories(Set.of("OBC"));
        sc.setEligibilityThreshold(new BigDecimal("70.00"));

        SchemeEligibilityQuestion q = new SchemeEligibilityQuestion();
        setId(q, 10L);
        q.setQuestionText("Eligibility Question");
        q.setQuestionType("YES_NO");
        q.setMaximumPoints(new BigDecimal("100.00"));
        q.setRequired(true);
        q.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        sc.setEligibilityQuestions(List.of(q));

        app.setScheme(sc);
        app.setApplicationDetails("Valid test application details for grant review");
        app.setRequestedAmount(new BigDecimal("100000.00"));
        app.setWorkflowStage(stage);
        app.setStatus(status);
        app.setAssignedOfficer(officer);
        app.setEligibilityAnswers(List.of(new EligibilityAnswer(10L, "YES")));
        return app;
    }

    private void setId(Object target, Long id) throws Exception {
        setField(target, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
