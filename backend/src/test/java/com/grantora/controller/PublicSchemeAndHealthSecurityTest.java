package com.grantora.controller;

import com.grantora.entity.Scheme;
import com.grantora.repository.SchemeRepository;
import com.grantora.service.SchemeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicSchemeAndHealthSecurityTest {

    private SchemeRepository schemeRepository;
    private SchemeService schemeService;
    private SchemeController schemeController;
    private HealthController healthController;

    private Scheme activeScheme;
    private Scheme draftScheme;

    @BeforeEach
    void setUp() {
        schemeRepository = mock(SchemeRepository.class);
        schemeService = new SchemeService(schemeRepository);
        schemeController = new SchemeController(schemeService);
        healthController = new HealthController();

        activeScheme = new Scheme();
        activeScheme.setStatus("ACTIVE");

        draftScheme = new Scheme();
        draftScheme.setStatus("DRAFT");

        when(schemeRepository.findAll()).thenReturn(List.of(activeScheme, draftScheme));
        when(schemeRepository.findByStatusIgnoreCase("ACTIVE")).thenReturn(List.of(activeScheme));
        when(schemeRepository.findById(1L)).thenReturn(Optional.of(activeScheme));
        when(schemeRepository.findById(2L)).thenReturn(Optional.of(draftScheme));
    }

    @Test
    void healthEndpoint_shouldReturnRunningMessage() {
        String status = healthController.health();
        assertEquals("Grantora Backend is Running!", status);
    }

    @Test
    void anonymousUser_shouldOnlySeeActiveSchemes() {
        List<Scheme> schemes = schemeController.getAllSchemes(null);

        assertEquals(1, schemes.size());
        assertEquals("ACTIVE", schemes.get(0).getStatus());
        verify(schemeRepository).findByStatusIgnoreCase("ACTIVE");
        verify(schemeRepository, never()).findAll();
    }

    @Test
    void anonymousUser_shouldBeAbleToAccessActiveSchemeDetails() {
        Scheme scheme = schemeController.getSchemeById(1L, null);

        assertNotNull(scheme);
        assertEquals("ACTIVE", scheme.getStatus());
    }

    @Test
    void anonymousUser_shouldNotAccessDraftOrUnpublishedSchemeDetails() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> schemeController.getSchemeById(2L, null));

        assertEquals(404, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("not published") || ex.getReason().contains("not found"));
    }

    @Test
    void adminUser_shouldSeeAllSchemesIncludingDrafts() {
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@test.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        List<Scheme> schemes = schemeController.getAllSchemes(adminAuth);

        assertEquals(2, schemes.size());
        verify(schemeRepository).findAll();
    }

    @Test
    void adminUser_shouldAccessDraftSchemeDetails() {
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@test.local", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Scheme scheme = schemeController.getSchemeById(2L, adminAuth);

        assertNotNull(scheme);
        assertEquals("DRAFT", scheme.getStatus());
    }

    @Test
    void adminUser_canCreateScheme() {
        Scheme newScheme = new Scheme();
        newScheme.setSchemeName("Test Scheme");
        newScheme.setStatus("DRAFT");
        newScheme.setMinimumAge(18);
        newScheme.setMaximumAge(60);
        newScheme.setMaximumAnnualIncome(new java.math.BigDecimal("500000.00"));
        newScheme.setEligibilityThreshold(new java.math.BigDecimal("70.00"));
        newScheme.setEligibleCategories(java.util.Set.of("GENERAL"));

        com.grantora.entity.SchemeEligibilityQuestion question =
                new com.grantora.entity.SchemeEligibilityQuestion();
        question.setQuestionText("Are you a local resident?");
        question.setQuestionType("YES_NO");
        question.setRequired(true);
        question.setAnswerOptions(List.of(
                new com.grantora.entity.SchemeEligibilityQuestion.AnswerOption(
                        "YES", "Yes", java.math.BigDecimal.valueOf(100), true),
                new com.grantora.entity.SchemeEligibilityQuestion.AnswerOption(
                        "NO", "No", java.math.BigDecimal.ZERO, false)
        ));
        newScheme.setEligibilityQuestions(List.of(question));

        when(schemeRepository.save(any(Scheme.class))).thenReturn(newScheme);

        Scheme created = schemeController.createScheme(newScheme);
        assertNotNull(created);
        verify(schemeRepository).save(newScheme);
    }
}
