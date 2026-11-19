package com.grantora.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.Role;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import com.grantora.entity.User;
import com.grantora.repository.BeneficiaryRepository;
import com.grantora.repository.SchemeRepository;
import com.grantora.service.EligibilityScoringService;
import com.grantora.service.SchemeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemeControllerEligibilityTest {

    static class TrackingSchemeService extends SchemeService {
        Long lastSchemeId;
        Long lastBeneficiaryId;
        String lastUserEmail;
        List<EligibilityAnswer> lastAnswers;

        public TrackingSchemeService(
                SchemeRepository schemeRepository,
                BeneficiaryRepository beneficiaryRepository,
                EligibilityScoringService eligibilityScoringService) {
            super(schemeRepository, beneficiaryRepository, eligibilityScoringService);
        }

        @Override
        public EligibilityEvaluationResult evaluateEligibility(
                Long schemeId,
                Long beneficiaryId,
                String userEmail,
                List<EligibilityAnswer> answers) {
            this.lastSchemeId = schemeId;
            this.lastBeneficiaryId = beneficiaryId;
            this.lastUserEmail = userEmail;
            this.lastAnswers = answers;
            return super.evaluateEligibility(schemeId, beneficiaryId, userEmail, answers);
        }
    }

    private SchemeController controller;
    private TrackingSchemeService schemeService;
    private Scheme activeScheme;
    private Scheme draftScheme;
    private Beneficiary beneficiary;
    private Beneficiary otherBeneficiary;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        activeScheme = new Scheme();
        Field id1 = Scheme.class.getDeclaredField("id");
        id1.setAccessible(true);
        id1.set(activeScheme, 1L);
        activeScheme.setSchemeName("Active Scheme");
        activeScheme.setStatus("ACTIVE");
        activeScheme.setMinimumAge(18);
        activeScheme.setMaximumAge(60);
        activeScheme.setMaximumAnnualIncome(new BigDecimal("500000.00"));
        activeScheme.setEligibleCategories(Set.of("GENERAL"));
        activeScheme.setEligibilityThreshold(new BigDecimal("70.00"));

        SchemeEligibilityQuestion q1 = new SchemeEligibilityQuestion();
        Field qId = SchemeEligibilityQuestion.class.getDeclaredField("id");
        qId.setAccessible(true);
        qId.set(q1, 10L);
        q1.setQuestionText("Are you an Indian citizen?");
        q1.setQuestionType("YES_NO");
        q1.setRequired(true);
        q1.setMaximumPoints(new BigDecimal("100.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        activeScheme.setEligibilityQuestions(List.of(q1));

        draftScheme = new Scheme();
        Field id2 = Scheme.class.getDeclaredField("id");
        id2.setAccessible(true);
        id2.set(draftScheme, 2L);
        draftScheme.setSchemeName("Draft Scheme");
        draftScheme.setStatus("DRAFT");

        User user = new User();
        user.setEmail("beneficiary@example.test");
        user.setRole(Role.BENEFICIARY);

        beneficiary = new Beneficiary();
        Field benId = Beneficiary.class.getDeclaredField("id");
        benId.setAccessible(true);
        benId.set(beneficiary, 1L);
        beneficiary.setUser(user);
        beneficiary.setDateOfBirth(LocalDate.of(1995, 1, 1));
        beneficiary.setAnnualIncome(new BigDecimal("200000.00"));
        beneficiary.setCategory("GENERAL");

        User otherUser = new User();
        otherUser.setEmail("other@example.test");
        otherUser.setRole(Role.BENEFICIARY);

        otherBeneficiary = new Beneficiary();
        benId.set(otherBeneficiary, 2L);
        otherBeneficiary.setUser(otherUser);
        otherBeneficiary.setDateOfBirth(LocalDate.of(1995, 1, 1));
        otherBeneficiary.setAnnualIncome(new BigDecimal("9000000.00"));
        otherBeneficiary.setCategory("EXCLUDED");

        SchemeRepository schemeRepository = (SchemeRepository) Proxy.newProxyInstance(
                SchemeRepository.class.getClassLoader(),
                new Class<?>[]{SchemeRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        Long id = (Long) args[0];
                        if (id.equals(1L)) return Optional.of(activeScheme);
                        if (id.equals(2L)) return Optional.of(draftScheme);
                        return Optional.empty();
                    }
                    return null;
                });

        BeneficiaryRepository beneficiaryRepository = (BeneficiaryRepository) Proxy.newProxyInstance(
                BeneficiaryRepository.class.getClassLoader(),
                new Class<?>[]{BeneficiaryRepository.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("findById".equals(methodName)) {
                        Long id = (Long) args[0];
                        if (Long.valueOf(1L).equals(id)) return Optional.of(beneficiary);
                        if (Long.valueOf(2L).equals(id)) return Optional.of(otherBeneficiary);
                        return Optional.empty();
                    }
                    if ("findByUser_Email".equals(methodName)) {
                        String email = (String) args[0];
                        if ("beneficiary@example.test".equals(email)) return Optional.of(beneficiary);
                        if ("other@example.test".equals(email)) return Optional.of(otherBeneficiary);
                        return Optional.empty();
                    }
                    return null;
                });

        schemeService = new TrackingSchemeService(
                schemeRepository, beneficiaryRepository, new EligibilityScoringService());
        controller = new SchemeController(schemeService);
    }

    @Test
    void checkEligibility_asBeneficiaryWithArrayPayload_returnsEligible() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("[{\"questionId\": 10, \"answer\": \"YES\"}]");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ELIGIBLE", response.getBody().eligibilityStatus());
        assertNull(response.getBody().applicationId());
        assertEquals(new BigDecimal("100.00"), response.getBody().scoreOutOf100());
        assertNull(schemeService.lastBeneficiaryId);
        assertEquals("beneficiary@example.test", schemeService.lastUserEmail);
    }

    @Test
    void checkEligibility_asBeneficiaryWithObjectPayload_returnsEligible() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("{\"schemeId\": 1, \"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ELIGIBLE", response.getBody().eligibilityStatus());
        assertNull(schemeService.lastBeneficiaryId);
        assertEquals("beneficiary@example.test", schemeService.lastUserEmail);
    }

    @Test
    void checkEligibility_onDraftSchemeByNonAdmin_throwsNotFound() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("[]");

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> controller.checkEligibility(2L, body, auth));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void checkEligibility_onDraftSchemeByAdmin_isAllowed() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var body = mapper.readTree("[]");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(2L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void checkEligibility_beneficiarySupplyingAnotherBeneficiaryId_cannotSelectThatProfile() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("{\"beneficiaryId\": 2, \"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(schemeService.lastBeneficiaryId,
                "Service must NOT receive the supplied beneficiaryId from a non-admin caller");
        assertEquals("beneficiary@example.test", schemeService.lastUserEmail,
                "Service must receive the authenticated caller email");
        assertEquals("ELIGIBLE", response.getBody().eligibilityStatus(),
                "Evaluation must be for the authenticated beneficiary (eligible), not beneficiary #2 (ineligible)");
    }

    @Test
    void checkEligibility_beneficiarySupplyingOwnId_stillResolvesThroughAuthenticatedIdentity() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("{\"beneficiaryId\": 1, \"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(schemeService.lastBeneficiaryId,
                "Non-admin beneficiaryId in payload must be ignored regardless of value");
        assertEquals("beneficiary@example.test", schemeService.lastUserEmail);
        assertEquals("ELIGIBLE", response.getBody().eligibilityStatus());
    }

    @Test
    void checkEligibility_beneficiaryOmittingId_resolvesOwnProfile() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "beneficiary@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_BENEFICIARY")));
        var body = mapper.readTree("{\"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(schemeService.lastBeneficiaryId);
        assertEquals("beneficiary@example.test", schemeService.lastUserEmail);
        assertEquals("ELIGIBLE", response.getBody().eligibilityStatus());
    }

    @Test
    void checkEligibility_adminSupplyingBeneficiaryId_canSelectBeneficiaryProfile() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var body = mapper.readTree("{\"beneficiaryId\": 2, \"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2L, schemeService.lastBeneficiaryId,
                "Admin must be permitted to supply a beneficiaryId for evaluation");
        assertEquals("admin@example.test", schemeService.lastUserEmail);
        assertEquals("INELIGIBLE", response.getBody().eligibilityStatus(),
                "Should evaluate beneficiary #2 whose income and category fail scheme criteria");
    }

    @Test
    void checkEligibility_officerSupplyingBeneficiaryId_cannotSelectArbitraryBeneficiaryProfile() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                "officer@example.test", null, List.of(new SimpleGrantedAuthority("ROLE_OFFICER")));
        var body = mapper.readTree("{\"beneficiaryId\": 2, \"answers\": [{\"questionId\": 10, \"answer\": \"YES\"}]}");

        ResponseEntity<EligibilityEvaluationResult> response = controller.checkEligibility(1L, body, auth);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(schemeService.lastBeneficiaryId,
                "Officer cannot select arbitrary beneficiary IDs through this endpoint");
        assertEquals("officer@example.test", schemeService.lastUserEmail);
        assertEquals("NEEDS_MORE_INFORMATION", response.getBody().eligibilityStatus(),
                "Should resolve officer identity (no beneficiary record) rather than beneficiary #2");
    }

    @Test
    void checkEligibility_bothEndpointAliases_useSameProtectedControllerMethod() throws NoSuchMethodException {
        Method method = SchemeController.class.getMethod(
                "checkEligibility", Long.class, JsonNode.class, Authentication.class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertNotNull(postMapping, "checkEligibility must be annotated with @PostMapping");
        List<String> paths = List.of(postMapping.value());
        assertTrue(paths.contains("/{id}/check-eligibility"),
                "Mapping must include /{id}/check-eligibility");
        assertTrue(paths.contains("/{id}/evaluate-eligibility"),
                "Mapping must include /{id}/evaluate-eligibility");
    }
}
