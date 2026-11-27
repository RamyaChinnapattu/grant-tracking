package com.grantora.service;

import com.grantora.entity.*;
import com.grantora.repository.BeneficiaryEmailLogRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class BeneficiaryEmailService {

    private static final Logger log = LoggerFactory.getLogger(BeneficiaryEmailService.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final BeneficiaryEmailLogRepository emailLogRepository;

    @Value("${grantora.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${grantora.mail.from-address:notifications@grantora.gov.in}")
    private String fromAddress;

    @Value("${grantora.mail.from-name:Grantora Administration}")
    private String fromName;

    @Value("${grantora.mail.app-url:http://localhost:5173}")
    private String appUrl;

    public BeneficiaryEmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            BeneficiaryEmailLogRepository emailLogRepository) {
        this.mailSenderProvider = mailSenderProvider;
        this.emailLogRepository = emailLogRepository;
    }

    // ==========================================
    // 1. APPLICATION SUBMITTED EMAIL
    // ==========================================

    @Transactional
    public void sendApplicationSubmittedEmail(GrantApplication application) {
        if (application == null || application.getBeneficiary() == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();
        String submittedDate = application.getSubmittedAt() != null
                ? application.getSubmittedAt().format(DATE_TIME_FORMAT)
                : LocalDateTime.now().format(DATE_TIME_FORMAT);

        String subject = String.format("Grantora: Application Submitted Successfully - %s (%s)", schemeName, appRef);
        String idempotencyKey = "app_submitted:" + application.getId() + ":" + (application.getSubmittedAt() != null ? application.getSubmittedAt().toLocalDate() : LocalDate.now());

        String ctaUrl = appUrl + "/beneficiary/applications";
        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                "Application Submission Confirmation",
                "#0284c7",
                "Your grant application has been submitted successfully and has entered the official verification pipeline.",
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Submission Status", "SUBMITTED"},
                        {"Submission Date & Time", submittedDate},
                        {"Requested Grant Amount", application.getRequestedAmount() != null ? "₹" + application.getRequestedAmount().toPlainString() : "N/A"}
                },
                "Next Steps: Your application is currently assigned for initial Field Verification. You can track its live progress anytime from your Grantora Beneficiary portal.",
                "Track Your Application",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.APPLICATION_SUBMITTED, subject, htmlBody, idempotencyKey);
    }

    // ==========================================
    // 2. CORRECTION REQUIRED EMAIL
    // ==========================================

    @Transactional
    public void sendCorrectionRequiredEmail(GrantApplication application, String remarks) {
        if (application == null || application.getBeneficiary() == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();
        String officerRemarks = remarks != null && !remarks.isBlank() ? remarks.trim() : "Please review and upload corrected documents as required.";

        String subject = String.format("Action Required: Corrections Requested on Application %s", appRef);
        String idempotencyKey = "correction:" + application.getId() + ":" + System.currentTimeMillis();

        String ctaUrl = appUrl + "/beneficiary/applications";
        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                "Action Required: Application Corrections Needed",
                "#d97706",
                "An authorized reviewing officer has requested corrections or additional information on your grant application.",
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Current Status", "NEEDS_CORRECTION"},
                        {"Officer Remarks", officerRemarks}
                },
                "Instructions: Please log in to your Grantora account, update the required details or upload the requested supporting documents, and click 'Resubmit Application'.",
                "Submit Corrections",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.CORRECTION_REQUIRED, subject, htmlBody, idempotencyKey);
    }

    // ==========================================
    // 3. APPLICATION STATUS UPDATE EMAIL
    // ==========================================

    @Transactional
    public void sendStatusUpdateEmail(GrantApplication application, VerificationStage stage, String stageSummary) {
        if (application == null || application.getBeneficiary() == null || stage == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();

        String stageLabel = switch (stage) {
            case FIELD -> "Field Verification Completed";
            case DISTRICT -> "District Review Completed";
            case FINANCE -> "Financial Verification Completed";
        };

        String nextStep = switch (stage) {
            case FIELD -> "Your application has successfully passed field inspection and has advanced to District Review.";
            case DISTRICT -> "Your application has received District Officer recommendation and has advanced to Finance Officer Review.";
            case FINANCE -> "Financial review is complete and the application is queued for final sanction.";
        };

        String subject = String.format("Grantora: Status Update - %s for %s", stageLabel, appRef);
        String idempotencyKey = "status_update:" + application.getId() + ":" + stage.name();

        String ctaUrl = appUrl + "/beneficiary/applications";
        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                "Application Progress Update",
                "#2563eb",
                stageSummary != null ? stageSummary : nextStep,
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Verification Stage", stageLabel},
                        {"Next Phase", nextStep}
                },
                "You will receive further updates as your application progresses through subsequent verification stages.",
                "Track Status",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.STATUS_UPDATE, subject, htmlBody, idempotencyKey);
    }

    // ==========================================
    // 4. FINAL DECISION EMAIL (APPROVED / REJECTED)
    // ==========================================

    @Transactional
    public void sendFinalDecisionEmail(GrantApplication application, boolean approved, BigDecimal sanctionedAmount, String remarks) {
        if (application == null || application.getBeneficiary() == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();

        if (approved) {
            String subject = String.format("Congratulations! Grant Sanctioned - %s (%s)", schemeName, appRef);
            String idempotencyKey = "final_decision_approved:" + application.getId();
            String amountFormatted = sanctionedAmount != null ? "₹" + sanctionedAmount.toPlainString() : "Approved Grant Amount";

            String ctaUrl = appUrl + "/beneficiary/my-grant";
            String htmlBody = buildHtmlEmail(
                    beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                    "Official Sanction: Application Approved",
                    "#16a34a",
                    "We are pleased to inform you that your grant application has successfully passed all verification levels and has been officially sanctioned.",
                    new String[][]{
                            {"Scheme Name", schemeName},
                            {"Application Reference", appRef},
                            {"Sanction Status", "APPROVED / SANCTIONED"},
                            {"Sanctioned Grant Amount", amountFormatted},
                            {"Approval Date", LocalDate.now().format(DATE_FORMAT)}
                    },
                    "Next Steps: A staged disbursement plan is configured for your sanctioned grant. Installments will be released directly to your verified bank account upon satisfactory completion of milestone conditions and compliance proofs.",
                    "View Sanction & Disbursement Schedule",
                    ctaUrl
            );

            dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.FINAL_DECISION, subject, htmlBody, idempotencyKey);
        } else {
            String subject = String.format("Grantora: Application Decision Notification - %s", appRef);
            String idempotencyKey = "final_decision_rejected:" + application.getId();
            String rejectionReason = remarks != null && !remarks.isBlank() ? remarks.trim() : "Application did not satisfy the scheme eligibility or verification requirements.";

            String ctaUrl = appUrl + "/beneficiary/applications";
            String htmlBody = buildHtmlEmail(
                    beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                    "Application Decision Notification",
                    "#dc2626",
                    "We regret to inform you that after formal evaluation, your grant application could not be approved.",
                    new String[][]{
                            {"Scheme Name", schemeName},
                            {"Application Reference", appRef},
                            {"Decision Status", "REJECTED"},
                            {"Evaluation Remarks", rejectionReason}
                    },
                    "You may log in to Grantora to view the detailed evaluation notes. If eligible, you may explore and apply for alternative government grant schemes.",
                    "View Application Details",
                    ctaUrl
            );

            dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.FINAL_DECISION, subject, htmlBody, idempotencyKey);
        }
    }

    // ==========================================
    // 5. DISBURSEMENT & COMPLIANCE EMAIL
    // ==========================================

    @Transactional
    public void sendDisbursementReleasedEmail(DisbursementInstallment installment) {
        if (installment == null || installment.getPlan() == null || installment.getPlan().getApplication() == null) {
            return;
        }

        GrantApplication application = installment.getPlan().getApplication();
        Beneficiary beneficiary = application.getBeneficiary();
        if (beneficiary == null) {
            return;
        }

        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();
        String amountFormatted = installment.getAmount() != null ? "₹" + installment.getAmount().toPlainString() : "N/A";
        String utr = installment.getPaymentReference() != null ? installment.getPaymentReference() : "Direct Bank Transfer";
        String disbDate = installment.getActualDisbursementDate() != null
                ? installment.getActualDisbursementDate().format(DATE_TIME_FORMAT)
                : LocalDateTime.now().format(DATE_TIME_FORMAT);

        String subject = String.format("Grantora: Installment #%d Disbursed (₹%s) - %s",
                installment.getInstallmentNumber(), installment.getAmount() != null ? installment.getAmount().toPlainString() : "0", appRef);
        String idempotencyKey = "disb_released:" + installment.getId();

        String ctaUrl = appUrl + "/beneficiary/my-grant?tab=schedule";
        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                "Grant Disbursement Authorized",
                "#16a34a",
                String.format("Payment release of Installment #%d for your grant has been successfully recorded.", installment.getInstallmentNumber()),
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Installment Stage", "Stage #" + installment.getInstallmentNumber() + " (" + installment.getTitle() + ")"},
                        {"Disbursed Amount", amountFormatted},
                        {"Transaction Reference / UTR", utr},
                        {"Disbursement Date", disbDate}
                },
                "Beneficiary Obligation: Please log in to your portal to acknowledge the fund release and report expenditure utilization records along with supporting invoices/receipts.",
                "View Disbursement & Log Expenditure",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, subject, htmlBody, idempotencyKey);
    }

    @Transactional
    public void sendInstallmentStatusUpdateEmail(DisbursementInstallment installment, InstallmentStatus oldStatus, InstallmentStatus newStatus, String remarks) {
        if (installment == null || installment.getPlan() == null || installment.getPlan().getApplication() == null) {
            return;
        }

        // Only send for important statuses
        if (newStatus != InstallmentStatus.READY_FOR_DISBURSEMENT && newStatus != InstallmentStatus.ON_HOLD) {
            return;
        }

        GrantApplication application = installment.getPlan().getApplication();
        Beneficiary beneficiary = application.getBeneficiary();
        if (beneficiary == null) {
            return;
        }

        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();
        String badgeColor = newStatus == InstallmentStatus.READY_FOR_DISBURSEMENT ? "#0284c7" : "#ea580c";
        String title = newStatus == InstallmentStatus.READY_FOR_DISBURSEMENT
                ? "Installment Ready for Disbursement"
                : "Installment Status: On Hold";
        String statusNote = newStatus == InstallmentStatus.READY_FOR_DISBURSEMENT
                ? "Milestone conditions have been fulfilled. This installment is now queued for Finance Officer payment authorization."
                : (remarks != null && !remarks.isBlank() ? remarks : "This installment has been placed on hold pending compliance review.");

        String subject = String.format("Grantora: Installment #%d %s - %s",
                installment.getInstallmentNumber(), newStatus.name(), appRef);
        String idempotencyKey = "installment_status:" + installment.getId() + ":" + newStatus.name();

        String ctaUrl = appUrl + "/beneficiary/my-grant?tab=schedule";
        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                title,
                badgeColor,
                statusNote,
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Installment Stage", "Stage #" + installment.getInstallmentNumber() + " (" + installment.getTitle() + ")"},
                        {"Scheduled Amount", installment.getAmount() != null ? "₹" + installment.getAmount().toPlainString() : "N/A"},
                        {"New Status", newStatus.name()}
                },
                "Log in to your Grantora portal for complete installment details and progress monitoring.",
                "View My Grant",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, subject, htmlBody, idempotencyKey);
    }

    @Transactional
    public void sendComplianceReminderEmail(MilestoneCompliance compliance, String eventType) {
        if (compliance == null || compliance.getMilestone() == null
                || compliance.getMilestone().getInstallment() == null
                || compliance.getMilestone().getInstallment().getPlan() == null) {
            return;
        }

        GrantApplication application = compliance.getMilestone().getInstallment().getPlan().getApplication();
        if (application == null || application.getBeneficiary() == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();
        String deadlineFormatted = compliance.getSubmissionDeadline() != null
                ? compliance.getSubmissionDeadline().format(DATE_FORMAT)
                : "Upcoming Deadline";

        boolean isOverdue = "OVERDUE".equalsIgnoreCase(eventType);
        String subject = isOverdue
                ? String.format("URGENT: Compliance Requirement Overdue - %s (%s)", compliance.getRequirementTitle(), appRef)
                : String.format("Reminder: Compliance Requirement Due on %s - %s", deadlineFormatted, appRef);

        String badgeColor = isOverdue ? "#dc2626" : "#d97706";
        String title = isOverdue ? "Mandatory Compliance Overdue" : "Compliance Milestone Reminder";
        String summary = isOverdue
                ? "A mandatory compliance requirement has exceeded its submission deadline. Non-compliance may temporarily hold subsequent grant disbursements."
                : "This is a reminder that supporting milestone documentation is due for submission.";

        String idempotencyKey = "compliance_reminder:" + compliance.getId() + ":" + eventType + ":" + LocalDate.now();
        String ctaUrl = appUrl + "/beneficiary/my-grant?tab=documents";

        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                title,
                badgeColor,
                summary,
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Milestone", compliance.getMilestone().getTitle()},
                        {"Requirement Title", compliance.getRequirementTitle()},
                        {"Submission Deadline", deadlineFormatted},
                        {"Current Status", isOverdue ? "OVERDUE" : "PENDING SUBMISSION"}
                },
                "Action Required: Please log in to your Beneficiary workspace and upload the required proof/certificate to ensure uninterrupted fund release.",
                "Upload Compliance Evidence",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, subject, htmlBody, idempotencyKey);
    }

    @Transactional
    public void sendComplianceActionRequiredEmail(MilestoneCompliance compliance, String decision, String remarks) {
        if (compliance == null || compliance.getMilestone() == null
                || compliance.getMilestone().getInstallment() == null
                || compliance.getMilestone().getInstallment().getPlan() == null) {
            return;
        }

        GrantApplication application = compliance.getMilestone().getInstallment().getPlan().getApplication();
        if (application == null || application.getBeneficiary() == null) {
            return;
        }

        Beneficiary beneficiary = application.getBeneficiary();
        String schemeName = application.getScheme() != null ? application.getScheme().getSchemeName() : "Grant Scheme";
        String appRef = "APP-" + application.getId();

        boolean isRejected = "REJECTED".equalsIgnoreCase(decision);
        String subject = String.format("Action Required: Compliance Evidence %s - %s",
                isRejected ? "Rejected" : "Correction Required", appRef);
        String title = isRejected ? "Compliance Evidence Rejected" : "Compliance Correction Required";
        String badgeColor = isRejected ? "#dc2626" : "#ea580c";
        String officerRemarks = remarks != null && !remarks.isBlank() ? remarks.trim() : "Uploaded evidence does not meet configured requirements.";

        String idempotencyKey = "compliance_action:" + compliance.getId() + ":" + decision + ":" + System.currentTimeMillis();
        String ctaUrl = appUrl + "/beneficiary/my-grant?tab=documents";

        String htmlBody = buildHtmlEmail(
                beneficiary.getUser() != null ? beneficiary.getUser().getFullName() : "Beneficiary",
                title,
                badgeColor,
                "An officer has reviewed your submitted compliance document and noted that action is required.",
                new String[][]{
                        {"Scheme Name", schemeName},
                        {"Application Reference", appRef},
                        {"Milestone", compliance.getMilestone().getTitle()},
                        {"Requirement", compliance.getRequirementTitle()},
                        {"Review Decision", decision},
                        {"Reviewer Remarks", officerRemarks}
                },
                "Action Required: Please upload a revised, valid document resolving the reviewer's remarks.",
                "Submit Revised Evidence",
                ctaUrl
        );

        dispatchEmail(beneficiary, application.getId(), BeneficiaryEmailType.DISBURSEMENT_COMPLIANCE, subject, htmlBody, idempotencyKey);
    }

    // ==========================================
    // CORE DISPATCH & SECURITY ENFORCEMENT
    // ==========================================

    private void dispatchEmail(
            Beneficiary beneficiary,
            Long applicationId,
            BeneficiaryEmailType type,
            String subject,
            String htmlBody,
            String idempotencyKey) {

        if (beneficiary == null) {
            log.warn("Cannot send email: Beneficiary is null.");
            return;
        }

        User user = beneficiary.getUser();
        if (user == null) {
            log.warn("Cannot send email: Beneficiary #{} has no attached User entity.", beneficiary.getId());
            return;
        }

        // STRICT SECURITY RULE: Email notifications are exclusively for BENEFICIARY role!
        // Admin, Field Officers, District Officers, and Finance Officers MUST NEVER receive emails.
        if (user.getRole() != Role.BENEFICIARY) {
            log.warn("Blocked attempt to send beneficiary email to non-beneficiary role: {} ({})",
                    user.getRole(), user.getEmail());
            return;
        }

        String recipientEmail = user.getEmail();
        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("Cannot send email: Beneficiary #{} User has no registered email.", beneficiary.getId());
            return;
        }

        // Duplicate prevention via idempotency key
        if (emailLogRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate email suppressed for key: {} to {}", idempotencyKey, recipientEmail);
            return;
        }

        BeneficiaryEmailLog logEntry = new BeneficiaryEmailLog(
                beneficiary.getId(),
                applicationId,
                recipientEmail.trim(),
                type,
                subject,
                idempotencyKey
        );
        logEntry = emailLogRepository.save(logEntry);

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!mailEnabled || mailSender == null) {
            log.info("Email delivery disabled or JavaMailSender unavailable (mailEnabled={}, senderAvailable={}). Recorded email log #{} with status PENDING.",
                    mailEnabled, mailSender != null, logEntry.getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setTo(recipientEmail.trim());
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            logEntry.markSent();
            emailLogRepository.save(logEntry);
            log.info("Beneficiary email delivered to {} [Type: {}, Ref: APP-{}]", recipientEmail, type, applicationId);

        } catch (Exception ex) {
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            log.error("Failed to send email to {} [Key: {}]: {}", recipientEmail, idempotencyKey, errorMsg);
            logEntry.markFailed(errorMsg);
            emailLogRepository.save(logEntry);
            // Non-breaking exception boundary: workflow transaction remains intact
        }
    }

    // ==========================================
    // RESPONSIVE HTML EMAIL TEMPLATE BUILDER
    // ==========================================

    private String buildHtmlEmail(
            String beneficiaryName,
            String badgeTitle,
            String badgeColor,
            String primaryMessage,
            String[][] detailRows,
            String nextStepsNote,
            String ctaText,
            String ctaUrl) {

        StringBuilder rowsHtml = new StringBuilder();
        if (detailRows != null) {
            for (String[] row : detailRows) {
                if (row.length >= 2) {
                    rowsHtml.append("<tr>")
                            .append("<td style=\"padding: 10px 14px; color: #64748b; font-size: 13px; font-weight: 500; border-bottom: 1px solid #f1f5f9; width: 40%;\">")
                            .append(escapeHtml(row[0]))
                            .append("</td>")
                            .append("<td style=\"padding: 10px 14px; color: #0f172a; font-size: 13px; font-weight: 600; border-bottom: 1px solid #f1f5f9;\">")
                            .append(escapeHtml(row[1]))
                            .append("</td>")
                            .append("</tr>");
                }
            }
        }

        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Grantora Notification</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f8fafc; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;">
                    <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f8fafc; padding: 30px 10px;">
                        <tr>
                            <td align="center">
                                <table width="100%" border="0" cellspacing="0" cellpadding="0" style="max-width: 600px; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08); border: 1px solid #e2e8f0;">
                                    <!-- HEADER -->
                                    <tr>
                                        <td style="background: linear-gradient(135deg, #0f172a 0%, #1e3a8a 100%); padding: 28px 32px; text-align: left;">
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td>
                                                        <h1 style="margin: 0; color: #ffffff; font-size: 22px; font-weight: 800; letter-spacing: 0.5px;">GRANTORA</h1>
                                                        <p style="margin: 4px 0 0; color: #93c5fd; font-size: 12px; font-weight: 400; letter-spacing: 0.2px;">Digital Subsidy &amp; Grant Administration Platform</p>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <!-- MAIN BODY -->
                                    <tr>
                                        <td style="padding: 32px;">
                                            <!-- STATUS BADGE -->
                                            <div style="display: inline-block; background-color: {BADGE_COLOR}; color: #ffffff; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.6px; padding: 5px 12px; border-radius: 20px; margin-bottom: 20px;">
                                                {BADGE_TITLE}
                                            </div>

                                            <h2 style="margin: 0 0 12px; color: #0f172a; font-size: 18px; font-weight: 700;">Dear {BENEFICIARY_NAME},</h2>
                                            <p style="margin: 0 0 24px; color: #334155; font-size: 14px; line-height: 1.6;">
                                                {PRIMARY_MESSAGE}
                                            </p>

                                            <!-- DETAILS TABLE -->
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0" style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 24px; border-collapse: collapse;">
                                                {ROWS_HTML}
                                            </table>

                                            <!-- NOTE -->
                                            <div style="background-color: #f1f5f9; border-left: 4px solid #3b82f6; padding: 14px 16px; border-radius: 4px; margin-bottom: 28px;">
                                                <p style="margin: 0; color: #334155; font-size: 13px; line-height: 1.5;">
                                                    {NEXT_STEPS_NOTE}
                                                </p>
                                            </div>

                                            <!-- CTA BUTTON -->
                                            <table border="0" cellspacing="0" cellpadding="0" style="margin-top: 10px;">
                                                <tr>
                                                    <td align="center" style="border-radius: 6px; background-color: #2563eb;">
                                                        <a href="{CTA_URL}" target="_blank" style="display: inline-block; padding: 12px 28px; font-size: 14px; font-weight: 600; color: #ffffff; text-decoration: none; border-radius: 6px;">
                                                            {CTA_TEXT} &rarr;
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <!-- FOOTER -->
                                    <tr>
                                        <td style="background-color: #f8fafc; border-top: 1px solid #e2e8f0; padding: 24px 32px; text-align: center;">
                                            <p style="margin: 0 0 8px; color: #64748b; font-size: 12px;">
                                                This is an automated notification from the <strong>Grantora Platform</strong>. Please do not reply directly to this email.
                                            </p>
                                            <p style="margin: 0; color: #94a3b8; font-size: 11px;">
                                                Government of India &bull; Direct Subsidy &amp; Grant Tracking &bull; All Rights Reserved
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """;

        return template
                .replace("{BADGE_COLOR}", badgeColor != null ? badgeColor : "#2563eb")
                .replace("{BADGE_TITLE}", escapeHtml(badgeTitle))
                .replace("{BENEFICIARY_NAME}", escapeHtml(beneficiaryName))
                .replace("{PRIMARY_MESSAGE}", escapeHtml(primaryMessage))
                .replace("{ROWS_HTML}", rowsHtml.toString())
                .replace("{NEXT_STEPS_NOTE}", escapeHtml(nextStepsNote))
                .replace("{CTA_URL}", escapeHtml(ctaUrl))
                .replace("{CTA_TEXT}", escapeHtml(ctaText));
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
