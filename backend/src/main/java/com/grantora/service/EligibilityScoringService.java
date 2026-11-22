
package com.grantora.service;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EligibilityScoringService {

    public EligibilityEvaluationResult evaluate(
            Long applicationId,
            Scheme scheme,
            Beneficiary beneficiary,
            List<EligibilityAnswer> answers) {
        return evaluate(applicationId, scheme, beneficiary, answers, LocalDate.now());
    }

    EligibilityEvaluationResult evaluate(
            Long applicationId,
            Scheme scheme,
            Beneficiary beneficiary,
            List<EligibilityAnswer> answers,
            LocalDate today) {

        LocalDate evaluationDate = today != null ? today : LocalDate.now();

        if (scheme == null) {
            List<String> missing = List.of("Scheme configuration is missing.");

            return new EligibilityEvaluationResult(
                    applicationId,
                    null,
                    "NEEDS_MORE_INFORMATION",
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of(),
                    List.of(),
                    List.of(),
                    missing,
                    "More information is required before eligibility can be determined.",
                    null,
                    LocalDateTime.now());
        }

        List<EligibilityEvaluationResult.MandatoryCriterionResult> criteria = new ArrayList<>();

        List<EligibilityEvaluationResult.QuestionResult> questionResults = new ArrayList<>();

        List<String> failedCriteria = new ArrayList<>();
        List<String> missingInformation = new ArrayList<>();

        evaluateAge(
                scheme,
                beneficiary,
                evaluationDate,
                criteria,
                failedCriteria,
                missingInformation);

        evaluateIncome(
                scheme,
                beneficiary,
                criteria,
                failedCriteria,
                missingInformation);

        evaluateCategory(
                scheme,
                beneficiary,
                criteria,
                failedCriteria,
                missingInformation);

        Map<Long, EligibilityAnswer> answersByQuestion = new HashMap<>();

        if (answers != null) {
            for (EligibilityAnswer answer : answers) {
                if (answer != null && answer.questionId() != null) {
                    answersByQuestion.put(answer.questionId(), answer);
                }
            }
        }

        BigDecimal pointsEarned = BigDecimal.ZERO;
        BigDecimal totalSchemeMaxPoints = BigDecimal.ZERO;

        List<SchemeEligibilityQuestion> questions = scheme.getEligibilityQuestions() == null
                ? List.of()
                : scheme.getEligibilityQuestions();

        for (SchemeEligibilityQuestion question : questions) {
            if (question == null) {
                continue;
            }

            BigDecimal questionMaximum = resolveQuestionMaximum(question);

            totalSchemeMaxPoints = totalSchemeMaxPoints.add(questionMaximum);

            EligibilityAnswer answer = question.getId() != null
                    ? answersByQuestion.get(question.getId())
                    : null;

            String rawAnswer = answer != null && answer.answer() != null
                    ? answer.answer().trim()
                    : "";

            if (rawAnswer.isEmpty()) {

                if (question.isRequired()) {
                    missingInformation.add(
                            "Answer required: " + question.getQuestionText());

                    questionResults.add(
                            new EligibilityEvaluationResult.QuestionResult(
                                    question.getId(),
                                    question.getQuestionText(),
                                    null,
                                    null,
                                    BigDecimal.ZERO,
                                    questionMaximum,
                                    false,
                                    true,
                                    "A response is required for this question."));
                } else {
                    questionResults.add(
                            new EligibilityEvaluationResult.QuestionResult(
                                    question.getId(),
                                    question.getQuestionText(),
                                    null,
                                    null,
                                    BigDecimal.ZERO,
                                    questionMaximum,
                                    false,
                                    false,
                                    "This optional question was not answered."));
                }

                continue;
            }

            SchemeEligibilityQuestion.AnswerOption selected = findMatchingOption(question, rawAnswer);

            if (selected == null) {
                missingInformation.add(
                        "Invalid answer for question '"
                                + question.getQuestionText()
                                + "': "
                                + rawAnswer);

                questionResults.add(
                        new EligibilityEvaluationResult.QuestionResult(
                                question.getId(),
                                question.getQuestionText(),
                                "Invalid (" + rawAnswer + ")",
                                rawAnswer,
                                BigDecimal.ZERO,
                                questionMaximum,
                                false,
                                question.isRequired(),
                                "Invalid answer value provided: The supplied answer does not match any configured option."));

                continue;
            }

            BigDecimal optionPoints = selected.points() != null
                    ? selected.points()
                    : BigDecimal.ZERO;

            pointsEarned = pointsEarned.add(optionPoints);

            boolean meetsCondition = selected.meetsEligibilityCondition();

            String explanation = meetsCondition
                    ? "The answer meets this configured condition."
                    : "The answer does not meet this configured condition.";

            questionResults.add(
                    new EligibilityEvaluationResult.QuestionResult(
                            question.getId(),
                            question.getQuestionText(),
                            selected.label(),
                            selected.value(),
                            optionPoints,
                            questionMaximum,
                            meetsCondition,
                            question.isRequired(),
                            explanation));
        }

        if (questions.isEmpty()
                || totalSchemeMaxPoints.compareTo(
                        BigDecimal.valueOf(100)) != 0) {

            missingInformation.add(
                    "This scheme does not have a complete 100-point eligibility configuration.");
        }

        BigDecimal score;

        if (totalSchemeMaxPoints.signum() == 0) {
            score = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            score = pointsEarned
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            totalSchemeMaxPoints,
                            2,
                            RoundingMode.HALF_UP);

            if (score.compareTo(BigDecimal.valueOf(100)) > 0) {
                score = BigDecimal.valueOf(100)
                        .setScale(2, RoundingMode.HALF_UP);
            }

            if (score.signum() < 0) {
                score = BigDecimal.ZERO
                        .setScale(2, RoundingMode.HALF_UP);
            }
        }

        String status;

        if (!failedCriteria.isEmpty()) {
            status = "INELIGIBLE";
        } else if (!missingInformation.isEmpty()) {
            status = "NEEDS_MORE_INFORMATION";
        } else if (scheme.getEligibilityThreshold() == null) {
            status = "NEEDS_MORE_INFORMATION";
            missingInformation.add(
                    "The scheme eligibility score threshold is not configured.");
        } else {
            status = score.compareTo(
                    scheme.getEligibilityThreshold()) >= 0
                            ? "ELIGIBLE"
                            : "INELIGIBLE";
        }

        String explanation = switch (status) {
            case "ELIGIBLE" ->
                "All mandatory criteria passed and the custom eligibility score meets the scheme threshold.";

            case "INELIGIBLE" ->
                failedCriteria.isEmpty()
                        ? "The custom eligibility score is below the scheme threshold."
                        : "One or more mandatory eligibility criteria were not met.";

            case "NEEDS_MORE_INFORMATION" ->
                "More information is required before eligibility can be determined.";

            default ->
                "Eligibility evaluation complete.";
        };

        return new EligibilityEvaluationResult(
                applicationId,
                scheme.getId(),
                status,
                score,
                pointsEarned,
                totalSchemeMaxPoints,
                criteria,
                questionResults,
                failedCriteria,
                missingInformation,
                explanation,
                scheme.getEligibilityThreshold(),
                LocalDateTime.now());
    }

    private BigDecimal resolveQuestionMaximum(
            SchemeEligibilityQuestion question) {

        if (question == null) {
            return BigDecimal.ZERO;
        }

        if (question.getMaximumPoints() != null
                && question.getMaximumPoints().signum() >= 0) {
            return question.getMaximumPoints();
        }

        BigDecimal maximum = BigDecimal.ZERO;

        List<SchemeEligibilityQuestion.AnswerOption> options = question.getAnswerOptions();

        if (options != null) {
            for (SchemeEligibilityQuestion.AnswerOption option : options) {
                if (option != null
                        && option.points() != null
                        && option.points().signum() >= 0
                        && option.points().compareTo(maximum) > 0) {

                    maximum = option.points();
                }
            }
        }

        return maximum;
    }

    private SchemeEligibilityQuestion.AnswerOption findMatchingOption(
            SchemeEligibilityQuestion question,
            String answer) {

        if (question == null
                || question.getAnswerOptions() == null
                || answer == null
                || answer.isEmpty()) {
            return null;
        }

        for (SchemeEligibilityQuestion.AnswerOption option : question.getAnswerOptions()) {

            if (option == null) {
                continue;
            }

            if (option.value() != null
                    && option.value().trim().equalsIgnoreCase(answer)) {
                return option;
            }

            if (option.label() != null
                    && option.label().trim().equalsIgnoreCase(answer)) {
                return option;
            }
        }

        return null;
    }

    private void evaluateAge(
            Scheme scheme,
            Beneficiary beneficiary,
            LocalDate today,
            List<EligibilityEvaluationResult.MandatoryCriterionResult> results,
            List<String> failed,
            List<String> missing) {

        Integer minimum = scheme.getMinimumAge();
        Integer maximum = scheme.getMaximumAge();

        if (minimum == null || maximum == null) {
            addMissingCriterion(
                    "Age",
                    "The scheme age range is not configured.",
                    results,
                    missing);
            return;
        }

        if (minimum < 0 || maximum < 0 || minimum > maximum) {
            addMissingCriterion(
                    "Age",
                    "The scheme age range configuration is invalid.",
                    results,
                    missing);
            return;
        }

        String expected = minimum + " to " + maximum;

        if (beneficiary == null
                || beneficiary.getDateOfBirth() == null) {
            addMissingCriterion(
                    "Age",
                    "Date of birth is missing from the beneficiary profile.",
                    results,
                    missing);
            return;
        }

        LocalDate dateOfBirth = beneficiary.getDateOfBirth();

        if (dateOfBirth.isAfter(today)) {
            addFailedCriterion(
                    "Age",
                    "Date of birth cannot be in the future.",
                    expected,
                    dateOfBirth.toString(),
                    results,
                    failed);
            return;
        }

        int age = Period.between(dateOfBirth, today).getYears();

        boolean passed = age >= minimum && age <= maximum;

        String explanation;

        if (passed) {
            explanation = "Age is within the scheme range.";
        } else if (age < minimum) {
            explanation = "Age is below the minimum scheme age requirement ("
                    + minimum + ").";
        } else {
            explanation = "Age exceeds the maximum scheme age requirement ("
                    + maximum + ").";
        }

        results.add(
                new EligibilityEvaluationResult.MandatoryCriterionResult(
                        "Age",
                        passed ? "PASS" : "FAIL",
                        expected,
                        Integer.toString(age),
                        explanation));

        if (!passed) {
            failed.add("Age: " + explanation);
        }
    }

    private void evaluateIncome(
            Scheme scheme,
            Beneficiary beneficiary,
            List<EligibilityEvaluationResult.MandatoryCriterionResult> results,
            List<String> failed,
            List<String> missing) {

        BigDecimal maximum = scheme.getMaximumAnnualIncome();

        if (maximum == null) {
            addMissingCriterion(
                    "Annual income",
                    "The scheme maximum annual income is not configured.",
                    results,
                    missing);
            return;
        }

        if (beneficiary == null
                || beneficiary.getAnnualIncome() == null) {
            addMissingCriterion(
                    "Annual income",
                    "Annual income is missing from the beneficiary profile.",
                    results,
                    missing);
            return;
        }

        BigDecimal income = beneficiary.getAnnualIncome();

        if (income.signum() < 0) {
            addFailedCriterion(
                    "Annual income",
                    "Annual income cannot be negative.",
                    "At most " + maximum,
                    income.toPlainString(),
                    results,
                    failed);
            return;
        }

        boolean passed = income.compareTo(maximum) <= 0;

        String explanation = passed
                ? "Annual income is within the scheme limit."
                : "Annual income exceeds the scheme limit.";

        results.add(
                new EligibilityEvaluationResult.MandatoryCriterionResult(
                        "Annual income",
                        passed ? "PASS" : "FAIL",
                        "At most " + maximum,
                        income.toPlainString(),
                        explanation));

        if (!passed) {
            failed.add("Annual income: " + explanation);
        }
    }

    private void evaluateCategory(
            Scheme scheme,
            Beneficiary beneficiary,
            List<EligibilityEvaluationResult.MandatoryCriterionResult> results,
            List<String> failed,
            List<String> missing) {

        if (scheme.getEligibleCategories() == null
                || scheme.getEligibleCategories().isEmpty()) {
            addMissingCriterion(
                    "Beneficiary category",
                    "The scheme eligible categories are not configured.",
                    results,
                    missing);
            return;
        }

        if (beneficiary == null
                || beneficiary.getCategory() == null
                || beneficiary.getCategory().isBlank()) {
            addMissingCriterion(
                    "Beneficiary category",
                    "Beneficiary category is missing from the profile.",
                    results,
                    missing);
            return;
        }

        String category = beneficiary.getCategory().trim();

        boolean passed = scheme.getEligibleCategories().stream()
                .anyMatch(value -> value != null
                        && value.trim().equalsIgnoreCase(category));

        String explanation = passed
                ? "Beneficiary category is eligible for this scheme."
                : "Beneficiary category is not included in this scheme.";

        results.add(
                new EligibilityEvaluationResult.MandatoryCriterionResult(
                        "Beneficiary category",
                        passed ? "PASS" : "FAIL",
                        String.join(", ", scheme.getEligibleCategories()),
                        category,
                        explanation));

        if (!passed) {
            failed.add("Beneficiary category: " + explanation);
        }
    }

    private void addMissingCriterion(
            String criterion,
            String explanation,
            List<EligibilityEvaluationResult.MandatoryCriterionResult> results,
            List<String> missing) {

        results.add(
                new EligibilityEvaluationResult.MandatoryCriterionResult(
                        criterion,
                        "MISSING",
                        null,
                        null,
                        explanation));

        missing.add(criterion + ": " + explanation);
    }

    private void addFailedCriterion(
            String criterion,
            String explanation,
            String expected,
            String actual,
            List<EligibilityEvaluationResult.MandatoryCriterionResult> results,
            List<String> failed) {

        results.add(
                new EligibilityEvaluationResult.MandatoryCriterionResult(
                        criterion,
                        "FAIL",
                        expected,
                        actual,
                        explanation));

        failed.add(criterion + ": " + explanation);
    }
}
