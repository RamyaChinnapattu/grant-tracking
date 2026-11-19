package com.grantora.service;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.entity.GrantApplication;
import com.grantora.entity.Scheme;
import com.grantora.entity.SchemeEligibilityQuestion;
import com.grantora.repository.GrantApplicationRepository;
import com.grantora.repository.SchemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SchemeLifecycleAndDataIntegrityTest {

    private SchemeService schemeService;
    private Scheme existingScheme;
    private SchemeEligibilityQuestion q1;
    private List<GrantApplication> associatedApplications;
    private boolean schemeHasApplications;
    private final AtomicBoolean deleteCalled = new AtomicBoolean(false);
    private final AtomicLong nextQuestionId = new AtomicLong(105L);

    @BeforeEach
    void setUp() {
        deleteCalled.set(false);
        schemeHasApplications = false;
        associatedApplications = new ArrayList<>();

        existingScheme = new Scheme();
        existingScheme.setId(1L);
        existingScheme.setSchemeName("Solar Irrigation Subsidy");
        existingScheme.setDepartment("Renewable Energy");
        existingScheme.setStatus("ACTIVE");
        existingScheme.setMinimumAge(18);
        existingScheme.setMaximumAge(60);
        existingScheme.setMaximumAnnualIncome(new BigDecimal("500000.00"));
        existingScheme.setMaximumGrantAmount(new BigDecimal("100000.00"));
        existingScheme.setEligibleCategories(Set.of("GENERAL", "OBC"));
        existingScheme.setEligibilityThreshold(new BigDecimal("70.00"));
        existingScheme.setApplicationStartDate(LocalDate.of(2026, 1, 1));
        existingScheme.setApplicationEndDate(LocalDate.of(2026, 12, 31));

        q1 = new SchemeEligibilityQuestion();
        q1.setId(101L);
        q1.setScheme(existingScheme);
        q1.setDisplayOrder(0);
        q1.setQuestionText("Do you own agricultural land?");
        q1.setQuestionType("YES_NO");
        q1.setRequired(true);
        q1.setMaximumPoints(new BigDecimal("100.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));

        existingScheme.setEligibilityQuestions(new ArrayList<>(List.of(q1)));

        SchemeRepository schemeRepository = (SchemeRepository) Proxy.newProxyInstance(
                SchemeRepository.class.getClassLoader(),
                new Class<?>[]{SchemeRepository.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("findById".equals(methodName)) {
                        Long id = (Long) args[0];
                        return Long.valueOf(1L).equals(id) ? Optional.of(existingScheme) : Optional.empty();
                    }
                    if ("save".equals(methodName)) {
                        Scheme saved = (Scheme) args[0];
                        if (saved.getId() == null) {
                            saved.setId(1L);
                        }
                        if (saved.getEligibilityQuestions() != null) {
                            for (SchemeEligibilityQuestion question : saved.getEligibilityQuestions()) {
                                if (question.getId() == null) {
                                    question.setId(nextQuestionId.getAndIncrement());
                                }
                            }
                        }
                        return saved;
                    }
                    if ("delete".equals(methodName)) {
                        deleteCalled.set(true);
                        return null;
                    }
                    return null;
                });

        GrantApplicationRepository grantApplicationRepository = (GrantApplicationRepository) Proxy.newProxyInstance(
                GrantApplicationRepository.class.getClassLoader(),
                new Class<?>[]{GrantApplicationRepository.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();
                    if ("existsByScheme_Id".equals(methodName)) {
                        return schemeHasApplications;
                    }
                    if ("findByScheme_Id".equals(methodName)) {
                        return associatedApplications;
                    }
                    return null;
                });

        schemeService = new SchemeService(
                schemeRepository,
                null,
                new EligibilityScoringService(),
                grantApplicationRepository
        );
    }

    private Scheme copyOf(Scheme original) {
        Scheme copy = new Scheme();
        copy.setId(original.getId());
        copy.setSchemeName(original.getSchemeName());
        copy.setDescription(original.getDescription());
        copy.setDepartment(original.getDepartment());
        copy.setMinimumAge(original.getMinimumAge());
        copy.setMaximumAge(original.getMaximumAge());
        copy.setMaximumAnnualIncome(original.getMaximumAnnualIncome());
        copy.setMaximumGrantAmount(original.getMaximumGrantAmount());
        copy.setEligibilityThreshold(original.getEligibilityThreshold());
        copy.setEligibleCategories(original.getEligibleCategories());
        copy.setApplicationStartDate(original.getApplicationStartDate());
        copy.setApplicationEndDate(original.getApplicationEndDate());
        copy.setStatus(original.getStatus());
        return copy;
    }

    @Test
    void updateExistingQuestion_preservesQuestionId() {
        Scheme updateRequest = copyOf(existingScheme);

        SchemeEligibilityQuestion updatedQ1 = new SchemeEligibilityQuestion();
        updatedQ1.setId(101L);
        updatedQ1.setQuestionText("Updated text: Do you own or lease land?");
        updatedQ1.setQuestionType("YES_NO");
        updatedQ1.setRequired(true);
        updatedQ1.setMaximumPoints(new BigDecimal("100.00"));
        updatedQ1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        updateRequest.setEligibilityQuestions(List.of(updatedQ1));

        Scheme result = schemeService.updateScheme(1L, updateRequest);

        assertNotNull(result);
        assertEquals(1, result.getEligibilityQuestions().size());
        SchemeEligibilityQuestion savedQuestion = result.getEligibilityQuestions().get(0);
        assertEquals(101L, savedQuestion.getId(), "Existing question ID must be preserved");
        assertEquals("Updated text: Do you own or lease land?", savedQuestion.getQuestionText());
    }

    @Test
    void addNewQuestion_createsNewQuestionWithNewId() {
        Scheme updateRequest = copyOf(existingScheme);

        SchemeEligibilityQuestion existingQ1 = new SchemeEligibilityQuestion();
        existingQ1.setId(101L);
        existingQ1.setQuestionText("Do you own agricultural land?");
        existingQ1.setQuestionType("YES_NO");
        existingQ1.setRequired(true);
        existingQ1.setMaximumPoints(new BigDecimal("60.00"));
        existingQ1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("60.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));

        SchemeEligibilityQuestion newQ2 = new SchemeEligibilityQuestion();
        newQ2.setId(null); // Brand new question
        newQ2.setQuestionText("Is grid electricity available?");
        newQ2.setQuestionType("YES_NO");
        newQ2.setRequired(true);
        newQ2.setMaximumPoints(new BigDecimal("40.00"));
        newQ2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("40.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));

        updateRequest.setEligibilityQuestions(List.of(existingQ1, newQ2));

        Scheme result = schemeService.updateScheme(1L, updateRequest);

        assertNotNull(result);
        assertEquals(2, result.getEligibilityQuestions().size());
        assertEquals(101L, result.getEligibilityQuestions().get(0).getId(), "Existing question ID must be preserved");
        assertNotNull(result.getEligibilityQuestions().get(1).getId(), "New question must receive a newly generated ID");
        assertNotEquals(101L, result.getEligibilityQuestions().get(1).getId());
        assertEquals(0, result.getEligibilityQuestions().get(0).getDisplayOrder());
        assertEquals(1, result.getEligibilityQuestions().get(1).getDisplayOrder());
    }

    @Test
    void questionIdBelongingToAnotherScheme_isRejected() {
        Scheme updateRequest = copyOf(existingScheme);

        SchemeEligibilityQuestion alienQuestion = new SchemeEligibilityQuestion();
        alienQuestion.setId(999L); // Does not belong to scheme 1
        alienQuestion.setQuestionText("Alien question");
        alienQuestion.setQuestionType("YES_NO");
        alienQuestion.setRequired(true);
        alienQuestion.setMaximumPoints(new BigDecimal("100.00"));
        alienQuestion.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        updateRequest.setEligibilityQuestions(List.of(alienQuestion));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.updateScheme(1L, updateRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("does not belong to scheme 1"),
                "Error must clearly indicate the question does not belong to the target scheme");
    }

    @Test
    void removingQuestionReferencedByExistingApplication_isSafelyRejected() {
        // Scheme currently has Q1 (101L) and Q2 (102L)
        SchemeEligibilityQuestion q2 = new SchemeEligibilityQuestion();
        q2.setId(102L);
        q2.setScheme(existingScheme);
        q2.setDisplayOrder(1);
        q2.setQuestionText("Has existing solar pump?");
        q2.setQuestionType("YES_NO");
        q2.setRequired(true);
        q2.setMaximumPoints(new BigDecimal("50.00"));
        q2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        q1.setMaximumPoints(new BigDecimal("50.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        existingScheme.setEligibilityQuestions(new ArrayList<>(List.of(q1, q2)));

        // Create an existing application referencing Q2
        GrantApplication application = new GrantApplication();
        application.setScheme(existingScheme);
        application.setEligibilityAnswers(List.of(
                new EligibilityAnswer(101L, "YES"),
                new EligibilityAnswer(102L, "NO")
        ));
        associatedApplications.add(application);

        // Admin tries to remove Q2 by only sending Q1 (reweighted to 100)
        Scheme updateRequest = copyOf(existingScheme);
        SchemeEligibilityQuestion retainedQ1 = new SchemeEligibilityQuestion();
        retainedQ1.setId(101L);
        retainedQ1.setQuestionText("Do you own agricultural land?");
        retainedQ1.setQuestionType("YES_NO");
        retainedQ1.setRequired(true);
        retainedQ1.setMaximumPoints(new BigDecimal("100.00"));
        retainedQ1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        updateRequest.setEligibilityQuestions(List.of(retainedQ1));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.updateScheme(1L, updateRequest)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cannot remove question"),
                "Error must explain that the question cannot be removed");
        assertTrue(ex.getReason().contains("existing grant applications"),
                "Error must mention existing grant applications");
    }

    @Test
    void removingQuestionNotReferencedByApplications_succeeds() {
        // Scheme has Q1 (101L) and Q2 (102L)
        SchemeEligibilityQuestion q2 = new SchemeEligibilityQuestion();
        q2.setId(102L);
        q2.setScheme(existingScheme);
        q2.setDisplayOrder(1);
        q2.setQuestionText("Unanswered question");
        q2.setQuestionType("YES_NO");
        q2.setRequired(true);
        q2.setMaximumPoints(new BigDecimal("50.00"));
        q2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        q1.setMaximumPoints(new BigDecimal("50.00"));
        q1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        existingScheme.setEligibilityQuestions(new ArrayList<>(List.of(q1, q2)));

        // Application exists but only answered Q1 (101L)
        GrantApplication application = new GrantApplication();
        application.setScheme(existingScheme);
        application.setEligibilityAnswers(List.of(new EligibilityAnswer(101L, "YES")));
        associatedApplications.add(application);

        // Admin updates scheme removing unreferenced Q2
        Scheme updateRequest = copyOf(existingScheme);
        SchemeEligibilityQuestion retainedQ1 = new SchemeEligibilityQuestion();
        retainedQ1.setId(101L);
        retainedQ1.setQuestionText("Do you own agricultural land?");
        retainedQ1.setQuestionType("YES_NO");
        retainedQ1.setRequired(true);
        retainedQ1.setMaximumPoints(new BigDecimal("100.00"));
        retainedQ1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        updateRequest.setEligibilityQuestions(List.of(retainedQ1));

        Scheme result = schemeService.updateScheme(1L, updateRequest);

        assertNotNull(result);
        assertEquals(1, result.getEligibilityQuestions().size());
        assertEquals(101L, result.getEligibilityQuestions().get(0).getId());
    }

    @Test
    void existingApplicationAnswers_remainValidAfterEditingOtherSchemeFields() {
        // Existing application references question 101L
        GrantApplication application = new GrantApplication();
        application.setScheme(existingScheme);
        application.setEligibilityAnswers(List.of(new EligibilityAnswer(101L, "YES")));
        associatedApplications.add(application);

        // Admin edits scheme name, department, end date, and grant amount
        Scheme updateRequest = copyOf(existingScheme);
        updateRequest.setSchemeName("Prime Solar Irrigation Scheme 2026");
        updateRequest.setDepartment("Ministry of New & Renewable Energy");
        updateRequest.setMaximumGrantAmount(new BigDecimal("150000.00"));
        updateRequest.setApplicationEndDate(LocalDate.of(2027, 3, 31));

        SchemeEligibilityQuestion retainedQ1 = new SchemeEligibilityQuestion();
        retainedQ1.setId(101L);
        retainedQ1.setQuestionText("Do you own agricultural land?");
        retainedQ1.setQuestionType("YES_NO");
        retainedQ1.setRequired(true);
        retainedQ1.setMaximumPoints(new BigDecimal("100.00"));
        retainedQ1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        updateRequest.setEligibilityQuestions(List.of(retainedQ1));

        Scheme result = schemeService.updateScheme(1L, updateRequest);

        assertNotNull(result);
        assertEquals("Prime Solar Irrigation Scheme 2026", result.getSchemeName());
        // Verify question ID is preserved so existing application answer remains valid
        assertEquals(1, result.getEligibilityQuestions().size());
        assertEquals(101L, result.getEligibilityQuestions().get(0).getId());
        assertEquals(application.getEligibilityAnswers().get(0).questionId(),
                result.getEligibilityQuestions().get(0).getId(),
                "Application answer questionId must still match the scheme question ID");
    }

    @Test
    void deleteScheme_withAssociatedApplications_isRejected() {
        schemeHasApplications = true;

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.deleteScheme(1L)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Cannot delete scheme"), "Error must explain deletion failure");
        assertTrue(ex.getReason().contains("Deactivate the scheme instead"), "Error must suggest deactivation");
        assertFalse(deleteCalled.get(), "Repository delete must NOT be called when applications exist");
    }

    @Test
    void deleteScheme_withoutApplications_succeeds() {
        schemeHasApplications = false;

        assertDoesNotThrow(() -> schemeService.deleteScheme(1L));
        assertTrue(deleteCalled.get(), "Repository delete must be executed when no applications exist");
    }

    @Test
    void endDateEarlierThanStartDate_isRejected() {
        Scheme scheme = copyOf(existingScheme);
        scheme.setApplicationStartDate(LocalDate.of(2026, 6, 1));
        scheme.setApplicationEndDate(LocalDate.of(2026, 5, 31)); // Earlier than start date
        scheme.setEligibilityQuestions(List.of(q1));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.updateScheme(1L, scheme)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Application end date must not be earlier than application start date"));
    }

    @Test
    void negativeMaximumGrantAmount_isRejected() {
        Scheme scheme = copyOf(existingScheme);
        scheme.setMaximumGrantAmount(new BigDecimal("-0.01"));
        scheme.setEligibilityQuestions(List.of(q1));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.updateScheme(1L, scheme)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Maximum grant amount must be zero or greater"));
    }

    @Test
    void createScheme_withExistingQuestionId_isRejected() {
        Scheme newScheme = copyOf(existingScheme);
        newScheme.setId(null);

        SchemeEligibilityQuestion questionWithId = new SchemeEligibilityQuestion();
        questionWithId.setId(999L);
        questionWithId.setQuestionText("New question with pre-set ID");
        questionWithId.setQuestionType("YES_NO");
        questionWithId.setRequired(true);
        questionWithId.setMaximumPoints(new BigDecimal("100.00"));
        questionWithId.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("100.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));
        newScheme.setEligibilityQuestions(List.of(questionWithId));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.createScheme(newScheme)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("New questions cannot specify an existing ID"));
    }

    @Test
    void duplicateQuestionIdsInUpdate_isRejected() {
        Scheme updateRequest = copyOf(existingScheme);

        SchemeEligibilityQuestion copy1 = new SchemeEligibilityQuestion();
        copy1.setId(101L);
        copy1.setQuestionText("Question 1");
        copy1.setQuestionType("YES_NO");
        copy1.setRequired(true);
        copy1.setMaximumPoints(new BigDecimal("50.00"));
        copy1.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));

        SchemeEligibilityQuestion copy2 = new SchemeEligibilityQuestion();
        copy2.setId(101L); // Duplicate ID!
        copy2.setQuestionText("Question 1 duplicate");
        copy2.setQuestionType("YES_NO");
        copy2.setRequired(true);
        copy2.setMaximumPoints(new BigDecimal("50.00"));
        copy2.setAnswerOptions(List.of(
                new SchemeEligibilityQuestion.AnswerOption("YES", "Yes", new BigDecimal("50.00"), true),
                new SchemeEligibilityQuestion.AnswerOption("NO", "No", BigDecimal.ZERO, false)
        ));

        updateRequest.setEligibilityQuestions(List.of(copy1, copy2));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeService.updateScheme(1L, updateRequest)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Duplicate question ID"));
    }
}
