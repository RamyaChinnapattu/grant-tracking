package com.grantora.service;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.AnswerOptionListConverter;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.EligibilityAnswerListConverter;
import com.grantora.entity.EligibilityEvaluationResultConverter;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityScoringServiceTest {

    private final EligibilityScoringService scoringService =
            new EligibilityScoringService();
    private final LocalDate today = LocalDate.of(2026, 1, 1);

    @Test
    @DisplayName("Normalizes configured points, applies threshold, and marks eligible")
    void normalizesConfiguredPointsAndUsesThreshold() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"),
                answer(5, "NO"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                42L, scheme, beneficiary, answers, today);

        assertEquals("ELIGIBLE", result.eligibilityStatus());
        assertEquals(new BigDecimal("80.00"), result.scoreOutOf100());
        assertEquals(new BigDecimal("80"), result.pointsEarned());
        assertEquals(new BigDecimal("100"), result.maximumAvailablePoints());
        assertEquals(new BigDecimal("80"), result.eligibilityThreshold());
        assertEquals(42L, result.applicationId());
        assertEquals(5, result.customQuestionResults().size());
        assertTrue(result.failedCriteria().isEmpty());
        assertTrue(result.missingInformation().isEmpty());
    }

    @Test
    @DisplayName("Mandatory condition failure overrides a high numerical score")
    void mandatoryFailureOverridesAHighCustomScore() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setCategory("UNLISTED");
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"),
                answer(5, "YES"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                7L, scheme, beneficiary, answers, today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(new BigDecimal("100.00"), result.scoreOutOf100());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Beneficiary category"));
        assertEquals("One or more mandatory eligibility criteria were not met.", result.explanation());
    }

    @Test
    @DisplayName("Score below the configured threshold is ineligible")
    void scoreBelowTheConfiguredThresholdIsIneligible() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "NO"),
                answer(5, "NO"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                8L, scheme, beneficiary, answers, today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(new BigDecimal("60.00"), result.scoreOutOf100());
        assertEquals(0, result.failedCriteria().size());
        assertEquals("The custom eligibility score is below the scheme threshold.", result.explanation());
    }

    @Test
    @DisplayName("Missing required information produces NEEDS_MORE_INFORMATION")
    void missingRequiredInformationDoesNotProduceEligibility() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setDateOfBirth(null);
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                9L, scheme, beneficiary, answers, today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertEquals(2, result.missingInformation().size());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("Date of birth is missing")));
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("Answer required: Question 5")));
    }

    @Test
    @DisplayName("All mandatory conditions passing independently")
    void allMandatoryConditionsPassing_andScoreAboveThreshold_isEligible() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"),
                answer(5, "YES"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                10L, scheme, beneficiary, answers, today);

        assertEquals("ELIGIBLE", result.eligibilityStatus());
        assertEquals(new BigDecimal("100.00"), result.scoreOutOf100());
        assertEquals(3, result.mandatoryCriteriaResults().size());
        assertTrue(result.mandatoryCriteriaResults().stream().allMatch(c -> "PASS".equals(c.status())));
        assertTrue(result.failedCriteria().isEmpty());
        assertTrue(result.missingInformation().isEmpty());
    }

    @Test
    @DisplayName("Age below minimum requirement fails independently")
    void ageFailingBelowMinimum_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme(); // min 18, max 65
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setDateOfBirth(LocalDate.of(2010, 1, 1)); // 16 years old in 2026

        EligibilityEvaluationResult result = scoringService.evaluate(
                11L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Age is below the minimum scheme age requirement (18)"));
    }

    @Test
    @DisplayName("Age above maximum requirement fails independently")
    void ageFailingAboveMaximum_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme(); // min 18, max 65
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setDateOfBirth(LocalDate.of(1955, 1, 1)); // 71 years old in 2026

        EligibilityEvaluationResult result = scoringService.evaluate(
                12L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Age exceeds the maximum scheme age requirement (65)"));
    }

    @Test
    @DisplayName("Date of birth in the future fails independently")
    void futureDateOfBirth_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setDateOfBirth(LocalDate.of(2027, 6, 1)); // future date

        EligibilityEvaluationResult result = scoringService.evaluate(
                13L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Date of birth cannot be in the future"));
    }

    @Test
    @DisplayName("Income exceeding scheme maximum fails independently")
    void annualIncomeExceedingLimit_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme(); // max income 100,000
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setAnnualIncome(new BigDecimal("150000.00"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                14L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Annual income exceeds the scheme limit"));
    }

    @Test
    @DisplayName("Negative annual income fails independently")
    void negativeAnnualIncome_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setAnnualIncome(new BigDecimal("-500.00"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                15L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Annual income cannot be negative"));
    }

    @Test
    @DisplayName("Unlisted category fails independently")
    void unlistedCategory_isMarkedIneligible() throws Exception {
        Scheme scheme = configuredScheme(); // eligible: GENERAL
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setCategory("OTHER");

        EligibilityEvaluationResult result = scoringService.evaluate(
                16L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(1, result.failedCriteria().size());
        assertTrue(result.failedCriteria().get(0).contains("Beneficiary category is not included in this scheme"));
    }

    @Test
    @DisplayName("Multiple mandatory conditions failing are all recorded")
    void multipleMandatoryConditionsFailing_recordsAllFailures() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setDateOfBirth(LocalDate.of(1950, 1, 1)); // age 76 > 65
        beneficiary.setAnnualIncome(new BigDecimal("250000.00")); // income > 100,000
        beneficiary.setCategory("UNLISTED"); // category not GENERAL

        EligibilityEvaluationResult result = scoringService.evaluate(
                17L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("INELIGIBLE", result.eligibilityStatus());
        assertEquals(3, result.failedCriteria().size());
        assertTrue(result.failedCriteria().stream().anyMatch(f -> f.contains("Age")));
        assertTrue(result.failedCriteria().stream().anyMatch(f -> f.contains("Annual income")));
        assertTrue(result.failedCriteria().stream().anyMatch(f -> f.contains("Beneficiary category")));
    }

    @Test
    @DisplayName("Missing beneficiary annual income produces NEEDS_MORE_INFORMATION")
    void missingAnnualIncome_producesNeedsMoreInformation() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setAnnualIncome(null);

        EligibilityEvaluationResult result = scoringService.evaluate(
                18L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("Annual income is missing")));
        assertTrue(result.failedCriteria().isEmpty());
    }

    @Test
    @DisplayName("Missing beneficiary category produces NEEDS_MORE_INFORMATION")
    void missingCategory_producesNeedsMoreInformation() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        beneficiary.setCategory("   ");

        EligibilityEvaluationResult result = scoringService.evaluate(
                19L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("Beneficiary category is missing")));
    }

    @Test
    @DisplayName("Null beneficiary profile produces NEEDS_MORE_INFORMATION for all criteria")
    void nullBeneficiary_producesNeedsMoreInformation() throws Exception {
        Scheme scheme = configuredScheme();

        EligibilityEvaluationResult result = scoringService.evaluate(
                20L, scheme, null, allYesAnswers(), today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertEquals(3, result.missingInformation().size());
    }

    @Test
    @DisplayName("Null scheme returns NEEDS_MORE_INFORMATION without throwing NPE")
    void nullScheme_producesNeedsMoreInformation() {
        Beneficiary beneficiary = completeBeneficiary();

        EligibilityEvaluationResult result = scoringService.evaluate(
                21L, null, beneficiary, List.of(), today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().contains("Scheme configuration is missing."));
    }

    @Test
    @DisplayName("Unconfigured scheme threshold produces NEEDS_MORE_INFORMATION")
    void unconfiguredSchemeThreshold_producesNeedsMoreInformation() throws Exception {
        Scheme scheme = configuredScheme();
        scheme.setEligibilityThreshold(null);
        Beneficiary beneficiary = completeBeneficiary();

        EligibilityEvaluationResult result = scoringService.evaluate(
                22L, scheme, beneficiary, allYesAnswers(), today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("threshold is not configured")));
    }

    @Test
    @DisplayName("Dynamic question answers match case-insensitively and by option label")
    void dynamicQuestions_caseInsensitiveAndLabelMatching() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        List<EligibilityAnswer> answers = List.of(
                answer(1, "yes"), // lowercase
                answer(2, "YES "), // with trailing space
                answer(3, "Yes"), // titlecase / matches label
                answer(4, "YES"),
                answer(5, "no")); // lowercase

        EligibilityEvaluationResult result = scoringService.evaluate(
                23L, scheme, beneficiary, answers, today);

        assertEquals("ELIGIBLE", result.eligibilityStatus());
        assertEquals(new BigDecimal("80.00"), result.scoreOutOf100());
        assertEquals(new BigDecimal("80"), result.pointsEarned());
        // Verify meetsEligibilityCondition on question 1 is true
        assertTrue(result.customQuestionResults().get(0).meetsEligibilityCondition());
        // Verify meetsEligibilityCondition on question 5 ("no") is false
        assertFalse(result.customQuestionResults().get(4).meetsEligibilityCondition());
    }

    @Test
    @DisplayName("Invalid answer option is explicitly recorded and produces NEEDS_MORE_INFORMATION")
    void invalidAnswerOption_producesNeedsMoreInformationWithExplicitDetails() throws Exception {
        Scheme scheme = configuredScheme();
        Beneficiary beneficiary = completeBeneficiary();
        List<EligibilityAnswer> answers = List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"),
                answer(5, "INVALID_VALUE"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                24L, scheme, beneficiary, answers, today);

        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("Invalid answer for question 'Question 5'")));
        EligibilityEvaluationResult.QuestionResult q5Result = result.customQuestionResults().get(4);
        assertEquals("INVALID_VALUE", q5Result.answerValue());
        assertTrue(q5Result.explanation().contains("Invalid answer value provided"));
    }

    @Test
    @DisplayName("Unanswered optional question does not distort denominator or block eligibility")
    void unansweredOptionalQuestion_doesNotDistortDenominator() throws Exception {
        Scheme scheme = new Scheme();
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(65);
        scheme.setMaximumAnnualIncome(new BigDecimal("100000"));
        scheme.setEligibleCategories(Set.of("GENERAL"));
        scheme.setEligibilityThreshold(new BigDecimal("60"));

        // Question 1: required, 60 points
        SchemeEligibilityQuestion q1 = new SchemeEligibilityQuestion();
        setQuestionId(q1, 1L);
        q1.setQuestionText("Required question");
        q1.setQuestionType("YES_NO");
        q1.setRequired(true);
        q1.setMaximumPoints(new BigDecimal("60"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("60"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)));

        // Question 2: optional, 40 points
        SchemeEligibilityQuestion q2 = new SchemeEligibilityQuestion();
        setQuestionId(q2, 2L);
        q2.setQuestionText("Optional question");
        q2.setQuestionType("YES_NO");
        q2.setRequired(false);
        q2.setMaximumPoints(new BigDecimal("40"));
        q2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("40"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)));

        scheme.setEligibilityQuestions(List.of(q1, q2));

        Beneficiary beneficiary = completeBeneficiary();
        // Beneficiary only answers Q1, leaves optional Q2 unanswered
        List<EligibilityAnswer> answers = List.of(answer(1, "YES"));

        EligibilityEvaluationResult result = scoringService.evaluate(
                25L, scheme, beneficiary, answers, today);

        // Denominator must remain 100, pointsEarned = 60, score = 60.00%
        assertEquals(new BigDecimal("100"), result.maximumAvailablePoints());
        assertEquals(new BigDecimal("60"), result.pointsEarned());
        assertEquals(new BigDecimal("60.00"), result.scoreOutOf100());
        // Threshold is 60, score is 60.00, optional question was omitted without missingInformation error
        assertTrue(result.missingInformation().isEmpty());
        assertEquals("ELIGIBLE", result.eligibilityStatus());
    }

    @Test
    @DisplayName("Zero applicable scoring questions prevents division-by-zero and reports incomplete configuration")
    void zeroApplicableScoringQuestions_handlesSafely() {
        Scheme scheme = new Scheme();
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(65);
        scheme.setMaximumAnnualIncome(new BigDecimal("100000"));
        scheme.setEligibleCategories(Set.of("GENERAL"));
        scheme.setEligibilityThreshold(new BigDecimal("50"));
        scheme.setEligibilityQuestions(List.of()); // 0 questions

        Beneficiary beneficiary = completeBeneficiary();

        EligibilityEvaluationResult result = scoringService.evaluate(
                26L, scheme, beneficiary, List.of(), today);

        assertEquals(BigDecimal.ZERO, result.pointsEarned());
        assertEquals(BigDecimal.ZERO, result.maximumAvailablePoints());
        assertEquals(new BigDecimal("0.00"), result.scoreOutOf100());
        assertEquals("NEEDS_MORE_INFORMATION", result.eligibilityStatus());
        assertTrue(result.missingInformation().stream().anyMatch(m -> m.contains("complete 100-point eligibility configuration")));
    }

    @Test
    @DisplayName("Threshold boundary: score exactly at threshold is eligible, below is ineligible")
    void thresholdBoundaryCases() throws Exception {
        Scheme scheme = configuredScheme(); // threshold = 80
        Beneficiary beneficiary = completeBeneficiary();

        // 80.00 points == 80 threshold -> ELIGIBLE
        List<EligibilityAnswer> answers80 = List.of(
                answer(1, "YES"), answer(2, "YES"), answer(3, "YES"), answer(4, "YES"), answer(5, "NO"));
        EligibilityEvaluationResult result80 = scoringService.evaluate(
                27L, scheme, beneficiary, answers80, today);
        assertEquals("ELIGIBLE", result80.eligibilityStatus());
        assertEquals(new BigDecimal("80.00"), result80.scoreOutOf100());

        // 60.00 points < 80 threshold -> INELIGIBLE
        List<EligibilityAnswer> answers60 = List.of(
                answer(1, "YES"), answer(2, "YES"), answer(3, "YES"), answer(4, "NO"), answer(5, "NO"));
        EligibilityEvaluationResult result60 = scoringService.evaluate(
                28L, scheme, beneficiary, answers60, today);
        assertEquals("INELIGIBLE", result60.eligibilityStatus());
        assertEquals(new BigDecimal("60.00"), result60.scoreOutOf100());
    }

    @Test
    @DisplayName("Incomplete evaluation versus genuinely ineligible result")
    void incompleteEvaluationVersusGenuinelyIneligibleResult() throws Exception {
        Scheme scheme = configuredScheme();

        // Scenario 1: Genuinely ineligible (fails age) + has missing question answer
        // Age failure takes precedence -> INELIGIBLE
        Beneficiary ineligibleBeneficiary = completeBeneficiary();
        ineligibleBeneficiary.setDateOfBirth(LocalDate.of(1950, 1, 1)); // 76 years old (fails max 65)

        EligibilityEvaluationResult resultIneligible = scoringService.evaluate(
                29L, scheme, ineligibleBeneficiary, List.of(answer(1, "YES")), today); // questions 2-5 missing

        assertEquals("INELIGIBLE", resultIneligible.eligibilityStatus());
        assertEquals(1, resultIneligible.failedCriteria().size());
        assertTrue(resultIneligible.failedCriteria().get(0).contains("Age"));
        assertEquals("One or more mandatory eligibility criteria were not met.", resultIneligible.explanation());

        // Scenario 2: Passes all mandatory criteria, but missing required answers -> NEEDS_MORE_INFORMATION
        Beneficiary validBeneficiary = completeBeneficiary();
        EligibilityEvaluationResult resultIncomplete = scoringService.evaluate(
                30L, scheme, validBeneficiary, List.of(answer(1, "YES")), today);

        assertEquals("NEEDS_MORE_INFORMATION", resultIncomplete.eligibilityStatus());
        assertEquals(0, resultIncomplete.failedCriteria().size());
        assertEquals(4, resultIncomplete.missingInformation().size());
        assertEquals("More information is required before eligibility can be determined.", resultIncomplete.explanation());
    }

    @Test
    @DisplayName("Persisted eligibility JSON values round-trip through JPA attribute converters")
    void persistedEligibilityJsonValuesRoundTrip() throws Exception {
        Scheme scheme = configuredScheme();
        EligibilityEvaluationResult evaluation = scoringService.evaluate(
                31L,
                scheme,
                completeBeneficiary(),
                List.of(answer(1, "YES")),
                today);

        EligibilityEvaluationResultConverter evaluationConverter =
                new EligibilityEvaluationResultConverter();
        EligibilityAnswerListConverter answerConverter =
                new EligibilityAnswerListConverter();
        AnswerOptionListConverter optionConverter = new AnswerOptionListConverter();
        List<EligibilityAnswer> answers = List.of(answer(1, "YES"));
        List<SchemeEligibilityQuestion.AnswerOption> options =
                scheme.getEligibilityQuestions().get(0).getAnswerOptions();

        assertEquals(evaluation, evaluationConverter.convertToEntityAttribute(
                evaluationConverter.convertToDatabaseColumn(evaluation)));
        assertEquals(answers, answerConverter.convertToEntityAttribute(
                answerConverter.convertToDatabaseColumn(answers)));
        assertEquals(options, optionConverter.convertToEntityAttribute(
                optionConverter.convertToDatabaseColumn(options)));
    }

    private Scheme configuredScheme() throws Exception {
        Scheme scheme = new Scheme();
        scheme.setMinimumAge(18);
        scheme.setMaximumAge(65);
        scheme.setMaximumAnnualIncome(new BigDecimal("100000"));
        scheme.setEligibleCategories(Set.of("GENERAL"));
        scheme.setEligibilityThreshold(new BigDecimal("80"));

        List<SchemeEligibilityQuestion> questions = new ArrayList<>();
        for (long id = 1; id <= 5; id++) {
            SchemeEligibilityQuestion question = new SchemeEligibilityQuestion();
            setQuestionId(question, id);
            question.setQuestionText("Question " + id);
            question.setQuestionType("YES_NO");
            question.setRequired(true);
            question.setMaximumPoints(new BigDecimal("20"));
            question.setAnswerOptions(List.of(
                    new SchemeEligibilityQuestion.AnswerOption(
                            "YES", "Yes", new BigDecimal("20"), true),
                    new SchemeEligibilityQuestion.AnswerOption(
                            "NO", "No", BigDecimal.ZERO, false)));
            questions.add(question);
        }
        scheme.setEligibilityQuestions(questions);
        return scheme;
    }

    private Beneficiary completeBeneficiary() {
        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setDateOfBirth(LocalDate.of(1996, 1, 1));
        beneficiary.setAnnualIncome(new BigDecimal("50000"));
        beneficiary.setCategory("general");
        return beneficiary;
    }

    private List<EligibilityAnswer> allYesAnswers() {
        return List.of(
                answer(1, "YES"),
                answer(2, "YES"),
                answer(3, "YES"),
                answer(4, "YES"),
                answer(5, "YES"));
    }

    private EligibilityAnswer answer(long questionId, String value) {
        return new EligibilityAnswer(questionId, value);
    }

    private void setQuestionId(SchemeEligibilityQuestion question, long id)
            throws Exception {
        Field idField = SchemeEligibilityQuestion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(question, id);
    }
}
