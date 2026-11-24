package com.grantora.service;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.Role;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import com.grantora.entity.User;
import com.grantora.repository.BeneficiaryRepository;
import com.grantora.repository.SchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SchemeEligibilityEvaluationTest {

    private SchemeService schemeService;
    private Scheme scheme;
    private Beneficiary beneficiary;
    private User user;
    private SchemeEligibilityQuestion q1;

    @BeforeEach
    void setUp() throws Exception {
        scheme = new Scheme();
        Field schemeId = Scheme.class.getDeclaredField("id");
        schemeId.setAccessible(true);
        schemeId.set(scheme, 1L);

        scheme.setSchemeName("Solar Subsidy 2026");
        scheme.setStatus("ACTIVE");
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(60);
        scheme.setMaximumAnnualIncome(new BigDecimal("500000.00"));
        scheme.setEligibleCategories(Set.of("GENERAL", "OBC", "SC", "ST"));
        scheme.setEligibilityThreshold(new BigDecimal("70.00"));

        q1 = new SchemeEligibilityQuestion();
        Field qId = SchemeEligibilityQuestion.class.getDeclaredField("id");
        qId.setAccessible(true);
        qId.set(q1, 101L);

        q1.setQuestionText("Owns agricultural land?");
        q1.setQuestionType("YES_NO");
        q1.setRequired(true);
        q1.setMaximumPoints(new BigDecimal("100.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        scheme.setEligibilityQuestions(List.of(q1));

        user = new User();
        user.setEmail("farmer@example.test");
        user.setFullName("Ramesh Kumar");
        user.setRole(Role.BENEFICIARY);

        beneficiary = new Beneficiary();
        beneficiary.setUser(user);
        beneficiary.setDateOfBirth(LocalDate.of(1990, 5, 15));
        beneficiary.setAnnualIncome(new BigDecimal("300000.00"));
        beneficiary.setCategory("GENERAL");

        SchemeRepository schemeRepository = (SchemeRepository) Proxy.newProxyInstance(
                SchemeRepository.class.getClassLoader(),
                new Class<?>[]{SchemeRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        Long id = (Long) args[0];
                        return id.equals(1L) ? Optional.of(scheme) : Optional.empty();
                    }
                    return null;
                });

        BeneficiaryRepository beneficiaryRepository = (BeneficiaryRepository) Proxy.newProxyInstance(
                BeneficiaryRepository.class.getClassLoader(),
                new Class<?>[]{BeneficiaryRepository.class},
                (proxy, method, args) -> {
                    if ("findByUser_Email".equals(method.getName())) {
                        String email = (String) args[0];
                        return "farmer@example.test".equals(email) ? Optional.of(beneficiary) : Optional.empty();
                    }
                    return null;
                });

        EligibilityScoringService eligibilityScoringService = new EligibilityScoringService();
        schemeService = new SchemeService(schemeRepository, beneficiaryRepository, eligibilityScoringService);
    }

    @Test
    void evaluateEligibility_withEligibleBeneficiaryAndAnswers_returnsEligibleResult() {
        List<EligibilityAnswer> answers = List.of(new EligibilityAnswer(101L, "YES"));

        EligibilityEvaluationResult result = schemeService.evaluateEligibility(
                1L, null, "farmer@example.test", answers);

        assertNotNull(result);
        assertEquals("ELIGIBLE", result.eligibilityStatus(), "Result explanation: " + result.explanation() + ", missing: " + result.missingInformation() + ", failed: " + result.failedCriteria());
        assertNull(result.applicationId(), "Pre-application evaluation must not have an application ID");
        assertEquals(new BigDecimal("100.00"), result.scoreOutOf100());
        assertTrue(result.failedCriteria().isEmpty());
        assertTrue(result.missingInformation().isEmpty());
    }

    @Test
    void evaluateEligibility_withIneligibleAnswer_returnsIneligibleResult() {
        List<EligibilityAnswer> answers = List.of(new EligibilityAnswer(101L, "NO"));

        EligibilityEvaluationResult result = schemeService.evaluateEligibility(
                1L, null, "farmer@example.test", answers);

        assertNotNull(result);
        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertNull(result.applicationId());
        assertEquals(new BigDecimal("0.00"), result.scoreOutOf100());
    }

    @Test
    void evaluateEligibility_whenBeneficiaryProfileMissing_reportsNeedsMoreInformation() {
        EligibilityEvaluationResult result = schemeService.evaluateEligibility(
                1L, null, "unknown@example.test", List.of());

        assertNotNull(result);
        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertFalse(result.missingInformation().isEmpty());
    }
}
