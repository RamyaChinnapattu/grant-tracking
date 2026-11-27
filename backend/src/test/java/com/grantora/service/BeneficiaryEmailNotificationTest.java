package com.grantora.service;

import com.grantora.entity.*;
import com.grantora.repository.BeneficiaryEmailLogRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class BeneficiaryEmailNotificationTest {

    private JavaMailSender mailSender;
    private ObjectProvider<JavaMailSender> mailSenderProvider;
    private BeneficiaryEmailLogRepository emailLogRepository;
    private BeneficiaryEmailService emailService;

    private User beneficiaryUser;
    private Beneficiary beneficiary;
    private Scheme scheme;
    private GrantApplication application;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        mailSenderProvider = mock(ObjectProvider.class);
        emailLogRepository = mock(BeneficiaryEmailLogRepository.class);

        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        when(emailLogRepository.save(any(BeneficiaryEmailLog.class))).thenAnswer(inv -> {
            BeneficiaryEmailLog log = inv.getArgument(0);
            if (log.getId() == null) {
                ReflectionTestUtils.setField(log, "id", 101L);
            }
            return log;
        });

        emailService = new BeneficiaryEmailService(mailSenderProvider, emailLogRepository);
        ReflectionTestUtils.setField(emailService, "mailEnabled", true);
        ReflectionTestUtils.setField(emailService, "fromAddress", "notifications@grantora.gov.in");
        ReflectionTestUtils.setField(emailService, "fromName", "Grantora Administration");
        ReflectionTestUtils.setField(emailService, "appUrl", "http://localhost:5173");

        beneficiaryUser = new User();
        ReflectionTestUtils.setField(beneficiaryUser, "id", 1L);
        beneficiaryUser.setFullName("Ramesh Kumar");
        beneficiaryUser.setEmail("ramesh.kumar@example.com");
        beneficiaryUser.setRole(Role.BENEFICIARY);

        beneficiary = new Beneficiary();
        ReflectionTestUtils.setField(beneficiary, "id", 20L);
        beneficiary.setUser(beneficiaryUser);

        scheme = new Scheme();
        ReflectionTestUtils.setField(scheme, "id", 4L);
        scheme.setSchemeName("Aarogya Seva – Rural Healthcare Support Grant");

        application = new GrantApplication();
        ReflectionTestUtils.setField(application, "id", 55L);
        application.setBeneficiary(beneficiary);
        application.setScheme(scheme);
        application.setRequestedAmount(BigDecimal.valueOf(250000));
        application.setSubmittedAt(LocalDateTime.of(2026, 11, 24, 14, 30));
    }

    // 1. APPLICATION SUBMITTED EMAIL TRIGGER
    @Test
    @DisplayName("1. Application Submitted Email Trigger - dispatches email and logs SENT")
    void testApplicationSubmittedEmailTrigger_Success() {
        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.APPLICATION_SUBMITTED, saved.getNotificationType());
        assertEquals("ramesh.kumar@example.com", saved.getRecipientEmail());
        assertEquals(55L, saved.getApplicationId());
        assertTrue(saved.getSubject().contains("Application Submitted Successfully"));
    }

    // 2. CORRECTION REQUIRED EMAIL TRIGGER
    @Test
    @DisplayName("2. Correction Required Email Trigger - dispatches email with officer remarks")
    void testCorrectionRequiredEmailTrigger_Success() {
        emailService.sendCorrectionRequiredEmail(application, "Please re-upload a clear copy of the facility registration certificate.");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.CORRECTION_REQUIRED, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Corrections Requested"));
    }

    // 3. APPLICATION STATUS UPDATE EMAIL TRIGGER (Field -> District & District -> Finance)
    @Test
    @DisplayName("3. Application Status Update Email Trigger - Field Officer verification completed")
    void testApplicationStatusUpdateEmailTrigger_FieldVerification() {
        emailService.sendStatusUpdateEmail(application, VerificationStage.FIELD, "Field verification completed. Advanced to District Review.");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.STATUS_UPDATE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Field Verification Completed"));
    }

    @Test
    @DisplayName("3b. Application Status Update Email Trigger - District Review completed")
    void testApplicationStatusUpdateEmailTrigger_DistrictReview() {
        emailService.sendStatusUpdateEmail(application, VerificationStage.DISTRICT, "District review completed. Advanced to Finance Approval.");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.STATUS_UPDATE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("District Review Completed"));
    }

    // 4. FINAL SANCTION / APPROVAL EMAIL TRIGGER
    @Test
    @DisplayName("4. Final Decision Email Trigger - Approved/Sanctioned with amount")
    void testFinalDecisionEmailTrigger_Approved() {
        emailService.sendFinalDecisionEmail(application, true, BigDecimal.valueOf(250000), "All eligibility conditions satisfied");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.FINAL_DECISION, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Grant Sanctioned"));
    }

    // 5. FINAL REJECTION EMAIL TRIGGER
    @Test
    @DisplayName("5. Final Decision Email Trigger - Rejected with remarks")
    void testFinalDecisionEmailTrigger_Rejected() {
        emailService.sendFinalDecisionEmail(application, false, null, "Applicant annual income exceeds the statutory scheme ceiling");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.FINAL_DECISION, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Application Decision Notification"));
    }

    // 6. DISBURSEMENT RELEASED EMAIL TRIGGER
    @Test
    @DisplayName("6. Disbursement Update Email Trigger - Installment released with UTR")
    void testDisbursementReleasedEmailTrigger_Success() {
        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        ReflectionTestUtils.setField(installment, "id", 10L);
        installment.setPlan(plan);
        installment.setInstallmentNumber(1);
        installment.setTitle("Mobilization Advance");
        installment.setAmount(BigDecimal.valueOf(100000));
        installment.setPaymentReference("PFMS-TXN-2026-99001");
        installment.setActualDisbursementDate(LocalDateTime.now());
        installment.setStatus(InstallmentStatus.DISBURSED);

        emailService.sendDisbursementReleasedEmail(installment);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Installment #1 Disbursed"));
    }

    @Test
    @DisplayName("6b. Disbursement Status Update - Ready for Disbursement")
    void testInstallmentStatusUpdateEmailTrigger_Ready() {
        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        ReflectionTestUtils.setField(installment, "id", 11L);
        installment.setPlan(plan);
        installment.setInstallmentNumber(2);
        installment.setTitle("Equipment Installation");
        installment.setAmount(BigDecimal.valueOf(75000));
        installment.setStatus(InstallmentStatus.READY_FOR_DISBURSEMENT);

        emailService.sendInstallmentStatusUpdateEmail(installment, InstallmentStatus.PLANNED, InstallmentStatus.READY_FOR_DISBURSEMENT, "Milestone verified");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("READY_FOR_DISBURSEMENT"));
    }

    // 7. COMPLIANCE REMINDER AND ACTION REQUIRED EMAIL TRIGGER
    @Test
    @DisplayName("7. Compliance Reminder Email Trigger - Upcoming and Overdue reminders")
    void testComplianceReminderEmailTrigger_Success() {
        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setInstallment(installment);
        milestone.setTitle("Procurement & Site Setup");

        MilestoneCompliance compliance = new MilestoneCompliance();
        ReflectionTestUtils.setField(compliance, "id", 33L);
        compliance.setMilestone(milestone);
        compliance.setRequirementTitle("Equipment Purchase Invoices");
        compliance.setSubmissionDeadline(LocalDate.now().plusDays(3));

        emailService.sendComplianceReminderEmail(compliance, "UPCOMING");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Reminder: Compliance Requirement Due"));
    }

    @Test
    @DisplayName("7b. Compliance Action Required Email Trigger - Correction Required")
    void testComplianceActionRequiredEmailTrigger_Correction() {
        DisbursementPlan plan = new DisbursementPlan();
        plan.setApplication(application);

        DisbursementInstallment installment = new DisbursementInstallment();
        installment.setPlan(plan);

        DisbursementMilestone milestone = new DisbursementMilestone();
        milestone.setInstallment(installment);
        milestone.setTitle("Procurement");

        MilestoneCompliance compliance = new MilestoneCompliance();
        ReflectionTestUtils.setField(compliance, "id", 34L);
        compliance.setMilestone(milestone);
        compliance.setRequirementTitle("Safety Audit Certificate");

        emailService.sendComplianceActionRequiredEmail(compliance, "CORRECTION_REQUIRED", "Certificate has expired; please upload latest valid audit report.");

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.SENT, saved.getStatus());
        assertEquals(BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, saved.getNotificationType());
        assertTrue(saved.getSubject().contains("Action Required: Compliance Evidence"));
    }

    // 8. SMTP UNAVAILABLE SCENARIO
    @Test
    @DisplayName("8. SMTP Unavailable Scenario - handles MailSendException safely without throwing")
    void testSmtpUnavailableScenario_DoesNotThrow() {
        doThrow(new MailSendException("SMTP host unreachable at 587"))
                .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.sendApplicationSubmittedEmail(application));

        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getFailureReason());
        assertTrue(saved.getFailureReason().contains("SMTP host unreachable"));
    }

    // 9. MISSING EMAIL CONFIGURATION SCENARIO
    @Test
    @DisplayName("9. Missing Email Configuration Scenario - mailEnabled=false records PENDING and does not call SMTP")
    void testMissingEmailConfigurationScenario() {
        ReflectionTestUtils.setField(emailService, "mailEnabled", false);

        assertDoesNotThrow(() -> emailService.sendApplicationSubmittedEmail(application));

        verify(mailSender, never()).send(any(MimeMessage.class));
        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());

        BeneficiaryEmailLog saved = captor.getValue();
        assertEquals(EmailDeliveryStatus.PENDING, saved.getStatus());
    }

    // 10. EMAIL FAILURE DOES NOT BREAK APPLICATION WORKFLOW
    @Test
    @DisplayName("10. Email failure should not break application workflow - general exception caught safely")
    void testEmailFailureDoesNotBreakWorkflow() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("MIME factory internal error"));

        assertDoesNotThrow(() -> emailService.sendCorrectionRequiredEmail(application, "Fix errors"));

        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());
        assertEquals(EmailDeliveryStatus.FAILED, captor.getValue().getStatus());
    }

    // 11. DUPLICATE EMAIL PREVENTION (IDEMPOTENCY)
    @Test
    @DisplayName("11. Duplicate email prevention - existing idempotency key suppresses second dispatch")
    void testDuplicateEmailPrevention_Idempotency() {
        when(emailLogRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(emailLogRepository, never()).save(any(BeneficiaryEmailLog.class));
    }

    // 12. BENEFICIARY EMAIL RECIPIENT CORRECTNESS
    @Test
    @DisplayName("12. Beneficiary email recipient correctness - strictly matches registered email")
    void testBeneficiaryEmailRecipientCorrectness() {
        beneficiaryUser.setEmail("distinct.beneficiary@example.org");

        emailService.sendApplicationSubmittedEmail(application);

        ArgumentCaptor<BeneficiaryEmailLog> captor = ArgumentCaptor.forClass(BeneficiaryEmailLog.class);
        verify(emailLogRepository, atLeastOnce()).save(captor.capture());
        assertEquals("distinct.beneficiary@example.org", captor.getValue().getRecipientEmail());
    }

    // 13. ENSURE NO EMAIL SENT TO ADMIN
    @Test
    @DisplayName("13. Ensure no email is sent to Admin - non-beneficiary role strictly blocked")
    void testEnsureNoEmailSentToAdmin() {
        User adminUser = new User();
        adminUser.setEmail("admin@grantora.gov.in");
        adminUser.setRole(Role.ADMIN);

        Beneficiary adminBen = new Beneficiary();
        adminBen.setUser(adminUser);
        application.setBeneficiary(adminBen);

        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(emailLogRepository, never()).save(any(BeneficiaryEmailLog.class));
    }

    // 14. ENSURE NO EMAIL SENT TO FIELD OFFICERS
    @Test
    @DisplayName("14. Ensure no email is sent to Field Officers - role blocked")
    void testEnsureNoEmailSentToFieldOfficers() {
        User officerUser = new User();
        officerUser.setEmail("field.officer@grantora.demo");
        officerUser.setRole(Role.OFFICER);
        officerUser.setOfficerLevel("FIELD");

        Beneficiary officerBen = new Beneficiary();
        officerBen.setUser(officerUser);
        application.setBeneficiary(officerBen);

        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(emailLogRepository, never()).save(any(BeneficiaryEmailLog.class));
    }

    // 15. ENSURE NO EMAIL SENT TO DISTRICT OFFICERS
    @Test
    @DisplayName("15. Ensure no email is sent to District Officers - role blocked")
    void testEnsureNoEmailSentToDistrictOfficers() {
        User officerUser = new User();
        officerUser.setEmail("district.officer@grantora.demo");
        officerUser.setRole(Role.OFFICER);
        officerUser.setOfficerLevel("DISTRICT");

        Beneficiary officerBen = new Beneficiary();
        officerBen.setUser(officerUser);
        application.setBeneficiary(officerBen);

        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(emailLogRepository, never()).save(any(BeneficiaryEmailLog.class));
    }

    // 16. ENSURE NO EMAIL SENT TO FINANCE OFFICERS
    @Test
    @DisplayName("16. Ensure no email is sent to Finance Officers - role blocked")
    void testEnsureNoEmailSentToFinanceOfficers() {
        User officerUser = new User();
        officerUser.setEmail("finance.officer@grantora.demo");
        officerUser.setRole(Role.OFFICER);
        officerUser.setOfficerLevel("FINANCE");

        Beneficiary officerBen = new Beneficiary();
        officerBen.setUser(officerUser);
        application.setBeneficiary(officerBen);

        emailService.sendApplicationSubmittedEmail(application);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(emailLogRepository, never()).save(any(BeneficiaryEmailLog.class));
    }
}
