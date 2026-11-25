package com.grantora.service;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.Beneficiary;
import com.grantora.entity.GrantApplication;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import com.grantora.repository.BeneficiaryRepository;
import com.grantora.repository.GrantApplicationRepository;
import com.grantora.repository.SchemeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SchemeService {

    private final SchemeRepository schemeRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final EligibilityScoringService eligibilityScoringService;
    private final GrantApplicationRepository grantApplicationRepository;
    private final AuditService auditService;

    public SchemeService(SchemeRepository schemeRepository) {
        this(schemeRepository, null, null, null, null);
    }

    public SchemeService(
            SchemeRepository schemeRepository,
            BeneficiaryRepository beneficiaryRepository,
            EligibilityScoringService eligibilityScoringService) {
        this(schemeRepository, beneficiaryRepository, eligibilityScoringService, null, null);
    }

    public SchemeService(
            SchemeRepository schemeRepository,
            BeneficiaryRepository beneficiaryRepository,
            EligibilityScoringService eligibilityScoringService,
            GrantApplicationRepository grantApplicationRepository) {
        this(schemeRepository, beneficiaryRepository, eligibilityScoringService, grantApplicationRepository, null);
    }

    @Autowired
    public SchemeService(
            SchemeRepository schemeRepository,
            BeneficiaryRepository beneficiaryRepository,
            EligibilityScoringService eligibilityScoringService,
            GrantApplicationRepository grantApplicationRepository,
            @Autowired(required = false) AuditService auditService) {
        this.schemeRepository = schemeRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.eligibilityScoringService = eligibilityScoringService;
        this.grantApplicationRepository = grantApplicationRepository;
        this.auditService = auditService;
    }

    public List<Scheme> getAllSchemes() {
        return schemeRepository.findAll();
    }

    public List<Scheme> getPublicSchemes() {
        return schemeRepository.findByStatusIgnoreCase("ACTIVE");
    }

    public Scheme getSchemeById(Long id) {
        return schemeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Scheme not found with ID: " + id));
    }

    public Scheme getPublicSchemeById(Long id) {
        Scheme scheme = getSchemeById(id);
        if (!"ACTIVE".equalsIgnoreCase(scheme.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Scheme not found or is not published: " + id);
        }
        return scheme;
    }

    @Transactional
    public Scheme createScheme(Scheme scheme) {
        validateEligibilityConfiguration(scheme);
        if (scheme.getAllocatedFunds() != null && scheme.getAllocatedFunds().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total scheme budget (allocated funds) must be greater than zero.");
        }
        if (scheme.getMaximumGrantAmount() != null && scheme.getMaximumGrantAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum grant amount must be greater than zero.");
        }
        if (scheme.getEligibilityQuestions() != null) {
            for (SchemeEligibilityQuestion question : scheme.getEligibilityQuestions()) {
                if (question != null && question.getId() != null) {
                    invalidConfiguration("New questions cannot specify an existing ID");
                }
            }
        }
        List<SchemeEligibilityQuestion> questions = new ArrayList<>();
        if (scheme.getEligibilityQuestions() != null) {
            for (int index = 0; index < scheme.getEligibilityQuestions().size(); index++) {
                SchemeEligibilityQuestion incoming = scheme.getEligibilityQuestions().get(index);
                SchemeEligibilityQuestion question = new SchemeEligibilityQuestion();
                question.setScheme(scheme);
                question.setDisplayOrder(index);
                copyQuestionData(incoming, question);
                questions.add(question);
            }
        }
        if (scheme.getEligibilityConfigurationVersion() == null || scheme.getEligibilityConfigurationVersion() <= 0) {
            scheme.setEligibilityConfigurationVersion(1);
        }
        scheme.setEligibilityQuestions(questions);
        Scheme saved = schemeRepository.save(scheme);
        if (auditService != null) {
            auditService.recordAudit(
                    "SCHEME_CREATED",
                    "SCHEME",
                    "Scheme",
                    saved.getId(),
                    "Created scheme: " + saved.getSchemeName() + " (Allocated funds: ₹" + saved.getAllocatedFunds() + ")");
        }
        return saved;
    }

    @Transactional
    public Scheme updateScheme(Long id, Scheme updatedScheme) {
        validateEligibilityConfiguration(updatedScheme);
        if (updatedScheme.getAllocatedFunds() != null && updatedScheme.getAllocatedFunds().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total scheme budget (allocated funds) must be greater than zero.");
        }
        if (updatedScheme.getMaximumGrantAmount() != null && updatedScheme.getMaximumGrantAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum grant amount must be greater than zero.");
        }
        Scheme existing = getSchemeById(id);

        existing.setSchemeName(updatedScheme.getSchemeName());
        existing.setDescription(updatedScheme.getDescription());
        existing.setDepartment(updatedScheme.getDepartment());
        existing.setMinimumAge(updatedScheme.getMinimumAge());
        existing.setMaximumAge(updatedScheme.getMaximumAge());
        existing.setMaximumAnnualIncome(updatedScheme.getMaximumAnnualIncome());
        existing.setMaximumGrantAmount(updatedScheme.getMaximumGrantAmount());
        if (updatedScheme.getAllocatedFunds() != null) {
            existing.setAllocatedFunds(updatedScheme.getAllocatedFunds());
        }
        existing.setEligibilityThreshold(updatedScheme.getEligibilityThreshold());
        if (updatedScheme.getEligibilityConfigurationVersion() != null
                && updatedScheme.getEligibilityConfigurationVersion() > 0) {
            existing.setEligibilityConfigurationVersion(updatedScheme.getEligibilityConfigurationVersion());
        } else if (existing.getEligibilityConfigurationVersion() == null
                || existing.getEligibilityConfigurationVersion() <= 0) {
            existing.setEligibilityConfigurationVersion(1);
        }
        existing.setEligibleCategories(updatedScheme.getEligibleCategories());
        reconcileQuestions(existing, updatedScheme.getEligibilityQuestions());
        existing.setApplicationStartDate(updatedScheme.getApplicationStartDate());
        existing.setApplicationEndDate(updatedScheme.getApplicationEndDate());
        existing.setStatus(updatedScheme.getStatus());

        Scheme saved = schemeRepository.save(existing);
        if (auditService != null) {
            auditService.recordAudit(
                    "SCHEME_BUDGET_UPDATED",
                    "SCHEME",
                    "Scheme",
                    saved.getId(),
                    "Updated scheme " + saved.getSchemeName() + " (Allocated funds: ₹" + saved.getAllocatedFunds() + ")");
        }
        return saved;
    }

    private void reconcileQuestions(
            Scheme existingScheme,
            List<SchemeEligibilityQuestion> incomingQuestions) {
        if (incomingQuestions == null) {
            return;
        }
        List<SchemeEligibilityQuestion> incomingCopy = new ArrayList<>(incomingQuestions);
        List<SchemeEligibilityQuestion> currentQuestions = existingScheme.getEligibilityQuestions();
        if (currentQuestions == null) {
            currentQuestions = new ArrayList<>();
            existingScheme.setEligibilityQuestions(currentQuestions);
        }

        Map<Long, SchemeEligibilityQuestion> existingById = currentQuestions.stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(SchemeEligibilityQuestion::getId, Function.identity()));

        Set<Long> seenIncomingIds = new HashSet<>();
        for (SchemeEligibilityQuestion incoming : incomingCopy) {
            if (incoming != null && incoming.getId() != null) {
                if (!existingById.containsKey(incoming.getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Question ID " + incoming.getId() + " does not belong to scheme " + existingScheme.getId());
                }
                if (!seenIncomingIds.add(incoming.getId())) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Duplicate question ID " + incoming.getId() + " in update request");
                }
            }
        }

        // Identify existing questions being removed
        List<SchemeEligibilityQuestion> removedQuestions = currentQuestions.stream()
                .filter(q -> q.getId() != null && !seenIncomingIds.contains(q.getId()))
                .toList();

        if (!removedQuestions.isEmpty() && grantApplicationRepository != null) {
            List<GrantApplication> applications = grantApplicationRepository.findByScheme_Id(existingScheme.getId());
            if (applications != null && !applications.isEmpty()) {
                Set<Long> answeredQuestionIds = applications.stream()
                        .filter(app -> app != null && app.getEligibilityAnswers() != null)
                        .flatMap(app -> app.getEligibilityAnswers().stream())
                        .filter(Objects::nonNull)
                        .map(EligibilityAnswer::questionId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                for (SchemeEligibilityQuestion removed : removedQuestions) {
                    if (answeredQuestionIds.contains(removed.getId())) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Cannot remove question \"" + removed.getQuestionText()
                                        + "\" (ID: " + removed.getId()
                                        + ") because existing grant applications reference it. Deactivate the scheme or keep the question.");
                    }
                }
            }
        }

        // Remove unreferenced questions from existing collection
        currentQuestions.removeIf(q -> q.getId() != null && !seenIncomingIds.contains(q.getId()));

        // Update existing questions in-place and add new questions
        for (int index = 0; index < incomingCopy.size(); index++) {
            SchemeEligibilityQuestion incoming = incomingCopy.get(index);
            if (incoming.getId() != null) {
                SchemeEligibilityQuestion existing = existingById.get(incoming.getId());
                existing.setDisplayOrder(index);
                copyQuestionData(incoming, existing);
                existing.setScheme(existingScheme);
            } else {
                SchemeEligibilityQuestion newQuestion = new SchemeEligibilityQuestion();
                newQuestion.setScheme(existingScheme);
                newQuestion.setDisplayOrder(index);
                copyQuestionData(incoming, newQuestion);
                currentQuestions.add(newQuestion);
            }
        }

        currentQuestions.sort(Comparator.comparingInt(SchemeEligibilityQuestion::getDisplayOrder));
    }

    private void copyQuestionData(
            SchemeEligibilityQuestion source,
            SchemeEligibilityQuestion target) {
        target.setQuestionText(source.getQuestionText().trim());
        target.setQuestionType(
                source.getQuestionType().trim().toUpperCase(Locale.ROOT));
        BigDecimal legacyMaximum = source.getAnswerOptions() == null ? null
                : source.getAnswerOptions().stream()
                        .map(SchemeEligibilityQuestion.AnswerOption::points)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo).orElse(null);
        target.setMaximumPoints(source.getMaximumPoints() == null
                ? legacyMaximum
                : source.getMaximumPoints());
        target.setRequired(source.isRequired());
        target.setAnswerOptions(source.getAnswerOptions().stream()
                .map(option -> new SchemeEligibilityQuestion.AnswerOption(
                        option.value().trim(),
                        option.label().trim(),
                        option.points(),
                        option.meetsEligibilityCondition()))
                .toList());
    }

    private void validateEligibilityConfiguration(Scheme scheme) {
        if (scheme.getSchemeName() == null
                || scheme.getSchemeName().isBlank()
                || scheme.getSchemeName().trim().length() > 200) {
            invalidConfiguration("Scheme name is required and must not exceed 200 characters");
        }

        if (scheme.getStatus() == null
                || !Set.of("DRAFT", "ACTIVE", "INACTIVE")
                        .contains(scheme.getStatus().trim().toUpperCase(Locale.ROOT))) {
            invalidConfiguration("Scheme status must be DRAFT, ACTIVE, or INACTIVE");
        }

        scheme.setSchemeName(scheme.getSchemeName().trim());
        scheme.setStatus(scheme.getStatus().trim().toUpperCase(Locale.ROOT));
        if (scheme.getMinimumAge() == null || scheme.getMinimumAge() < 0
                || scheme.getMaximumAge() == null || scheme.getMaximumAge() < 0
                || scheme.getMaximumAge() < scheme.getMinimumAge()) {
            invalidConfiguration("Minimum and maximum ages must be valid and ordered");
        }
        if (scheme.getMaximumAnnualIncome() == null
                || scheme.getMaximumAnnualIncome().signum() < 0) {
            invalidConfiguration("Maximum annual income must be zero or greater");
        }
        if (scheme.getMaximumGrantAmount() != null
                && scheme.getMaximumGrantAmount().signum() < 0) {
            invalidConfiguration("Maximum grant amount must be zero or greater");
        }
        if (scheme.getApplicationStartDate() != null && scheme.getApplicationEndDate() != null
                && scheme.getApplicationEndDate().isBefore(scheme.getApplicationStartDate())) {
            invalidConfiguration("Application end date must not be earlier than application start date");
        }
        if (scheme.getEligibleCategories() == null
                || scheme.getEligibleCategories().isEmpty()) {
            invalidConfiguration("At least one eligible beneficiary category is required");
        }
        Set<String> normalizedCategories = new LinkedHashSet<>();
        Set<String> categoryKeys = new HashSet<>();
        for (String category : scheme.getEligibleCategories()) {
            if (category == null || category.isBlank() || category.trim().length() > 50) {
                invalidConfiguration("Eligible categories must be non-empty and at most 50 characters");
            }
            String normalized = category.trim();
            if (!categoryKeys.add(normalized.toLowerCase(Locale.ROOT))) {
                invalidConfiguration("Eligible categories must be unique");
            }
            normalizedCategories.add(normalized);
        }
        scheme.setEligibleCategories(normalizedCategories);
        if (scheme.getEligibilityThreshold() == null
                || scheme.getEligibilityThreshold().compareTo(BigDecimal.ZERO) < 0
                || scheme.getEligibilityThreshold().compareTo(BigDecimal.valueOf(100)) > 0
                || scheme.getEligibilityThreshold().stripTrailingZeros().scale() > 2) {
            invalidConfiguration("Eligibility threshold must be between 0 and 100");
        }
        if (scheme.getMaximumAnnualIncome() != null
                && (scheme.getMaximumAnnualIncome().stripTrailingZeros().scale() > 2
                        || scheme.getMaximumAnnualIncome().precision()
                                - scheme.getMaximumAnnualIncome().scale() > 10)) {
            invalidConfiguration("Maximum annual income supports up to two decimal places");
        }
        if (scheme.getEligibilityQuestions() == null
                || scheme.getEligibilityQuestions().isEmpty()) {
            invalidConfiguration("At least one eligibility question is required");
        }

        BigDecimal maximumPoints = BigDecimal.ZERO;
        for (SchemeEligibilityQuestion question : scheme.getEligibilityQuestions()) {
            if (question == null || question.getQuestionText() == null
                    || question.getQuestionText().isBlank()
                    || question.getQuestionText().length() > 500) {
                invalidConfiguration("Each eligibility question needs valid question text");
            }
            String type = question.getQuestionType() == null
                    ? ""
                    : question.getQuestionType().trim().toUpperCase(Locale.ROOT);
            List<SchemeEligibilityQuestion.AnswerOption> options = question.getAnswerOptions();
            if (!type.equals("YES_NO") && !type.equals("SINGLE_CHOICE")
                    && !type.equals("MULTIPLE_CHOICE")) {
                invalidConfiguration("Question type must be YES_NO or SINGLE_CHOICE");
            }
            if (type.equals("MULTIPLE_CHOICE")) {
                invalidConfiguration("Multiple choice scoring is not supported; use single choice");
            }
            if (options == null || options.size() < 2) {
                invalidConfiguration("Each eligibility question needs at least two options");
            }

            Set<String> optionValues = new HashSet<>();
            BigDecimal questionMaximum = BigDecimal.ZERO;
            for (SchemeEligibilityQuestion.AnswerOption option : options) {
                if (option == null || option.value() == null || option.value().isBlank()
                        || option.value().length() > 100
                        || option.label() == null || option.label().isBlank()
                        || option.label().length() > 200
                        || option.points() == null || option.points().signum() < 0
                        || option.points().stripTrailingZeros().scale() > 2
                        || option.points().precision() - option.points().scale() > 10) {
                    invalidConfiguration(
                            "Answer options need a value, label, and non-negative points");
                }
                if (!optionValues.add(option.value().trim().toLowerCase(Locale.ROOT))) {
                    invalidConfiguration("Answer option values must be unique per question");
                }
                questionMaximum = questionMaximum.max(option.points());
            }
            if (type.equals("YES_NO")
                    && (options.size() != 2
                            || !optionValues.contains("yes")
                            || !optionValues.contains("no"))) {
                invalidConfiguration("YES_NO questions must have YES and NO answer values");
            }
            BigDecimal configuredMaximum = question.getMaximumPoints() == null
                    ? questionMaximum
                    : question.getMaximumPoints();
            if (configuredMaximum == null || configuredMaximum.signum() <= 0
                    || configuredMaximum.stripTrailingZeros().scale() > 2
                    || configuredMaximum.precision() - configuredMaximum.scale() > 3
                    || questionMaximum.compareTo(configuredMaximum) > 0) {
                invalidConfiguration(
                        "Each question needs a valid maximum weight at least as large as its highest option score");
            }
            maximumPoints = maximumPoints.add(configuredMaximum);
        }
        if (maximumPoints.compareTo(BigDecimal.valueOf(100)) != 0) {
            invalidConfiguration("Question maximum weights must total exactly 100 points");
        }
    }

    private void invalidConfiguration(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    @Transactional
    public void deleteScheme(Long id) {
        Scheme scheme = getSchemeById(id);
        if (grantApplicationRepository != null && grantApplicationRepository.existsByScheme_Id(id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete scheme \"" + scheme.getSchemeName()
                            + "\" because grant applications are associated with it. Deactivate the scheme instead.");
        }
        schemeRepository.delete(scheme);
    }

    public EligibilityEvaluationResult evaluateEligibility(
            Long schemeId,
            Long beneficiaryId,
            String userEmail,
            List<EligibilityAnswer> answers) {
        Scheme scheme = getSchemeById(schemeId);
        Beneficiary beneficiary = null;
        if (beneficiaryId != null && beneficiaryRepository != null) {
            beneficiary = beneficiaryRepository.findById(beneficiaryId).orElse(null);
        } else if (userEmail != null && !userEmail.isBlank() && beneficiaryRepository != null) {
            beneficiary = beneficiaryRepository.findByUser_Email(userEmail).orElse(null);
        }
        if (eligibilityScoringService == null) {
            throw new IllegalStateException("Eligibility scoring engine is not configured.");
        }
        return eligibilityScoringService.evaluate(null, scheme, beneficiary, answers);
    }
}
