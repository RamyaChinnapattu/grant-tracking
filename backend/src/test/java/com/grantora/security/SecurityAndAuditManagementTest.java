package com.grantora.security;

import com.grantora.dto.AuditLogResponse;
import com.grantora.entity.*;
import com.grantora.repository.*;
import com.grantora.service.*;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SecurityAndAuditManagementTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private SystemAuditLogRepository systemAuditLogRepository;
    private GrantApplicationStatusHistoryRepository statusHistoryRepository;
    private ApplicationVerificationRepository verificationRepository;
    private DisbursementPlanAuditRepository planAuditRepository;
    private ComplianceReviewHistoryRepository complianceReviewHistoryRepository;
    private GrantApplicationRepository applicationRepository;
    private SchemeRepository schemeRepository;

    private AuditService auditService;
    private UserService userService;
    private SchemeService schemeService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        systemAuditLogRepository = mock(SystemAuditLogRepository.class);
        statusHistoryRepository = mock(GrantApplicationStatusHistoryRepository.class);
        verificationRepository = mock(ApplicationVerificationRepository.class);
        planAuditRepository = mock(DisbursementPlanAuditRepository.class);
        complianceReviewHistoryRepository = mock(ComplianceReviewHistoryRepository.class);
        applicationRepository = mock(GrantApplicationRepository.class);
        schemeRepository = mock(SchemeRepository.class);

        auditService = new AuditService(
                systemAuditLogRepository,
                statusHistoryRepository,
                verificationRepository,
                planAuditRepository,
                complianceReviewHistoryRepository,
                applicationRepository
        );

        userService = new UserService(userRepository, passwordEncoder, auditService);
        schemeService = new SchemeService(schemeRepository, null, null, applicationRepository, auditService);

        // Custom secure 256-bit key for unit testing
        String secret = Base64.getEncoder().encodeToString(
                "unit-test-secure-256-bit-key-for-security-tests-grantora!".getBytes());
        jwtService = new JwtService(secret, 3600000L); // 1 hour expiration

        // Set mock admin authentication in SecurityContext
        var auth = new UsernamePasswordAuthenticationToken(
                "admin@grantora.gov.in",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -------------------------------------------------------------------------
    // 1. JWT Security & Validation Tests
    // -------------------------------------------------------------------------
    @Test
    void jwtValidation_shouldValidateCorrectTokenAndRejectMismatchedEmail() {
        User user = new User();
        user.setId(1L);
        user.setEmail("officer@grantora.gov.in");
        user.setRole(Role.OFFICER);

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        // Valid token with correct email
        assertTrue(jwtService.isTokenValid(token, "officer@grantora.gov.in"));

        // Invalid when email doesn't match subject
        assertFalse(jwtService.isTokenValid(token, "imposter@grantora.gov.in"));
    }

    @Test
    void jwtValidation_shouldRejectExpiredToken() {
        String secret = Base64.getEncoder().encodeToString(
                "unit-test-secure-256-bit-key-for-security-tests-grantora!".getBytes());
        JwtService expiredJwtService = new JwtService(secret, -5000L); // Negative expiration -> expired instantly

        User user = new User();
        user.setId(2L);
        user.setEmail("expired@grantora.gov.in");
        user.setRole(Role.BENEFICIARY);

        String token = expiredJwtService.generateToken(user);

        // Expired token must throw JwtException or return false
        assertThrows(JwtException.class, () -> jwtService.isTokenValid(token, "expired@grantora.gov.in"));
    }

    @Test
    void jwtValidation_shouldRejectTokenSignedWithDifferentSecret() {
        String otherSecret = "YW5vdGhlci1kaWZmZXJlbnQtc2VjcmV0LWtleS0yNTZiaXRzLWZvci10ZXN0aW5nIQ==";
        JwtService rogueJwtService = new JwtService(otherSecret, 3600000L);

        User user = new User();
        user.setId(3L);
        user.setEmail("hacker@grantora.gov.in");
        user.setRole(Role.ADMIN);

        String rogueToken = rogueJwtService.generateToken(user);

        assertThrows(JwtException.class, () -> jwtService.isTokenValid(rogueToken, "hacker@grantora.gov.in"));
    }

    // -------------------------------------------------------------------------
    // 2. User Update Email Uniqueness & Privilege Boundaries
    // -------------------------------------------------------------------------
    @Test
    void updateUser_shouldRejectEmailCollisionWithAnotherUser() {
        User existingUser = new User();
        existingUser.setId(5L);
        existingUser.setEmail("original@grantora.gov.in");
        existingUser.setRole(Role.BENEFICIARY);

        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("taken@grantora.gov.in")).thenReturn(true);

        User updatedData = new User();
        updatedData.setEmail("taken@grantora.gov.in");
        updatedData.setFullName("Updated Name");
        updatedData.setRole(Role.BENEFICIARY);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.updateUser(5L, updatedData));

        assertEquals(409, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Email already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldAllowKeepingSameEmailAndRecordAudit() {
        User existingUser = new User();
        existingUser.setId(5L);
        existingUser.setEmail("same@grantora.gov.in");
        existingUser.setFullName("Old Name");
        existingUser.setRole(Role.BENEFICIARY);

        when(userRepository.findById(5L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User updatedData = new User();
        updatedData.setEmail("same@grantora.gov.in");
        updatedData.setFullName("New Name");
        updatedData.setRole(Role.BENEFICIARY);

        User saved = userService.updateUser(5L, updatedData);
        assertEquals("New Name", saved.getFullName());
        assertEquals("same@grantora.gov.in", saved.getEmail());

        // Verify audit record created for user update
        verify(systemAuditLogRepository).save(argThat(log ->
                "USER_UPDATED".equals(log.getAction()) &&
                "ADMIN_MANAGEMENT".equals(log.getCategory()) &&
                log.getTargetId() == 5L));
    }

    // -------------------------------------------------------------------------
    // 3. Officer Level & Jurisdiction Constraints (FIELD, DISTRICT, FINANCE only)
    // -------------------------------------------------------------------------
    @Test
    void createOfficer_shouldRejectInvalidOfficerLevel() {
        User officer = new User();
        officer.setEmail("state.officer@grantora.gov.in");
        officer.setFullName("Invalid Officer");
        officer.setPassword("ValidPassword123!");
        officer.setRole(Role.OFFICER);
        officer.setOfficerLevel("STATE"); // Obsolete/invalid level
        officer.setJurisdictionState("Maharashtra");

        when(userRepository.existsByEmail(any())).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(officer));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Officer level must be FIELD, DISTRICT, or FINANCE"));
    }

    @Test
    void createOfficer_shouldRequireDistrictForFieldAndDistrictOfficers() {
        User fieldOfficer = new User();
        fieldOfficer.setEmail("field@grantora.gov.in");
        fieldOfficer.setFullName("Field Officer");
        fieldOfficer.setPassword("ValidPassword123!");
        fieldOfficer.setRole(Role.OFFICER);
        fieldOfficer.setOfficerLevel("FIELD");
        fieldOfficer.setJurisdictionState("Maharashtra");
        fieldOfficer.setJurisdictionDistrict(""); // Missing district

        when(userRepository.existsByEmail(any())).thenReturn(false);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(fieldOfficer));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Field and district officers require a district"));
    }

    @Test
    void createOfficer_shouldAllowFinanceOfficerWithoutDistrict() {
        User financeOfficer = new User();
        financeOfficer.setEmail("finance@grantora.gov.in");
        financeOfficer.setFullName("Finance Officer");
        financeOfficer.setPassword("ValidPassword123!");
        financeOfficer.setRole(Role.OFFICER);
        financeOfficer.setOfficerLevel("FINANCE");
        financeOfficer.setJurisdictionState("Maharashtra");
        financeOfficer.setJurisdictionDistrict(null);

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-pwd");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(20L);
            return u;
        });

        User saved = userService.createUser(financeOfficer);
        assertNotNull(saved);
        assertEquals("FINANCE", saved.getOfficerLevel());
        assertNull(saved.getJurisdictionDistrict());

        // Verify audit log
        verify(systemAuditLogRepository).save(argThat(log ->
                "USER_CREATED".equals(log.getAction()) &&
                log.getDetails().contains("FINANCE")));
    }

    // -------------------------------------------------------------------------
    // 4. Scheme & Budget Administrative Auditing
    // -------------------------------------------------------------------------
    @Test
    void schemeCreationAndUpdate_shouldRecordAdministrativeAuditEvents() {
        Scheme scheme = new Scheme();
        scheme.setId(10L);
        scheme.setSchemeName("Krishi Vikas Subsidy");
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(60);
        scheme.setMaximumAnnualIncome(new BigDecimal("300000.00"));
        scheme.setEligibleCategories(Set.of("GENERAL", "OBC"));
        scheme.setEligibilityThreshold(new BigDecimal("60.00"));
        scheme.setAllocatedFunds(new BigDecimal("5000000.00"));
        scheme.setMaximumGrantAmount(new BigDecimal("100000.00"));

        SchemeEligibilityQuestion question = new SchemeEligibilityQuestion();
        question.setQuestionText("Are you an eligible farmer?");
        question.setQuestionType("YES_NO");
        question.setRequired(true);
        question.setMaximumPoints(new BigDecimal("100.00"));
        question.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        scheme.setEligibilityQuestions(List.of(question));

        when(schemeRepository.save(any(Scheme.class))).thenReturn(scheme);
        when(schemeRepository.findById(10L)).thenReturn(Optional.of(scheme));

        schemeService.createScheme(scheme);
        verify(systemAuditLogRepository).save(argThat(log ->
                "SCHEME_CREATED".equals(log.getAction()) &&
                "SCHEME".equals(log.getCategory()) &&
                log.getTargetId() == 10L));

        Scheme updateData = new Scheme();
        updateData.setSchemeName("Krishi Vikas Subsidy");
        updateData.setMinimumAge(18);
        updateData.setMaximumAge(60);
        updateData.setMaximumAnnualIncome(new BigDecimal("300000.00"));
        updateData.setEligibleCategories(Set.of("GENERAL", "OBC"));
        updateData.setEligibilityThreshold(new BigDecimal("60.00"));
        updateData.setAllocatedFunds(new BigDecimal("7500000.00"));
        updateData.setMaximumGrantAmount(new BigDecimal("100000.00"));
        updateData.setEligibilityQuestions(List.of(question));

        schemeService.updateScheme(10L, updateData);
        verify(systemAuditLogRepository).save(argThat(log ->
                "SCHEME_BUDGET_UPDATED".equals(log.getAction()) &&
                "SCHEME".equals(log.getCategory()) &&
                log.getTargetId() == 10L));
    }

    // -------------------------------------------------------------------------
    // 5. Unified Audit Retrieval, Chronological Ordering & Data Protection
    // -------------------------------------------------------------------------
    @Test
    void getUnifiedAuditLogs_shouldAggregateAllCategoriesInChronologicalOrder() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 22, 10, 0, 0);

        // 1. System audit (10:00)
        SystemAuditLog sysLog = new SystemAuditLog();
        sysLog.setActorEmail("admin@grantora.gov.in");
        sysLog.setActorRole("ADMIN");
        sysLog.setAction("USER_CREATED");
        sysLog.setCategory("ADMIN_MANAGEMENT");
        sysLog.setTargetEntity("User");
        sysLog.setTargetId(1L);
        sysLog.setDetails("Created user officer1@grantora.gov.in");
        sysLog.setTimestamp(base);
        when(systemAuditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(List.of(sysLog));

        // Mock application
        GrantApplication app = new GrantApplication();
        setEntityField(app, "id", 101L);
        Scheme scheme = new Scheme();
        scheme.setSchemeName("Solar Subsidy");
        app.setScheme(scheme);
        Beneficiary ben = new Beneficiary();
        User benUser = new User();
        benUser.setFullName("Ramesh Kumar");
        ben.setUser(benUser);
        app.setBeneficiary(ben);
        when(applicationRepository.findAll()).thenReturn(List.of(app));

        // 2. Application Verification (11:00)
        ApplicationVerification ver = new ApplicationVerification();
        ver.setApplication(app);
        ver.setStage(VerificationStage.FIELD);
        ver.setAction("VERIFIED");
        User fieldOfficer = new User();
        fieldOfficer.setFullName("Field Officer A");
        fieldOfficer.setEmail("field.a@grantora.gov.in");
        fieldOfficer.setRole(Role.OFFICER);
        fieldOfficer.setOfficerLevel("FIELD");
        ver.setActor(fieldOfficer);
        ver.setRemarks("Physical farm inspection verified.");
        setEntityField(ver, "createdAt", base.plusHours(1));
        when(verificationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(ver));

        // 3. Application Lifecycle (09:00 - earlier than sysLog)
        GrantApplicationStatusHistory appHist = new GrantApplicationStatusHistory();
        appHist.setApplicationId(101L);
        appHist.setAction("SUBMITTED");
        appHist.setRemarks("Citizen submitted application.");
        setEntityField(appHist, "createdAt", base.minusHours(1));
        when(statusHistoryRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(appHist));

        // 4. Financial Plan Audit (12:00)
        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(50L);
        plan.setApplication(app);
        DisbursementPlanAudit planAudit = new DisbursementPlanAudit(
                plan, fieldOfficer, "DISBURSED", "Released installment #1 (₹50,000)");
        setEntityField(planAudit, "occurredAt", base.plusHours(2));
        when(planAuditRepository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(planAudit));

        // 5. Compliance Review (13:00 - latest)
        MilestoneCompliance compliance = new MilestoneCompliance();
        compliance.setId(200L);
        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setTitle("Equipment Setup");
        compliance.setMilestone(milestone);
        ComplianceReviewHistory compHist = new ComplianceReviewHistory();
        compHist.setCompliance(compliance);
        compHist.setAction("APPROVED");
        compHist.setRemarks("Invoice verified");
        setEntityField(compHist, "occurredAt", base.plusHours(3));
        when(complianceReviewHistoryRepository.findAllByOrderByOccurredAtDesc()).thenReturn(List.of(compHist));

        // Retrieve unified stream
        List<AuditLogResponse> unified = auditService.getUnifiedAuditLogs();
        assertNotNull(unified);
        assertEquals(5, unified.size());

        // Chronological order verification: descending by timestamp
        // Index 0: Compliance (13:00)
        assertEquals("COMPLIANCE", unified.get(0).category());
        assertEquals("APPROVED", unified.get(0).action());

        // Index 1: Financial (12:00)
        assertEquals("FINANCIAL", unified.get(1).category());
        assertEquals("DISBURSED", unified.get(1).action());

        // Index 2: Verification (11:00)
        assertEquals("VERIFICATION", unified.get(2).category());
        assertEquals("VERIFIED", unified.get(2).action());

        // Index 3: Admin Management (10:00)
        assertEquals("ADMIN_MANAGEMENT", unified.get(3).category());
        assertEquals("USER_CREATED", unified.get(3).action());

        // Index 4: Application Lifecycle (09:00)
        assertEquals("APPLICATION", unified.get(4).category());
        assertEquals("SUBMITTED", unified.get(4).action());

        // Data protection verification: ensure no sensitive data is in any response DTO
        for (AuditLogResponse log : unified) {
            assertNotNull(log.id());
            assertNotNull(log.category());
            assertNotNull(log.action());
            assertNotNull(log.timestamp());
            // No password fields exist in the DTO
            assertFalse(log.toString().contains("password"));
            assertFalse(log.toString().contains("JWT"));
        }
    }

    private void setEntityField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
