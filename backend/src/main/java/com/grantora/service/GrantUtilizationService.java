package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GrantUtilizationService {
    private final GrantUtilizationRepository records;
    private final GrantApplicationRepository applications;
    private final DisbursementInstallmentRepository installments;
    private final MilestoneComplianceRepository evidence;
    private final UserRepository users;
    private final WorkflowAutomationService notifications;

    public GrantUtilizationService(GrantUtilizationRepository records, GrantApplicationRepository applications,
            DisbursementInstallmentRepository installments, MilestoneComplianceRepository evidence,
            UserRepository users, WorkflowAutomationService notifications) {
        this.records = records;
        this.applications = applications;
        this.installments = installments;
        this.evidence = evidence;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional
    public GrantUtilizationResponse submit(GrantUtilizationRequest request, String email) {
        return submitWithDocument(request, null, email);
    }

    @Transactional
    public GrantUtilizationResponse submitWithDocument(GrantUtilizationRequest request, MultipartFile file, String email) {
        User beneficiary = user(email);
        if (beneficiary.getRole() != Role.BENEFICIARY) {
            throw denied("Beneficiary access required.");
        }
        GrantApplication app = applications.findById(request.applicationId()).orElseThrow(() -> missing("Application not found."));
        DisbursementInstallment installment = installments.findById(request.installmentId()).orElseThrow(() -> missing("Installment not found."));
        validateOwnershipAndRelease(app, installment, beneficiary);
        validateSupportingEvidence(request.supportingComplianceIds(), installment, file);
        validateAmount(request.reportedAmount(), installment, null);

        GrantUtilizationRecord record = new GrantUtilizationRecord();
        record.setApplication(app);
        record.setInstallment(installment);
        record.setSubmittedBy(beneficiary);
        applyRequest(record, request);
        int cycle = record.getCurrentReviewCycle() + 1;
        record.setCurrentReviewCycle(cycle);
        record.setCurrentReviewStage(VerificationStage.FIELD);
        record.setStatus(GrantUtilizationStatus.FIELD_PENDING);
        record.setSubmittedAt(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            attachDocumentVersion(record, file, beneficiary, cycle);
        }

        history(record, beneficiary, GrantUtilizationStatus.FIELD_PENDING, "Initial utilization report submitted (Cycle #" + cycle + ").");
        GrantUtilizationRecord saved = records.save(record);
        notifyEligibleOfficers(app, VerificationStage.FIELD,
                "utilization_submitted:" + saved.getId() + ":c" + cycle,
                "Stage FIELD expenditure report review required for Application #" + app.getId() + ".");
        return toResponse(saved);
    }

    @Transactional
    public GrantUtilizationResponse replace(Long id, GrantUtilizationRequest request, String email) {
        return replaceWithDocument(id, request, null, email);
    }

    @Transactional
    public GrantUtilizationResponse replaceWithDocument(Long id, GrantUtilizationRequest request, MultipartFile file, String email) {
        User beneficiary = user(email);
        if (beneficiary.getRole() != Role.BENEFICIARY) {
            throw denied("Beneficiary access required.");
        }
        GrantUtilizationRecord record = records.findById(id).orElseThrow(() -> missing("Utilization record not found."));
        if (!Objects.equals(record.getSubmittedBy().getId(), beneficiary.getId())) {
            throw denied("You can update only your own utilization reports.");
        }
        if (record.getStatus() == GrantUtilizationStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Rejected expenditure reports cannot be resubmitted directly.");
        }
        if (record.getStatus() != GrantUtilizationStatus.CORRECTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only reports needing correction can be resubmitted.");
        }
        if (!Objects.equals(record.getApplication().getId(), request.applicationId())
                || !Objects.equals(record.getInstallment().getId(), request.installmentId())) {
            throw bad("Application and installment references cannot change when correcting a report.");
        }
        validateSupportingEvidence(request.supportingComplianceIds(), record.getInstallment(), file);
        validateAmount(request.reportedAmount(), record.getInstallment(), id);

        applyRequest(record, request);
        int nextCycle = record.getCurrentReviewCycle() + 1;
        record.setCurrentReviewCycle(nextCycle);
        record.setCurrentReviewStage(VerificationStage.FIELD);
        record.setStatus(GrantUtilizationStatus.FIELD_PENDING);
        record.setSubmittedAt(LocalDateTime.now());
        record.setVerifiedAt(null);
        record.setVerifiedBy(null);
        record.setReviewRemarks(null);

        if (file != null && !file.isEmpty()) {
            attachDocumentVersion(record, file, beneficiary, nextCycle);
        }

        history(record, beneficiary, GrantUtilizationStatus.FIELD_PENDING, "Corrected utilization report resubmitted (Cycle #" + nextCycle + ").");
        GrantUtilizationRecord saved = records.save(record);
        notifyEligibleOfficers(record.getApplication(), VerificationStage.FIELD,
                "utilization_resubmitted:" + saved.getId() + ":c" + nextCycle,
                "Stage FIELD expenditure report resubmitted for Application #" + record.getApplication().getId() + ".");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GrantUtilizationResponse> mine(String email) {
        User beneficiary = user(email);
        if (beneficiary.getRole() != Role.BENEFICIARY) {
            throw denied("Beneficiary access required.");
        }
        return records.findByApplication_Beneficiary_User_IdOrderBySubmittedAtDesc(beneficiary.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GrantUtilizationResponse> reviewQueue(String email) {
        User actor = user(email);
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.OFFICER) {
            throw denied("Officer or admin access required.");
        }
        if (actor.getRole() == Role.ADMIN) {
            return records.findAllByOrderBySubmittedAtDesc().stream()
                    .filter(r -> isPendingReviewStatus(r.getStatus()))
                    .map(this::toResponse)
                    .toList();
        }
        VerificationStage actorStage = parseOfficerStage(actor);
        if (actorStage == null) {
            return Collections.emptyList();
        }
        return records.findAllByOrderBySubmittedAtDesc().stream()
                .filter(r -> matchesOfficerStageQueue(r, actorStage))
                .filter(r -> isJurisdictionEligible(actor, r.getApplication(), actorStage))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GrantUtilizationResponse review(Long id, GrantUtilizationReviewRequest request, String email) {
        User actor = user(email);
        if (actor.getRole() != Role.OFFICER) {
            throw denied("Only authorized verification officers can review expenditure reports.");
        }
        GrantUtilizationRecord record = records.findById(id).orElseThrow(() -> missing("Utilization record not found."));
        if (!isPendingReviewStatus(record.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending expenditure reports can be reviewed.");
        }
        VerificationStage currentStage = resolveCurrentStage(record);
        if (!isJurisdictionEligible(actor, record.getApplication(), currentStage)) {
            throw denied("You are not authorized for this expenditure review stage or jurisdiction.");
        }

        String decision = request.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "REJECTED", "CORRECTION_REQUIRED").contains(decision)) {
            throw bad("Decision must be APPROVED, REJECTED, or CORRECTION_REQUIRED.");
        }
        if (!decision.equals("APPROVED") && (request.remarks() == null || request.remarks().isBlank())) {
            throw bad("Remarks are required for rejection or correction.");
        }

        int cycle = Math.max(1, record.getCurrentReviewCycle());
        record.setCurrentReviewCycle(cycle);

        boolean duplicateInCycle = record.getStageReviews().stream()
                .anyMatch(sr -> Objects.equals(sr.getReviewCycle(), cycle) && sr.getStage() == currentStage);
        if (duplicateInCycle) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This stage (" + currentStage.name() + ") has already reviewed cycle #" + cycle + ".");
        }

        UtilizationStageReview stageReview = new UtilizationStageReview();
        stageReview.setReviewCycle(cycle);
        stageReview.setStage(currentStage);
        stageReview.setDecision(decision);
        stageReview.setReviewer(actor);
        stageReview.setReviewerOfficerLevel(currentStage.name());
        stageReview.setRemarks(request.remarks());
        stageReview.setDocumentVersionNumber(record.getDocumentVersions().stream()
                .map(UtilizationDocumentVersion::getVersionNumber)
                .max(Integer::compareTo)
                .orElse(null));
        stageReview.setReviewedAt(LocalDateTime.now());
        record.addStageReview(stageReview);

        GrantUtilizationStatus nextStatus;
        if (decision.equals("APPROVED")) {
            if (currentStage == VerificationStage.FIELD) {
                nextStatus = GrantUtilizationStatus.DISTRICT_PENDING;
                record.setCurrentReviewStage(VerificationStage.DISTRICT);
            } else if (currentStage == VerificationStage.DISTRICT) {
                nextStatus = GrantUtilizationStatus.FINANCE_PENDING;
                record.setCurrentReviewStage(VerificationStage.FINANCE);
            } else {
                nextStatus = GrantUtilizationStatus.VERIFIED;
                record.setCurrentReviewStage(null);
            }
        } else {
            nextStatus = decision.equals("REJECTED") ? GrantUtilizationStatus.REJECTED : GrantUtilizationStatus.CORRECTION_REQUIRED;
            record.setCurrentReviewStage(null);
        }

        record.setStatus(nextStatus);
        record.setVerifiedBy(actor);
        record.setVerifiedAt(LocalDateTime.now());
        record.setReviewRemarks(request.remarks());
        history(record, actor, nextStatus, "[" + currentStage.name() + " " + decision + "] " + (request.remarks() != null ? request.remarks() : ""));
        GrantUtilizationRecord saved = records.save(record);

        GrantApplication app = saved.getApplication();
        if (decision.equals("APPROVED") && currentStage != VerificationStage.FINANCE && app != null) {
            VerificationStage nextStage = currentStage == VerificationStage.FIELD ? VerificationStage.DISTRICT : VerificationStage.FINANCE;
            notifyEligibleOfficers(app, nextStage,
                    "utilization_stage:" + saved.getId() + ":" + nextStage.name() + ":c" + cycle,
                    "Stage " + nextStage.name() + " expenditure report review required for Application #" + app.getId() + ".");
        }
        if (app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
            notifications.notify(
                    app.getBeneficiary().getUser(),
                    "utilization_review:" + saved.getId() + ":" + currentStage.name() + ":c" + cycle + ":" + decision,
                    "Your expenditure report #" + saved.getId() + " was " + decision.toLowerCase(Locale.ROOT) + " at " + currentStage.name() + " review.",
                    app.getId());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public byte[] document(Long id, Integer versionNumber, String email) {
        User actor = user(email);
        GrantUtilizationRecord record = records.findById(id).orElseThrow(() -> missing("Utilization record not found."));
        GrantApplication app = record.getApplication();
        boolean isOwner = actor.getRole() == Role.BENEFICIARY
                && app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null
                && Objects.equals(app.getBeneficiary().getUser().getId(), actor.getId());
        boolean isOfficer = actor.getRole() == Role.OFFICER
                && (isAnyStageJurisdictionEligible(actor, app) || assigned(record, actor));
        if (actor.getRole() != Role.ADMIN && !isOwner && !isOfficer) {
            throw denied("You do not have access to this expenditure document.");
        }
        if (record.getDocumentVersions() == null || record.getDocumentVersions().isEmpty()) {
            throw missing("No uploaded supporting document attached to this expenditure report.");
        }
        if (versionNumber != null) {
            return record.getDocumentVersions().stream()
                    .filter(v -> Objects.equals(v.getVersionNumber(), versionNumber))
                    .findFirst()
                    .map(UtilizationDocumentVersion::getDocumentData)
                    .orElseThrow(() -> missing("Document version #" + versionNumber + " not found."));
        }
        return record.getDocumentVersions().get(record.getDocumentVersions().size() - 1).getDocumentData();
    }

    private void attachDocumentVersion(GrantUtilizationRecord record, MultipartFile file, User uploader, int cycle) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("expenditure_doc").replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!Set.of("pdf", "png", "jpg", "jpeg").contains(ext)) {
            throw bad("Supporting document must be PDF, PNG, JPG, or JPEG.");
        }
        if (file.getSize() > 10485760L) {
            throw bad("Supporting document exceeds 10 MB maximum size.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw bad("Could not read uploaded supporting document.");
        }
        UtilizationDocumentVersion v = new UtilizationDocumentVersion();
        v.setVersionNumber(cycle);
        v.setDocumentFileName(filename);
        v.setDocumentContentType(file.getContentType());
        v.setDocumentFileSize(file.getSize());
        v.setDocumentData(bytes);
        v.setUploadedBy(uploader);
        v.setUploadedAt(LocalDateTime.now());
        record.addDocumentVersion(v);
    }

    private void validateOwnershipAndRelease(GrantApplication app, DisbursementInstallment installment, User beneficiary) {
        if (app.getStatus() != GrantApplicationStatus.APPROVED) throw bad("Utilization can only be reported for an approved application.");
        if (app.getBeneficiary() == null || app.getBeneficiary().getUser() == null || !Objects.equals(app.getBeneficiary().getUser().getId(), beneficiary.getId())) throw denied("This application does not belong to you.");
        if (installment.getPlan() == null || !Objects.equals(installment.getPlan().getApplication().getId(), app.getId())) throw bad("Installment does not belong to this application.");
        if (installment.getStatus() != InstallmentStatus.DISBURSED) throw bad("Utilization can be reported only against an installment recorded as released.");
    }

    private void validateSupportingEvidence(List<Long> ids, DisbursementInstallment installment, MultipartFile file) {
        boolean hasFile = file != null && !file.isEmpty();
        boolean hasIds = ids != null && !ids.isEmpty();
        if (!hasFile && !hasIds) {
            throw bad("Attach a supporting document or select at least one existing compliance document reference.");
        }
        if (hasIds) {
            Set<Long> unique = new HashSet<>(ids);
            if (unique.size() != ids.size()) throw bad("Duplicate supporting document references are not allowed.");
            for (Long id : unique) {
                MilestoneCompliance item = evidence.findById(id).orElseThrow(() -> missing("Supporting compliance document not found."));
                if (item.getMilestone() == null || item.getMilestone().getInstallment() == null
                        || !Objects.equals(item.getMilestone().getInstallment().getId(), installment.getId())
                        || item.getDocumentData() == null || item.getDocumentData().length == 0) {
                    throw bad("Each supporting document must be attached to this installment.");
                }
            }
        }
    }

    private void validateAmount(BigDecimal amount, DisbursementInstallment installment, Long currentId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw bad("Reported expenditure must be greater than zero.");
        BigDecimal existing = records.findAllByOrderBySubmittedAtDesc().stream()
                .filter(r -> Objects.equals(r.getInstallment().getId(), installment.getId()) && !Objects.equals(r.getId(), currentId) && r.getStatus() != GrantUtilizationStatus.REJECTED)
                .map(GrantUtilizationRecord::getReportedAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existing.add(amount).compareTo(installment.getAmount()) > 0) throw bad("Reported expenditure cannot exceed the released installment amount.");
    }

    private void applyRequest(GrantUtilizationRecord r, GrantUtilizationRequest req) {
        r.setReportedAmount(req.reportedAmount());
        r.setCategory(req.category().trim());
        r.setDescription(req.description());
        Set<MilestoneCompliance> attached = new LinkedHashSet<>();
        if (req.supportingComplianceIds() != null) {
            for (Long id : req.supportingComplianceIds()) {
                attached.add(evidence.findById(id).orElseThrow(() -> missing("Supporting compliance document not found.")));
            }
        }
        r.setSupportingDocuments(attached);
    }

    private void history(GrantUtilizationRecord r, User actor, GrantUtilizationStatus status, String remarks) {
        UtilizationReviewHistory h = new UtilizationReviewHistory();
        h.setActor(actor);
        h.setStatus(status);
        h.setRemarks(remarks);
        r.addHistory(h);
    }

    private boolean isPendingReviewStatus(GrantUtilizationStatus s) {
        return s == GrantUtilizationStatus.FIELD_PENDING
                || s == GrantUtilizationStatus.DISTRICT_PENDING
                || s == GrantUtilizationStatus.FINANCE_PENDING
                || s == GrantUtilizationStatus.SUBMITTED
                || s == GrantUtilizationStatus.UNDER_REVIEW;
    }

    private VerificationStage resolveCurrentStage(GrantUtilizationRecord r) {
        if (r.getCurrentReviewStage() != null) return r.getCurrentReviewStage();
        if (r.getStatus() == GrantUtilizationStatus.DISTRICT_PENDING) return VerificationStage.DISTRICT;
        if (r.getStatus() == GrantUtilizationStatus.FINANCE_PENDING) return VerificationStage.FINANCE;
        return VerificationStage.FIELD;
    }

    private VerificationStage parseOfficerStage(User officer) {
        if (officer == null || officer.getOfficerLevel() == null) return null;
        try {
            return VerificationStage.valueOf(officer.getOfficerLevel().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean matchesOfficerStageQueue(GrantUtilizationRecord r, VerificationStage actorStage) {
        return switch (actorStage) {
            case FIELD -> r.getStatus() == GrantUtilizationStatus.FIELD_PENDING
                    || ((r.getStatus() == GrantUtilizationStatus.SUBMITTED || r.getStatus() == GrantUtilizationStatus.UNDER_REVIEW)
                            && (r.getCurrentReviewStage() == null || r.getCurrentReviewStage() == VerificationStage.FIELD));
            case DISTRICT -> r.getStatus() == GrantUtilizationStatus.DISTRICT_PENDING;
            case FINANCE -> r.getStatus() == GrantUtilizationStatus.FINANCE_PENDING;
        };
    }

    private boolean isAnyStageJurisdictionEligible(User officer, GrantApplication app) {
        VerificationStage stage = parseOfficerStage(officer);
        return stage != null && isJurisdictionEligible(officer, app, stage);
    }

    private boolean isJurisdictionEligible(User officer, GrantApplication app, VerificationStage stage) {
        if (officer == null || officer.getRole() != Role.OFFICER || !Boolean.TRUE.equals(officer.getActive())) return false;
        if (stage == null || officer.getOfficerLevel() == null || !stage.name().equalsIgnoreCase(officer.getOfficerLevel().trim())) return false;
        if (app == null || app.getBeneficiary() == null) return false;
        if (app.getBeneficiary().getUser() != null && Objects.equals(app.getBeneficiary().getUser().getId(), officer.getId())) return false;
        Beneficiary ben = app.getBeneficiary();
        if (!sameNormalized(officer.getJurisdictionState(), ben.getState())) return false;
        if (stage != VerificationStage.FINANCE && !sameNormalized(officer.getJurisdictionDistrict(), ben.getDistrict())) return false;
        return true;
    }

    private boolean sameNormalized(String a, String b) {
        if (a == null || b == null) return false;
        String na = a.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
        String nb = b.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT);
        return !na.isEmpty() && na.equals(nb);
    }

    private void notifyEligibleOfficers(GrantApplication app, VerificationStage stage, String keyPrefix, String message) {
        for (User u : users.findAll()) {
            if (isJurisdictionEligible(u, app, stage)) {
                notifications.notify(u, keyPrefix + ":u" + u.getId(), message, app.getId());
            }
        }
    }

    private boolean assigned(GrantUtilizationRecord r, User actor) {
        return r.getApplication() != null && r.getApplication().getAssignedOfficer() != null
                && Objects.equals(r.getApplication().getAssignedOfficer().getId(), actor.getId());
    }

    private User user(String email) {
        return users.findByEmail(email).orElseThrow(() -> missing("User not found."));
    }

    private GrantUtilizationResponse toResponse(GrantUtilizationRecord r) {
        GrantApplication app = r.getApplication();
        DisbursementInstallment installment = r.getInstallment();
        Scheme scheme = app != null ? app.getScheme() : null;
        Beneficiary ben = app != null ? app.getBeneficiary() : null;
        User benUser = ben != null ? ben.getUser() : null;
        User subUser = r.getSubmittedBy();

        return new GrantUtilizationResponse(
                r.getId(),
                app != null ? app.getId() : null,
                installment != null ? installment.getId() : null,
                installment != null ? installment.getInstallmentNumber() : null,
                scheme != null ? scheme.getSchemeName() : "Unknown Scheme",
                benUser != null ? benUser.getFullName() : (ben != null ? "Beneficiary #" + ben.getId() : "Beneficiary"),
                installment != null ? installment.getAmount() : null,
                r.getReportedAmount(),
                r.getCategory(),
                r.getDescription(),
                r.getSupportingDocuments() == null ? Collections.emptyList() : r.getSupportingDocuments().stream().map(MilestoneCompliance::getId).filter(Objects::nonNull).toList(),
                r.getSupportingDocuments() == null ? Collections.emptyList() : r.getSupportingDocuments().stream().map(MilestoneCompliance::getDocumentFileName).filter(Objects::nonNull).toList(),
                r.getStatus(),
                r.getSubmittedAt(),
                subUser != null ? subUser.getFullName() : "Beneficiary",
                r.getVerifiedAt(),
                r.getVerifiedBy() == null ? null : r.getVerifiedBy().getFullName(),
                r.getReviewRemarks(),
                r.getHistory() == null ? Collections.emptyList() : r.getHistory().stream().map(h -> new UtilizationHistoryResponse(h.getStatus(), h.getActor() == null ? null : h.getActor().getFullName(), h.getRemarks(), h.getOccurredAt())).toList(),
                r.getCurrentReviewCycle(),
                r.getCurrentReviewStage() != null ? r.getCurrentReviewStage().name() : null,
                r.getStageReviews() == null ? Collections.emptyList() : r.getStageReviews().stream()
                        .map(sr -> new ComplianceStageReviewResponse(
                                sr.getReviewCycle(),
                                sr.getStage() != null ? sr.getStage().name() : null,
                                sr.getDecision(),
                                sr.getReviewer() != null ? sr.getReviewer().getId() : null,
                                sr.getReviewer() != null ? sr.getReviewer().getFullName() : null,
                                sr.getReviewerOfficerLevel(),
                                sr.getRemarks(),
                                null,
                                sr.getDocumentVersionNumber(),
                                sr.getReviewedAt()))
                        .toList(),
                r.getDocumentVersions() == null ? Collections.emptyList() : r.getDocumentVersions().stream()
                        .map(dv -> new ComplianceDocumentVersionResponse(
                                dv.getVersionNumber(),
                                dv.getDocumentFileName(),
                                dv.getDocumentContentType(),
                                dv.getDocumentFileSize(),
                                null,
                                dv.getUploadedBy() != null ? dv.getUploadedBy().getFullName() : null,
                                dv.getUploadedAt()))
                        .toList()
        );
    }

    private ResponseStatusException missing(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
    private ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
    private ResponseStatusException denied(String m) { return new ResponseStatusException(HttpStatus.FORBIDDEN, m); }
}
