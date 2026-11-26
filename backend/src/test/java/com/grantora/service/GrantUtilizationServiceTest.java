package com.grantora.service;

import com.grantora.dto.GrantUtilizationRequest;
import com.grantora.dto.GrantUtilizationResponse;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GrantUtilizationServiceTest {

    private GrantUtilizationRepository records;
    private GrantApplicationRepository applications;
    private DisbursementInstallmentRepository installments;
    private MilestoneComplianceRepository evidence;
    private UserRepository users;
    private WorkflowAutomationService notifications;
    private GrantUtilizationService service;

    private User beneficiaryUser;
    private User officerUser;
    private User districtOfficerUser;
    private User financeOfficerUser;
    private GrantApplication application;
    private DisbursementInstallment installment;
    private MilestoneCompliance complianceDoc;

    @BeforeEach
    void setUp() {
        records = mock(GrantUtilizationRepository.class);
        applications = mock(GrantApplicationRepository.class);
        installments = mock(DisbursementInstallmentRepository.class);
        evidence = mock(MilestoneComplianceRepository.class);
        users = mock(UserRepository.class);
        notifications = mock(WorkflowAutomationService.class);

        service = new GrantUtilizationService(records, applications, installments, evidence, users, notifications);

        beneficiaryUser = new User();
        beneficiaryUser.setId(10L);
        beneficiaryUser.setEmail("beneficiary@example.com");
        beneficiaryUser.setRole(Role.BENEFICIARY);
        beneficiaryUser.setFullName("Test Beneficiary");

        officerUser = new User();
        officerUser.setId(20L);
        officerUser.setEmail("officer@example.com");
        officerUser.setRole(Role.OFFICER);
        officerUser.setOfficerLevel("FIELD");
        officerUser.setJurisdictionState("Karnataka");
        officerUser.setJurisdictionDistrict("Mysuru");
        officerUser.setActive(true);
        officerUser.setFullName("Test Officer");

        districtOfficerUser = new User();
        districtOfficerUser.setId(21L);
        districtOfficerUser.setEmail("district@example.com");
        districtOfficerUser.setRole(Role.OFFICER);
        districtOfficerUser.setOfficerLevel("DISTRICT");
        districtOfficerUser.setJurisdictionState("Karnataka");
        districtOfficerUser.setJurisdictionDistrict("Mysuru");
        districtOfficerUser.setActive(true);
        districtOfficerUser.setFullName("District Officer");

        financeOfficerUser = new User();
        financeOfficerUser.setId(22L);
        financeOfficerUser.setEmail("finance@example.com");
        financeOfficerUser.setRole(Role.OFFICER);
        financeOfficerUser.setOfficerLevel("FINANCE");
        financeOfficerUser.setJurisdictionState("Karnataka");
        financeOfficerUser.setActive(true);
        financeOfficerUser.setFullName("Finance Officer");

        Scheme scheme = new Scheme();
        scheme.setId(1L);
        scheme.setSchemeName("Agriculture Modernization");

        Beneficiary beneficiary = new Beneficiary();
        beneficiary.setId(100L);
        beneficiary.setUser(beneficiaryUser);
        beneficiary.setState("Karnataka");
        beneficiary.setDistrict("Mysuru");

        application = new GrantApplication();
        ReflectionTestUtils.setField(application, "id", 1000L);
        application.setScheme(scheme);
        application.setBeneficiary(beneficiary);
        application.setStatus(GrantApplicationStatus.APPROVED);
        application.setAssignedOfficer(officerUser);

        DisbursementPlan plan = new DisbursementPlan();
        plan.setId(2000L);
        plan.setApplication(application);
        plan.setScheme(scheme);

        installment = new DisbursementInstallment();
        installment.setId(3000L);
        installment.setPlan(plan);
        installment.setInstallmentNumber(1);
        installment.setTitle("Equipment Purchase");
        installment.setAmount(BigDecimal.valueOf(100000));
        installment.setStatus(InstallmentStatus.DISBURSED);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setId(4000L);
        milestone.setInstallment(installment);

        complianceDoc = new MilestoneCompliance();
        complianceDoc.setId(5000L);
        complianceDoc.setMilestone(milestone);
        complianceDoc.setDocumentFileName("invoice.pdf");
        complianceDoc.setDocumentData("%PDF-1.4 test".getBytes());
        complianceDoc.setStatus(ComplianceStatus.VERIFIED);
    }

    @Test
    void submit_validReport_createsRecordAndNotifiesOfficer() {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(applications.findById(1000L)).thenReturn(Optional.of(application));
        when(installments.findById(3000L)).thenReturn(Optional.of(installment));
        when(evidence.findById(5000L)).thenReturn(Optional.of(complianceDoc));
        when(records.findAllByOrderBySubmittedAtDesc()).thenReturn(Collections.emptyList());
        when(records.save(any(GrantUtilizationRecord.class))).thenAnswer(inv -> {
            GrantUtilizationRecord r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 7000L);
            return r;
        });

        when(users.findAll()).thenReturn(List.of(officerUser, districtOfficerUser, financeOfficerUser));

        GrantUtilizationRequest req = new GrantUtilizationRequest(
                1000L, 3000L, BigDecimal.valueOf(40000), "Equipment", "Tractor invoice", List.of(5000L)
        );

        GrantUtilizationResponse res = service.submit(req, "beneficiary@example.com");

        assertNotNull(res);
        assertEquals(7000L, res.id());
        assertEquals(GrantUtilizationStatus.FIELD_PENDING, res.status());
        assertEquals(BigDecimal.valueOf(40000), res.reportedAmount());
        assertEquals(1, res.supportingComplianceIds().size());
        verify(notifications).notify(eq(officerUser), anyString(), anyString(), eq(1000L));
    }

    @Test
    void submit_missingEvidence_throwsBadRequest() {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(applications.findById(1000L)).thenReturn(Optional.of(application));
        when(installments.findById(3000L)).thenReturn(Optional.of(installment));

        GrantUtilizationRequest reqNoEvidence = new GrantUtilizationRequest(
                1000L, 3000L, BigDecimal.valueOf(40000), "Equipment", "Notes", Collections.emptyList()
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(reqNoEvidence, "beneficiary@example.com"));
        assertTrue(ex.getReason().contains("Attach a supporting document or select at least one existing compliance document reference"));
    }

    @Test
    void submit_amountExceedingInstallmentHeadroom_throwsBadRequest() {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(applications.findById(1000L)).thenReturn(Optional.of(application));
        when(installments.findById(3000L)).thenReturn(Optional.of(installment));
        when(evidence.findById(5000L)).thenReturn(Optional.of(complianceDoc));

        // Existing approved/submitted report of 70,000 against 100,000 budget
        GrantUtilizationRecord existing = new GrantUtilizationRecord();
        ReflectionTestUtils.setField(existing, "id", 8000L);
        existing.setInstallment(installment);
        existing.setReportedAmount(BigDecimal.valueOf(70000));
        existing.setStatus(GrantUtilizationStatus.SUBMITTED);

        when(records.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of(existing));

        // Attempting to report 40,000 (70,000 + 40,000 = 110,000 > 100,000)
        GrantUtilizationRequest reqExceeding = new GrantUtilizationRequest(
                1000L, 3000L, BigDecimal.valueOf(40000), "Equipment", "Over budget", List.of(5000L)
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(reqExceeding, "beneficiary@example.com"));
        assertTrue(ex.getReason().contains("cannot exceed the released installment amount"));
    }

    @Test
    void replace_correctionRequired_resubmitsSuccessfully() {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(evidence.findById(5000L)).thenReturn(Optional.of(complianceDoc));

        GrantUtilizationRecord existing = new GrantUtilizationRecord();
        ReflectionTestUtils.setField(existing, "id", 9000L);
        existing.setApplication(application);
        existing.setInstallment(installment);
        existing.setSubmittedBy(beneficiaryUser);
        existing.setStatus(GrantUtilizationStatus.CORRECTION_REQUIRED);
        existing.setReportedAmount(BigDecimal.valueOf(50000));

        when(records.findById(9000L)).thenReturn(Optional.of(existing));
        when(records.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of(existing));
        when(records.save(any(GrantUtilizationRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GrantUtilizationRequest correctedReq = new GrantUtilizationRequest(
                1000L, 3000L, BigDecimal.valueOf(60000), "Machinery", "Corrected invoice", List.of(5000L)
        );

        GrantUtilizationResponse res = service.replace(9000L, correctedReq, "beneficiary@example.com");

        assertNotNull(res);
        assertEquals(GrantUtilizationStatus.FIELD_PENDING, res.status());
        assertEquals(BigDecimal.valueOf(60000), res.reportedAmount());
        assertEquals("Machinery", res.category());
    }

    @Test
    void mine_returnsOnlyBeneficiaryReportsWithSupportingDocs() {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));

        GrantUtilizationRecord r = new GrantUtilizationRecord();
        ReflectionTestUtils.setField(r, "id", 11000L);
        r.setApplication(application);
        r.setInstallment(installment);
        r.setSubmittedBy(beneficiaryUser);
        r.setStatus(GrantUtilizationStatus.VERIFIED);
        r.setReportedAmount(BigDecimal.valueOf(35000));
        r.setCategory("Supplies");
        r.setSubmittedAt(LocalDateTime.now().minusDays(2));
        r.setSupportingDocuments(Set.of(complianceDoc));

        when(records.findByApplication_Beneficiary_User_IdOrderBySubmittedAtDesc(10L))
                .thenReturn(List.of(r));

        List<GrantUtilizationResponse> mine = service.mine("beneficiary@example.com");

        assertEquals(1, mine.size());
        assertEquals(11000L, mine.get(0).id());
        assertEquals(1, mine.get(0).supportingDocumentNames().size());
        assertEquals("invoice.pdf", mine.get(0).supportingDocumentNames().get(0));
    }

    @Test
    void sequentialFieldDistrictFinanceReviewAndDocumentVersions() throws Exception {
        when(users.findByEmail("beneficiary@example.com")).thenReturn(Optional.of(beneficiaryUser));
        when(users.findByEmail("officer@example.com")).thenReturn(Optional.of(officerUser));
        when(users.findByEmail("district@example.com")).thenReturn(Optional.of(districtOfficerUser));
        when(users.findByEmail("finance@example.com")).thenReturn(Optional.of(financeOfficerUser));
        when(users.findAll()).thenReturn(List.of(officerUser, districtOfficerUser, financeOfficerUser));
        when(applications.findById(1000L)).thenReturn(Optional.of(application));
        when(installments.findById(3000L)).thenReturn(Optional.of(installment));
        when(records.findAllByOrderBySubmittedAtDesc()).thenReturn(Collections.emptyList());

        GrantUtilizationRecord[] holder = new GrantUtilizationRecord[1];
        when(records.save(any(GrantUtilizationRecord.class))).thenAnswer(inv -> {
            GrantUtilizationRecord r = inv.getArgument(0);
            if (r.getId() == null) ReflectionTestUtils.setField(r, "id", 7500L);
            holder[0] = r;
            return r;
        });
        when(records.findById(7500L)).thenAnswer(inv -> Optional.ofNullable(holder[0]));

        org.springframework.web.multipart.MultipartFile file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("bill-v1.pdf");
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(16L);
        when(file.getBytes()).thenReturn("%PDF-1.7 bill-v1".getBytes());

        GrantUtilizationRequest req = new GrantUtilizationRequest(1000L, 3000L, BigDecimal.valueOf(25000), "Seeds", "Seed bill", List.of());
        var submitted = service.submitWithDocument(req, file, "beneficiary@example.com");
        assertEquals(GrantUtilizationStatus.FIELD_PENDING, submitted.status());
        assertEquals(1, submitted.documentVersions().size());

        // District officer cannot review FIELD_PENDING
        assertThrows(ResponseStatusException.class, () -> service.review(7500L, new com.grantora.dto.GrantUtilizationReviewRequest("APPROVED", "Skip"), "district@example.com"));

        // Field approves -> DISTRICT_PENDING
        var afterField = service.review(7500L, new com.grantora.dto.GrantUtilizationReviewRequest("APPROVED", "Evidence verified"), "officer@example.com");
        assertEquals(GrantUtilizationStatus.DISTRICT_PENDING, afterField.status());

        // District approves -> FINANCE_PENDING
        var afterDistrict = service.review(7500L, new com.grantora.dto.GrantUtilizationReviewRequest("APPROVED", "District verified"), "district@example.com");
        assertEquals(GrantUtilizationStatus.FINANCE_PENDING, afterDistrict.status());

        // Finance approves -> VERIFIED
        var afterFinance = service.review(7500L, new com.grantora.dto.GrantUtilizationReviewRequest("APPROVED", "Amounts validated"), "finance@example.com");
        assertEquals(GrantUtilizationStatus.VERIFIED, afterFinance.status());
        assertEquals(3, afterFinance.stageReviews().size());
    }
}
