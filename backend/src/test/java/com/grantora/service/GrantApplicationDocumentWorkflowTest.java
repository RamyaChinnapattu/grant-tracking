package com.grantora.service;

import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.dto.VerificationDecisionRequest;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GrantApplicationDocumentWorkflowTest {

    private GrantApplicationRepository applicationRepository;
    private GrantApplicationStatusHistoryRepository statusHistoryRepository;
    private BeneficiaryRepository beneficiaryRepository;
    private SchemeRepository schemeRepository;
    private EligibilityScoringService eligibilityScoringService;
    private VerificationWorkflowService verificationWorkflowService;
    private ApplicationVerificationRepository verificationRepository;
    private ApplicationDocumentRepository documentRepository;
    private UserRepository userRepository;

    private GrantApplicationService applicationService;
    private GrantApplication application;
    private User beneficiaryUser;

    @BeforeEach
    void setUp() throws Exception {
        applicationRepository = mock(GrantApplicationRepository.class);
        statusHistoryRepository = mock(GrantApplicationStatusHistoryRepository.class);
        beneficiaryRepository = mock(BeneficiaryRepository.class);
        schemeRepository = mock(SchemeRepository.class);
        eligibilityScoringService = mock(EligibilityScoringService.class);
        verificationWorkflowService = mock(VerificationWorkflowService.class);
        verificationRepository = mock(ApplicationVerificationRepository.class);
        documentRepository = mock(ApplicationDocumentRepository.class);
        userRepository = mock(UserRepository.class);

        applicationService = new GrantApplicationService(
                applicationRepository,
                statusHistoryRepository,
                beneficiaryRepository,
                schemeRepository,
                eligibilityScoringService,
                verificationWorkflowService,
                verificationRepository,
                documentRepository,
                userRepository);

        beneficiaryUser = new User();
        setId(beneficiaryUser, 1L);
        beneficiaryUser.setEmail("beneficiary@test.local");
        beneficiaryUser.setFullName("Test Beneficiary");
        beneficiaryUser.setRole(Role.BENEFICIARY);

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setUser(beneficiaryUser);

        Scheme scheme = new Scheme();
        scheme.setStatus("ACTIVE");
        scheme.setEligibilityQuestions(List.of());

        application = new GrantApplication();
        setId(application, 100L);
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setApplicationDetails("Valid application details");
        application.setRequestedAmount(new BigDecimal("10000.00"));
        application.setStatus(GrantApplicationStatus.DRAFT);
        application.setEligibilityAnswers(List.of());

        when(applicationRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(application));
        when(applicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(userRepository.findByEmail("beneficiary@test.local")).thenReturn(Optional.of(beneficiaryUser));
        when(eligibilityScoringService.evaluate(any(), any(), any(), any()))
                .thenReturn(new EligibilityEvaluationResult(
                        100L, null, "ELIGIBLE", BigDecimal.valueOf(80), BigDecimal.valueOf(80),
                        BigDecimal.valueOf(80), List.of(), List.of(), List.of(), List.of(),
                        "Eligible", BigDecimal.valueOf(70), LocalDateTime.now()));
        when(verificationWorkflowService.submit(any(), any())).thenAnswer(invocation -> {
            GrantApplication app = invocation.getArgument(0);
            app.setStatus(GrantApplicationStatus.SUBMITTED);
            return app;
        });
    }

    @Test
    void submitDraft_shouldFailWhenNoDocumentsUploaded() {
        when(documentRepository.countByApplication_Id(100L)).thenReturn(0L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> applicationService.submitDraft(100L, "beneficiary@test.local"));

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("At least one supporting document must be uploaded"));
        assertEquals(GrantApplicationStatus.DRAFT, application.getStatus());
        verify(verificationWorkflowService, never()).submit(any(), any());
    }

    @Test
    void submitDraft_shouldSucceedWhenDocumentsUploaded() {
        when(documentRepository.countByApplication_Id(100L)).thenReturn(1L);
        when(applicationRepository.save(any(GrantApplication.class))).thenReturn(application);

        var response = applicationService.submitDraft(100L, "beneficiary@test.local");

        assertNotNull(response);
        assertEquals(GrantApplicationStatus.SUBMITTED, application.getStatus());
        verify(verificationWorkflowService).submit(application, "beneficiary@test.local");
    }

    @Test
    void addDocument_shouldSucceedInDraftState() {
        application.setStatus(GrantApplicationStatus.DRAFT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "id_proof.pdf", "application/pdf", "%PDF-1.4 dummy pdf content".getBytes());

        when(documentRepository.countByApplication_Id(100L)).thenReturn(0L);
        when(documentRepository.save(any(ApplicationDocument.class))).thenAnswer(invocation -> {
            ApplicationDocument doc = invocation.getArgument(0);
            setId(doc, 501L);
            return doc;
        });

        var response = applicationService.addDocument(100L, "beneficiary@test.local", file);

        assertNotNull(response);
        assertEquals("id_proof.pdf", response.fileName());
        verify(documentRepository).save(any(ApplicationDocument.class));
    }

    @Test
    void addDocument_shouldSucceedInNeedsCorrectionState() {
        application.setStatus(GrantApplicationStatus.NEEDS_CORRECTION);
        MockMultipartFile file = new MockMultipartFile(
                "file", "income_cert.pdf", "application/pdf", "%PDF-1.4 dummy pdf content".getBytes());

        when(documentRepository.countByApplication_Id(100L)).thenReturn(1L);
        when(documentRepository.save(any(ApplicationDocument.class))).thenAnswer(invocation -> {
            ApplicationDocument doc = invocation.getArgument(0);
            setId(doc, 502L);
            return doc;
        });

        var response = applicationService.addDocument(100L, "beneficiary@test.local", file);

        assertNotNull(response);
        assertEquals("income_cert.pdf", response.fileName());
    }

    @Test
    void addDocument_shouldSucceedInSubmittedStateBeforeVerificationBegins() {
        application.setStatus(GrantApplicationStatus.SUBMITTED);
        when(verificationRepository.findByApplication_IdOrderByCreatedAtAsc(100L))
                .thenReturn(List.of(new ApplicationVerification() {
                    {
                        setAction("SUBMITTED");
                    }
                }));

        MockMultipartFile file = new MockMultipartFile(
                "file", "additional_proof.pdf", "application/pdf", "%PDF-1.4 pdf bytes".getBytes());

        when(documentRepository.countByApplication_Id(100L)).thenReturn(1L);
        when(documentRepository.save(any(ApplicationDocument.class))).thenAnswer(invocation -> {
            ApplicationDocument doc = invocation.getArgument(0);
            setId(doc, 503L);
            return doc;
        });

        var response = applicationService.addDocument(100L, "beneficiary@test.local", file);

        assertNotNull(response);
        assertEquals("additional_proof.pdf", response.fileName());
    }

    @Test
    void addDocument_shouldFailInProhibitedStates() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "%PDF-1.4 bytes".getBytes());

        for (GrantApplicationStatus prohibitedStatus : List.of(
                GrantApplicationStatus.FIELD_VERIFICATION,
                GrantApplicationStatus.DISTRICT_REVIEW,
                GrantApplicationStatus.FINANCE_REVIEW,
                GrantApplicationStatus.APPROVED,
                GrantApplicationStatus.REJECTED,
                GrantApplicationStatus.WITHDRAWN)) {

            application.setStatus(prohibitedStatus);

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> applicationService.addDocument(100L, "beneficiary@test.local", file),
                    "Expected addDocument to fail for status: " + prohibitedStatus);

            assertEquals(409, ex.getStatusCode().value());
        }
    }

    private void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
