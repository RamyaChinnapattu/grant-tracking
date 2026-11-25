package com.grantora.service;

import com.grantora.dto.GrantApplicationResponse;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrantApplicationDisbursementSummaryTest {

    private GrantApplicationRepository applicationRepository;
    private BeneficiaryRepository beneficiaryRepository;
    private ApplicationVerificationRepository verificationRepository;
    private ApplicationDocumentRepository documentRepository;
    private GrantApplicationStatusHistoryRepository statusHistoryRepository;
    private DisbursementPlanRepository disbursementPlanRepository;
    private GrantApplicationService service;

    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private Scheme scheme;
    private GrantApplication application;

    @BeforeEach
    void setUp() throws Exception {
        applicationRepository = mock(GrantApplicationRepository.class);
        statusHistoryRepository = mock(GrantApplicationStatusHistoryRepository.class);
        beneficiaryRepository = mock(BeneficiaryRepository.class);
        SchemeRepository schemeRepository = mock(SchemeRepository.class);
        EligibilityScoringService scoringService = mock(EligibilityScoringService.class);
        VerificationWorkflowService workflowService = mock(VerificationWorkflowService.class);
        verificationRepository = mock(ApplicationVerificationRepository.class);
        documentRepository = mock(ApplicationDocumentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        disbursementPlanRepository = mock(DisbursementPlanRepository.class);

        service = new GrantApplicationService(
                applicationRepository,
                statusHistoryRepository,
                beneficiaryRepository,
                schemeRepository,
                scoringService,
                workflowService,
                verificationRepository,
                documentRepository,
                userRepository);
        service.setDisbursementPlanRepository(disbursementPlanRepository);

        beneficiaryUser = new User();
        beneficiaryUser.setEmail("farmer@example.com");
        beneficiary = new Beneficiary();
        setId(beneficiary, 10L);
        beneficiary.setUser(beneficiaryUser);

        scheme = new Scheme();
        scheme.setId(2L);
        scheme.setSchemeName("Agriculture Development Grant");

        application = new GrantApplication();
        setId(application, 5L);
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setApplicationDetails("Drip irrigation setup");
        application.setRequestedAmount(new BigDecimal("20000.00"));
        application.setStatus(GrantApplicationStatus.APPROVED);

        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));
        when(beneficiaryRepository.findByUser_Email("farmer@example.com")).thenReturn(Optional.of(beneficiary));
        when(applicationRepository.findByBeneficiary_IdOrderByIdDesc(10L)).thenReturn(List.of(application));
        when(verificationRepository.findByApplication_IdOrderByCreatedAtAsc(5L)).thenReturn(List.of());
        when(documentRepository.findMetadataByApplicationId(5L)).thenReturn(List.of());
        when(statusHistoryRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(5L)).thenReturn(List.of());
    }

    @Test
    void awaitingDisbursementBeforeAnyInstallmentIsReleased() {
        DisbursementPlan plan = planWithInstallments(
                DisbursementPlanStatus.ACTIVE,
                installment(1L, 1, "Stage 1", "8000.00", InstallmentStatus.READY_FOR_DISBURSEMENT, null),
                installment(2L, 2, "Stage 2", "6000.00", InstallmentStatus.PLANNED, null),
                installment(3L, 3, "Stage 3", "6000.00", InstallmentStatus.PLANNED, null)
        );
        when(disbursementPlanRepository.findByApplication_Id(5L)).thenReturn(Optional.of(plan));

        GrantApplicationResponse response = service.getApplication(5L, "admin@grantora.com", true);
        assertEquals(GrantApplicationStatus.APPROVED, response.status());
        assertNotNull(response.disbursementSummary());
        assertEquals("AWAITING_DISBURSEMENT", response.disbursementSummary().disbursementStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.disbursementSummary().disbursedAmount()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(response.disbursementSummary().remainingAmount()));
        assertEquals(0, response.disbursementSummary().releasedInstallmentsCount());
        assertEquals(3, response.disbursementSummary().totalInstallmentsCount());
    }

    @Test
    void partiallyDisbursedWhenOnlyStageOneIsReleased() {
        DisbursementPlan plan = planWithInstallments(
                DisbursementPlanStatus.ACTIVE,
                installment(1L, 1, "Stage 1", "8000.00", InstallmentStatus.DISBURSED, "UTR-001"),
                installment(2L, 2, "Stage 2", "6000.00", InstallmentStatus.READY_FOR_DISBURSEMENT, null),
                installment(3L, 3, "Stage 3", "6000.00", InstallmentStatus.PLANNED, null)
        );
        when(disbursementPlanRepository.findByApplication_Id(5L)).thenReturn(Optional.of(plan));

        List<GrantApplicationResponse> mine = service.getMyApplications("farmer@example.com");
        assertEquals(1, mine.size());
        var summary = mine.get(0).disbursementSummary();
        assertEquals(GrantApplicationStatus.APPROVED, mine.get(0).status());
        assertNotNull(summary);
        assertEquals("PARTIALLY_DISBURSED", summary.disbursementStatus());
        assertEquals(0, new BigDecimal("8000.00").compareTo(summary.disbursedAmount()));
        assertEquals(0, new BigDecimal("12000.00").compareTo(summary.remainingAmount()));
        assertEquals(1, summary.releasedInstallmentsCount());
        assertEquals(3, summary.totalInstallmentsCount());
        assertEquals("UTR-001", summary.installments().get(0).paymentReference());
    }

    @Test
    void fullyDisbursedOnlyWhenAllApplicableNonCancelledInstallmentsReleased() {
        DisbursementPlan plan = planWithInstallments(
                DisbursementPlanStatus.COMPLETED,
                installment(1L, 1, "Stage 1", "8000.00", InstallmentStatus.DISBURSED, "UTR-001"),
                installment(2L, 2, "Stage 2", "6000.00", InstallmentStatus.DISBURSED, "UTR-002"),
                installment(3L, 3, "Stage 3", "6000.00", InstallmentStatus.DISBURSED, "UTR-003"),
                installment(4L, 4, "Cancelled Extra", "0.00", InstallmentStatus.CANCELLED, null)
        );
        when(disbursementPlanRepository.findByApplication_Id(5L)).thenReturn(Optional.of(plan));

        GrantApplicationResponse response = service.getApplication(5L, "admin@grantora.com", true);
        var summary = response.disbursementSummary();
        assertEquals(GrantApplicationStatus.APPROVED, response.status());
        assertNotNull(summary);
        assertEquals("DISBURSED", summary.disbursementStatus());
        assertEquals(0, new BigDecimal("20000.00").compareTo(summary.disbursedAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.remainingAmount()));
        assertEquals(3, summary.releasedInstallmentsCount());
        assertEquals(3, summary.totalInstallmentsCount());
    }

    @Test
    void nonApprovedApplicationHasNullDisbursementSummary() {
        application.setStatus(GrantApplicationStatus.FINANCE_REVIEW);
        GrantApplicationResponse response = service.getApplication(5L, "admin@grantora.com", true);
        assertNull(response.disbursementSummary());
        verifyNoInteractions(disbursementPlanRepository);
    }

    private DisbursementPlan planWithInstallments(DisbursementPlanStatus status, DisbursementInstallment... items) {
        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(15L);
        plan.setApplication(application);
        plan.setScheme(scheme);
        plan.setTotalApprovedAmount(new BigDecimal("20000.00"));
        plan.setPlanStatus(status);
        plan.setInstallments(List.of(items));
        return plan;
    }

    private DisbursementInstallment installment(
            Long id, int number, String title, String amount, InstallmentStatus status, String ref) {
        DisbursementInstallment inst = new DisbursementInstallment();
        inst.setId(id);
        inst.setInstallmentNumber(number);
        inst.setTitle(title);
        inst.setAmount(new BigDecimal(amount));
        inst.setStatus(status);
        inst.setPlannedReleaseDate(LocalDate.of(2026, 12, number));
        inst.setPaymentReference(ref);
        if (status == InstallmentStatus.DISBURSED) {
            inst.setActualDisbursementDate(LocalDateTime.now());
        }
        return inst;
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
