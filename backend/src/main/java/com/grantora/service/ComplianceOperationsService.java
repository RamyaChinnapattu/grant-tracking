package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ComplianceOperationsService {
    private final ComplianceRequirementRepository requirements;
    private final MilestoneComplianceRepository compliances;
    private final ComplianceReminderHistoryRepository reminders;
    private final ComplianceNonComplianceRepository nonCompliances;
    private final DisbursementMilestoneRepository milestones;
    private final DisbursementPlanRepository plans;
    private final SchemeRepository schemes;
    private final GrantApplicationRepository applications;
    private final UserRepository users;
    private final WorkflowAutomationService notifications;
    private final DisbursementReminderConfigRepository reminderConfig;

    public ComplianceOperationsService(ComplianceRequirementRepository requirements, MilestoneComplianceRepository compliances,
            ComplianceReminderHistoryRepository reminders, ComplianceNonComplianceRepository nonCompliances,
            DisbursementMilestoneRepository milestones, DisbursementPlanRepository plans, SchemeRepository schemes,
            GrantApplicationRepository applications, UserRepository users, WorkflowAutomationService notifications,
            DisbursementReminderConfigRepository reminderConfig) {
        this.requirements=requirements; this.compliances=compliances; this.reminders=reminders; this.nonCompliances=nonCompliances;
        this.milestones=milestones; this.plans=plans; this.schemes=schemes; this.applications=applications; this.users=users; this.notifications=notifications;
        this.reminderConfig=reminderConfig;
    }

    @Transactional
    public ComplianceRequirementResponse createRequirement(ComplianceRequirementRequest req, String email) {
        User actor=user(email, Role.ADMIN);
        Scheme scheme=schemes.findById(req.schemeId()).orElseThrow(()->notFound("Scheme not found."));
        ComplianceRequirement requirement=new ComplianceRequirement();
        requirement.setScheme(scheme);
        requirement.setTitle(req.title().trim()); requirement.setDescription(req.description()); requirement.setRequired(req.required());
        requirement.setSubmissionDeadline(req.submissionDeadline()); requirement.setAllowedFormats(normalizeFormats(req.allowedFormats()));
        if(req.maxFileSizeBytes()==null || req.maxFileSizeBytes()<1024 || req.maxFileSizeBytes()>10485760L) throw bad("Maximum file size must be between 1 KB (1024 bytes) and 10 MB (10485760 bytes).");
        requirement.setMaxFileSizeBytes(req.maxFileSizeBytes()); requirement.setCreatedBy(actor);
        if(req.applicationId()!=null){
            GrantApplication app=applications.findById(req.applicationId()).orElseThrow(()->notFound("Application not found."));
            if(app.getScheme() == null || !Objects.equals(app.getScheme().getId(),scheme.getId())) throw bad("Application does not belong to the selected scheme.");
            if(app.getStatus()!=GrantApplicationStatus.APPROVED) throw bad("Compliance requirements can only be scoped to an approved application.");
            requirement.setApplication(app);
        }
        if(req.targetStage()!=null && (req.targetStage()<1 || req.targetStage()>3)) {
            throw bad("Target stage must be 1, 2, or 3.");
        }
        if(req.milestoneId()!=null){
            DisbursementMilestone milestone=milestones.findById(req.milestoneId()).orElseThrow(()->notFound("Milestone not found."));
            Long mSchemeId = milestone.getInstallment() != null && milestone.getInstallment().getPlan() != null && milestone.getInstallment().getPlan().getScheme() != null ? milestone.getInstallment().getPlan().getScheme().getId() : null;
            if(!Objects.equals(mSchemeId,scheme.getId())) throw bad("Milestone does not belong to the selected scheme.");
            Long mAppId = milestone.getInstallment() != null && milestone.getInstallment().getPlan() != null && milestone.getInstallment().getPlan().getApplication() != null ? milestone.getInstallment().getPlan().getApplication().getId() : null;
            if(req.applicationId()!=null && !Objects.equals(mAppId,req.applicationId())) throw bad("Milestone does not belong to the selected application.");
            Integer mStage = milestone.getInstallment() != null ? milestone.getInstallment().getInstallmentNumber() : null;
            if(req.targetStage()!=null && mStage!=null && !Objects.equals(req.targetStage(), mStage)) {
                throw bad("Selected target stage does not match the milestone's installment stage.");
            }
            requirement.setMilestone(milestone);
            requirement.setTargetStage(req.targetStage()!=null ? req.targetStage() : mStage);
        } else {
            if(req.targetStage()==null || req.targetStage()<1 || req.targetStage()>3) {
                throw bad("Target stage (Stage 1, Stage 2, or Stage 3) is required for scheme compliance requirements.");
            }
            requirement.setTargetStage(req.targetStage());
        }
        boolean duplicate=requirements.findByActiveTrue().stream().anyMatch(existing ->
                Objects.equals(existing.getScheme() != null ? existing.getScheme().getId() : null, scheme.getId())
                && Objects.equals(existing.getMilestone()==null?null:existing.getMilestone().getId(),req.milestoneId())
                && Objects.equals(existing.getApplication()==null?null:existing.getApplication().getId(),req.applicationId())
                && Objects.equals(existing.getTargetStage(), requirement.getTargetStage())
                && existing.getTitle() != null && existing.getTitle().equalsIgnoreCase(req.title().trim()));
        if(duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT,"An active requirement with this title already exists for this scope.");
        ComplianceRequirement saved=requirements.save(requirement);
        materialize(saved);
        return toRequirement(saved);
    }

    @Transactional(readOnly=true)
    public List<ComplianceRequirementResponse> getRequirements(String email) {
        user(email, Role.ADMIN);
        return requirements.findAllByOrderByCreatedAtDesc().stream().map(this::toRequirement).toList();
    }

    @Transactional
    public ComplianceRequirementResponse deactivateRequirement(Long id,String email){
        user(email,Role.ADMIN);
        ComplianceRequirement item=requirements.findById(id).orElseThrow(()->notFound("Compliance requirement not found."));
        item.setActive(false);
        ComplianceRequirement saved = requirements.save(item);
        Set<DisbursementMilestone> affectedMilestones = new LinkedHashSet<>();
        compliances.findAll().stream()
                .filter(c -> c.getRequirement() != null && Objects.equals(c.getRequirement().getId(), saved.getId()))
                .map(MilestoneCompliance::getMilestone)
                .filter(Objects::nonNull)
                .forEach(affectedMilestones::add);
        if (saved.getTargetStage() != null && saved.getScheme() != null) {
            for (DisbursementPlan p : plans.findAllByOrderByCreatedAtDesc()) {
                if (p == null || p.getPlanStatus() == DisbursementPlanStatus.CANCELLED) continue;
                if (p.getScheme() == null || !Objects.equals(p.getScheme().getId(), saved.getScheme().getId())) continue;
                if (saved.getApplication() != null && (p.getApplication() == null || !Objects.equals(p.getApplication().getId(), saved.getApplication().getId()))) continue;
                if (p.getInstallments() == null) continue;
                for (DisbursementInstallment inst : p.getInstallments()) {
                    if (inst == null || !Objects.equals(inst.getInstallmentNumber(), saved.getTargetStage()) || inst.getMilestones() == null) continue;
                    for (DisbursementMilestone m : inst.getMilestones()) {
                        if (m != null) affectedMilestones.add(m);
                    }
                }
            }
        }
        for (DisbursementMilestone m : affectedMilestones) {
            recalculateMilestoneState(m, null);
        }
        return toRequirement(saved);
    }

    @Transactional
    public List<ComplianceItemResponse> getMine(String email) {
        User actor=user(email, Role.BENEFICIARY);
        syncRequirements();
        return compliances.findAll().stream()
                .filter(c->beneficiary(c).map(b->Objects.equals(b.getId(), actor.getId())).orElse(false))
                .filter(this::isVisibleToBeneficiary)
                .map(this::toItem).toList();
    }

    private boolean isVisibleToBeneficiary(MilestoneCompliance c) {
        if (c == null) return false;
        if (hasSubmittedOrReviewedHistory(c)) {
            return true;
        }
        return c.getRequirement() != null
                && c.getRequirement().isActive()
                && (c.getRequirement().getTargetStage() != null || c.getRequirement().getMilestone() != null);
    }

    private boolean hasSubmittedOrReviewedHistory(MilestoneCompliance c) {
        if (c == null) return false;
        if (c.getDocumentData() != null && c.getDocumentData().length > 0) return true;
        if (c.getSubmittedAt() != null) return true;
        if (c.getReviewHistory() != null && !c.getReviewHistory().isEmpty()) return true;
        ComplianceStatus st = c.getStatus();
        return st == ComplianceStatus.SUBMITTED
                || st == ComplianceStatus.UNDER_REVIEW
                || st == ComplianceStatus.APPROVED
                || st == ComplianceStatus.VERIFIED
                || st == ComplianceStatus.WAIVED;
    }

    @Transactional
    public List<ComplianceItemResponse> getReviewQueue(String email) {
        User actor=currentUser(email);
        if(actor.getRole()!=Role.ADMIN && actor.getRole()!=Role.OFFICER) throw denied("Officer or admin access required.");
        syncRequirements();
        return compliances.findAll().stream().filter(c->actor.getRole()==Role.ADMIN || assignedOfficer(c).map(u->Objects.equals(u.getId(),actor.getId())).orElse(false))
                .filter(c->c.getStatus()==ComplianceStatus.SUBMITTED || c.getStatus()==ComplianceStatus.UNDER_REVIEW)
                .map(this::toItem).toList();
    }

    @Transactional
    public ComplianceItemResponse submit(Long id, String notes, MultipartFile file, String email) {
        User actor=user(email,Role.BENEFICIARY);
        MilestoneCompliance item=compliances.findById(id).orElseThrow(()->notFound("Compliance requirement not found."));
        if(!beneficiary(item).map(b->Objects.equals(b.getId(),actor.getId())).orElse(false)) throw denied("You can submit documents only for your own grant requirements.");
        if(item.getRequirement() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No compliance requirement is configured by Admin for this stage.");
        if(!item.getRequirement().isActive())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This compliance requirement is inactive and no longer accepts submissions.");
        if(item.getStatus()!=ComplianceStatus.PENDING && item.getStatus()!=ComplianceStatus.OVERDUE && item.getStatus()!=ComplianceStatus.REJECTED && item.getStatus()!=ComplianceStatus.CORRECTION_REQUIRED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This requirement is not accepting a submission or replacement.");
        if(file==null || file.isEmpty()) throw bad("A supporting document is required.");
        String allowed = item.getRequirement() != null && item.getRequirement().getAllowedFormats() != null
                ? item.getRequirement().getAllowedFormats()
                : "pdf,png,jpg,jpeg";
        long maxBytes = item.getRequirement() != null && item.getRequirement().getMaxFileSizeBytes() > 0
                ? item.getRequirement().getMaxFileSizeBytes()
                : 10485760L;
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        List<String> allowedList = Arrays.stream(allowed.toLowerCase(Locale.ROOT).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (allowedList.isEmpty() || !allowedList.contains(extension)) {
            throw bad("This file format is not allowed. Allowed: " + allowed + ".");
        }
        if (file.getSize() > maxBytes) {
            throw bad("Document exceeds the configured maximum file size.");
        }
        if (file.getContentType() == null || file.getContentType().isBlank()) {
            throw bad("Document content type is required.");
        }
        String clientContentType = file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        Map<String, Set<String>> allowedMimes = Map.of(
                "pdf", Set.of("application/pdf", "application/x-pdf", "application/acrobat", "applications/vnd.pdf", "text/pdf", "application/octet-stream"),
                "png", Set.of("image/png", "image/x-png", "application/octet-stream"),
                "jpg", Set.of("image/jpeg", "image/pjpeg", "image/jpg", "application/octet-stream"),
                "jpeg", Set.of("image/jpeg", "image/pjpeg", "image/jpg", "application/octet-stream"),
                "doc", Set.of("application/msword", "application/doc", "application/octet-stream"),
                "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/x-zip-compressed", "application/octet-stream"),
                "xls", Set.of("application/vnd.ms-excel", "application/msexcel", "application/x-msexcel", "application/octet-stream"),
                "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/x-zip-compressed", "application/octet-stream")
        );
        Set<String> validMimes = allowedMimes.getOrDefault(extension, Set.of());
        if (!validMimes.contains(clientContentType)) {
            throw bad("Document content type does not match its file format.");
        }
        byte[] documentBytes;
        try {
            documentBytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded document.");
        }
        if (!validSignature(extension, documentBytes)) {
            throw bad("Document contents do not match the selected file format.");
        }
        Map<String, String> standardMimes = Map.of(
                "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
                "doc", "application/msword", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "xls", "application/vnd.ms-excel", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String finalContentType = clientContentType.equals("application/octet-stream")
                ? standardMimes.getOrDefault(extension, clientContentType)
                : clientContentType;
        item.setDocumentData(documentBytes);
        item.setDocumentFileName(filename);
        item.setDocumentFileSize(file.getSize());
        item.setDocumentContentType(finalContentType);
        item.setSubmissionNotes(notes); item.setSubmittedBy(actor); item.setSubmittedAt(LocalDateTime.now()); item.setStatus(ComplianceStatus.SUBMITTED);
        item.setVerificationRemarks(null); item.setVerifiedBy(null); item.setVerifiedAt(null);
        history(item,actor,"SUBMITTED",notes,filename);
        DisbursementMilestone m=item.getMilestone();
        if (m != null && m.getNonCompliant() && m.getNonComplianceReason() != null && m.getNonComplianceReason().toLowerCase(Locale.ROOT).contains("compliance")) {
            m.setNonCompliant(false);
            m.setNonComplianceReason(null);
            milestones.save(m);
        }
        if (m != null && m.getInstallment() != null && m.getInstallment().getPlan() != null) {
            GrantApplication app = m.getInstallment().getPlan().getApplication();
            if (app != null && app.getAssignedOfficer() != null) {
                notifications.notify(app.getAssignedOfficer(), "compliance_submitted:" + id + ":" + item.getSubmittedAt(), "A compliance document is ready for review: " + item.getRequirementTitle() + ".", app.getId());
            }
        }
        return toItem(compliances.save(item));
    }

    @Transactional
    public ComplianceItemResponse review(Long id, ComplianceReviewRequest req, String email) {
        User actor=currentUser(email);
        if(actor.getRole()!=Role.ADMIN && actor.getRole()!=Role.OFFICER) throw denied("Officer or admin access required.");
        MilestoneCompliance item=compliances.findById(id).orElseThrow(()->notFound("Compliance record not found."));
        if(actor.getRole()==Role.OFFICER && !assignedOfficer(item).map(u->Objects.equals(u.getId(),actor.getId())).orElse(false)) throw denied("This application is outside your assigned workflow.");
        if(item.getStatus()!=ComplianceStatus.SUBMITTED && item.getStatus()!=ComplianceStatus.UNDER_REVIEW) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only submitted compliance documents can be reviewed.");
        String decision=req.decision().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("APPROVED","REJECTED","CORRECTION_REQUIRED").contains(decision)) throw bad("Decision must be APPROVED, REJECTED, or CORRECTION_REQUIRED.");
        if(!decision.equals("APPROVED") && (req.remarks()==null || req.remarks().isBlank())) throw bad("Remarks are required for a rejection or correction request.");
        if(decision.equals("APPROVED") && (item.getDocumentData()==null || item.getDocumentData().length==0)) {
            throw bad("Cannot approve compliance evidence without an uploaded supporting document.");
        }
        item.setStatus(decision.equals("APPROVED")?ComplianceStatus.APPROVED:decision.equals("REJECTED")?ComplianceStatus.REJECTED:ComplianceStatus.CORRECTION_REQUIRED);
        item.setVerifiedBy(actor); item.setVerifiedAt(LocalDateTime.now()); item.setVerificationRemarks(req.remarks());
        history(item,actor,decision,req.remarks(),null);
        MilestoneCompliance saved = compliances.save(item);

        DisbursementMilestone m = saved.getMilestone();
        if (m != null) {
            recalculateMilestoneState(m, saved);
        }

        long correctionCount=saved.getReviewHistory().stream().filter(h->h.getAction().equals("REJECTED")||h.getAction().equals("CORRECTION_REQUIRED")).count();
        if(!decision.equals("APPROVED") && correctionCount>=3) createNonCompliance(saved,LocalDate.now(),"Repeated unsuccessful compliance submissions.","HIGH");
        DisbursementMilestone rm = saved.getMilestone();
        if (rm != null && rm.getInstallment() != null && rm.getInstallment().getPlan() != null) {
            GrantApplication app = rm.getInstallment().getPlan().getApplication();
            if (app != null && app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) {
                notifications.notify(app.getBeneficiary().getUser(), "compliance_review:" + id + ":" + saved.getVerifiedAt(), "Your compliance submission was " + decision.toLowerCase(Locale.ROOT) + "." + (req.remarks() == null ? "" : " Remarks: " + req.remarks()), app.getId());
            }
        }
        return toItem(saved);
    }

    @Transactional
    public ComplianceItemResponse waive(Long id,ComplianceWaiverRequest req,String email){
        User actor=user(email,Role.ADMIN);
        MilestoneCompliance item=compliances.findById(id).orElseThrow(()->notFound("Compliance requirement not found."));
        if(item.getStatus()==ComplianceStatus.VERIFIED || item.getStatus()==ComplianceStatus.APPROVED || item.getStatus()==ComplianceStatus.WAIVED) throw new ResponseStatusException(HttpStatus.CONFLICT,"This requirement is already closed.");
        item.setStatus(ComplianceStatus.WAIVED);item.setVerifiedBy(actor);item.setVerifiedAt(LocalDateTime.now());item.setVerificationRemarks(req.remarks());
        history(item,actor,"WAIVED",req.remarks(),null);
        MilestoneCompliance saved = compliances.save(item);

        DisbursementMilestone m = saved.getMilestone();
        if (m != null) {
            recalculateMilestoneState(m, saved);
        }
        return toItem(saved);
    }

    @Transactional(readOnly=true)
    public byte[] document(Long id,String email) {
        User actor=currentUser(email); MilestoneCompliance c=compliances.findById(id).orElseThrow(()->notFound("Compliance record not found."));
        boolean allowed=actor.getRole()==Role.ADMIN || (actor.getRole()==Role.BENEFICIARY && beneficiary(c).map(b->Objects.equals(b.getId(),actor.getId())).orElse(false))
                || (actor.getRole()==Role.OFFICER && assignedOfficer(c).map(u->Objects.equals(u.getId(),actor.getId())).orElse(false));
        if(!allowed) throw denied("You do not have access to this compliance document.");
        if(c.getDocumentData()==null) throw notFound("No document is attached."); return c.getDocumentData();
    }

    @Transactional(readOnly=true)
    public List<NonComplianceResponse> getNonCompliances(String email) {
        User actor=currentUser(email); if(actor.getRole()==Role.BENEFICIARY) throw denied("Beneficiary access is not available for non-compliance case records.");
        return nonCompliances.findAllByOrderByDetectedAtDesc().stream().filter(n->actor.getRole()==Role.ADMIN || assignedOfficer(n.getCompliance()).map(u->Objects.equals(u.getId(),actor.getId())).orElse(false)).map(this::toNonCompliance).toList();
    }

    @Transactional
    public NonComplianceResponse resolveNonCompliance(Long id, NonComplianceResolutionRequest req, String email) {
        User actor=currentUser(email); if(actor.getRole()!=Role.ADMIN && actor.getRole()!=Role.OFFICER) throw denied("Officer or admin access required.");
        ComplianceNonCompliance item=nonCompliances.findById(id).orElseThrow(()->notFound("Non-compliance case not found."));
        if(actor.getRole()==Role.OFFICER && !assignedOfficer(item.getCompliance()).map(u->Objects.equals(u.getId(),actor.getId())).orElse(false)) throw denied("This case is outside your assigned workflow.");
        item.setStatus(req.status()); item.setOfficerRemarks(req.remarks());
        NonComplianceHistory h=new NonComplianceHistory();h.setActor(actor);h.setStatus(req.status());h.setRemarks(req.remarks());item.addHistory(h);
        return toNonCompliance(nonCompliances.save(item));
    }

    @Scheduled(fixedDelayString="${grantora.compliance.scan-ms:3600000}")
    @Transactional
    public void scheduledComplianceScan() { scanDeadlines(); }

    @Transactional
    public Map<String,Object> scanDeadlines() {
        syncRequirements(); LocalDate today=LocalDate.now(); int notified=0, overdue=0;
        DisbursementReminderConfig config=reminderConfig.findById(1L).orElseGet(DisbursementReminderConfig::new);
        if(Boolean.FALSE.equals(config.getEnabled())) return Map.of("scannedAt",LocalDateTime.now(),"remindersRecorded",0,"overdueDetected",0,"status","DISABLED");
        int daysBefore=config.getUpcomingDaysBefore()==null?3:config.getUpcomingDaysBefore();
        for(MilestoneCompliance c:compliances.findAll()) {
            if(c.getRequirement()==null || !c.getRequirement().isActive() || !c.isRequired() || c.getSubmissionDeadline()==null || terminal(c.getStatus())) continue;
            String event=null;
            if(c.getSubmissionDeadline().equals(today.plusDays(daysBefore))) event="UPCOMING";
            else if(c.getSubmissionDeadline().equals(today)) event="DEADLINE_DAY";
            else if(c.getSubmissionDeadline().isBefore(today)) {
                event="OVERDUE";
                if(c.getStatus()==ComplianceStatus.PENDING || c.getStatus()==ComplianceStatus.OVERDUE) c.setStatus(ComplianceStatus.OVERDUE);
                overdue++;
                createNonCompliance(c,today,"Mandatory compliance requirement overdue since "+c.getSubmissionDeadline()+".",null);
            }
            if(event==null || reminders.existsByCompliance_IdAndEventTypeAndEventDate(c.getId(),event,today)) continue;
            if (c.getMilestone() != null && c.getMilestone().getInstallment() != null && c.getMilestone().getInstallment().getPlan() != null) {
                GrantApplication app = c.getMilestone().getInstallment().getPlan().getApplication();
                if (app != null) {
                    String message = event.equals("OVERDUE") ? "Mandatory compliance is overdue: " + c.getRequirementTitle() + "." : "Reminder: " + c.getRequirementTitle() + " is due on " + c.getSubmissionDeadline() + ".";
                    if (app.getBeneficiary() != null && app.getBeneficiary().getUser() != null) notifications.notify(app.getBeneficiary().getUser(), "compliance_reminder:" + c.getId() + ":" + event + ":" + today, message, app.getId());
                    if (app.getAssignedOfficer() != null) notifications.notify(app.getAssignedOfficer(), "compliance_reminder:" + c.getId() + ":" + event + ":" + today + ":officer", message + " Application #" + app.getId() + ".", app.getId());
                }
            }
            ComplianceReminderHistory h=new ComplianceReminderHistory();h.setCompliance(c);h.setEventType(event);h.setEventDate(today);reminders.save(h);notified++;
        }
        return Map.of("scannedAt",LocalDateTime.now(),"remindersRecorded",notified,"overdueDetected",overdue);
    }

    private void syncRequirements() {
        for (ComplianceRequirement r : requirements.findByActiveTrue()) {
            materialize(r);
        }
    }

    private void materialize(ComplianceRequirement r) {
        if (r == null || r.getScheme() == null) return;
        // Legacy requirements with targetStage == null and milestone == null are left unchanged and never broadcast across all stages.
        if (r.getMilestone() == null && r.getTargetStage() == null) return;
        for(DisbursementPlan p:plans.findAllByOrderByCreatedAtDesc()) {
            if(p == null || p.getPlanStatus()==DisbursementPlanStatus.CANCELLED) continue;
            if(p.getScheme() == null || !Objects.equals(p.getScheme().getId(),r.getScheme().getId())) continue;
            if(r.getApplication()!=null && (p.getApplication() == null || !Objects.equals(p.getApplication().getId(),r.getApplication().getId()))) continue;
            if(p.getInstallments() == null) continue;
            for(DisbursementInstallment i:p.getInstallments()) {
                if(i == null || i.getMilestones() == null) continue;
                if(r.getMilestone()==null && r.getTargetStage()!=null && !Objects.equals(i.getInstallmentNumber(), r.getTargetStage())) continue;
                for(DisbursementMilestone m:i.getMilestones()) {
                    if(m == null) continue;
                    if(r.getMilestone()!=null && !Objects.equals(r.getMilestone().getId(),m.getId())) continue;
                    if(!compliances.existsByRequirement_IdAndMilestone_Id(r.getId(),m.getId())) {
                        MilestoneCompliance c=new MilestoneCompliance();
                        c.setMilestone(m);
                        c.setRequirement(r);
                        c.setRequirementTitle(r.getTitle());
                        c.setDescription(r.getDescription());
                        c.setRequired(r.isRequired());
                        c.setSubmissionDeadline(r.getSubmissionDeadline() != null ? r.getSubmissionDeadline() : m.getDueDate());
                        c.setStatus(ComplianceStatus.PENDING);
                        MilestoneCompliance saved = compliances.save(c);
                        if (saved != null && m.getCompliances() != null && m.getCompliances().stream().noneMatch(existing -> existing == saved || (saved.getId() != null && Objects.equals(existing.getId(), saved.getId())))) {
                            m.getCompliances().add(saved);
                        }
                    }
                    recalculateMilestoneState(m, null);
                }
            }
        }
    }

    private void ensureDefaultRequirementIfNeeded(DisbursementMilestone m) {
        if (m == null) return;
        List<MilestoneCompliance> current = getMilestoneCompliances(m, null);
        boolean hasActiveAdminReq = current.stream()
                .anyMatch(c -> c.getRequirement() != null && c.getRequirement().isActive());
        if (hasActiveAdminReq) return;

        boolean hasDefaultReq = current.stream().anyMatch(c -> c.getRequirement() == null);
        if (hasDefaultReq) return;

        Integer stageNum = m.getInstallment() != null ? m.getInstallment().getInstallmentNumber() : null;
        MilestoneCompliance restored = new MilestoneCompliance();
        restored.setMilestone(m);
        restored.setRequirement(null);
        restored.setRequirementTitle(stageNum != null
                ? "Stage " + stageNum + " Utilization Certificate & Invoices"
                : "Milestone Completion Certificate");
        restored.setDescription(defaultStageDescription(stageNum));
        restored.setRequired(true);
        restored.setSubmissionDeadline(m.getDueDate());
        restored.setStatus(ComplianceStatus.PENDING);
        MilestoneCompliance saved = compliances.save(restored);
        if (saved != null && m.getCompliances() != null && m.getCompliances().stream().noneMatch(existing -> existing == saved || (saved.getId() != null && Objects.equals(existing.getId(), saved.getId())))) {
            m.getCompliances().add(saved);
        }
    }

    private String defaultStageDescription(Integer stageNumber) {
        if (stageNumber == null) {
            return "Default compliance requirement (Admin-specific requirement not active): Submit milestone completion proof.";
        }
        return switch (stageNumber) {
            case 1 -> "Default Stage 1 compliance (no Admin-configured Stage 1 requirement active): Submit initial utilization certificate, advance acknowledgment, and baseline invoices.";
            case 2 -> "Default Stage 2 compliance (no Admin-configured Stage 2 requirement active): Submit mid-milestone progress report, interim utilization certificate, and supporting invoices.";
            case 3 -> "Default Stage 3 compliance (no Admin-configured Stage 3 requirement active): Submit final completion certificate, consolidated utilization certificate, and audited invoices.";
            default -> "Default Stage " + stageNumber + " compliance (no Admin-configured requirement active): Submit expenditure verification documents and progress report.";
        };
    }

    private void cleanupDefaultPlaceholders(DisbursementMilestone m) {
        if (m == null) return;
        List<MilestoneCompliance> milestoneCompliances = getMilestoneCompliances(m, null);
        boolean hasActiveAdminReq = milestoneCompliances.stream()
                .anyMatch(c -> c.getRequirement() != null
                        && c.getRequirement().isActive()
                        && (c.getRequirement().getTargetStage() != null || c.getRequirement().getMilestone() != null));
        if (!hasActiveAdminReq) return;

        List<MilestoneCompliance> placeholders = milestoneCompliances.stream()
                .filter(this::isUnsubmittedDefaultPlaceholder)
                .filter(c -> c.getId() == null || nonCompliances.findByCompliance_Id(c.getId()).isEmpty())
                .toList();
        if (placeholders.isEmpty()) return;

        for (MilestoneCompliance placeholder : placeholders) {
            if (m.getCompliances() != null) {
                m.getCompliances().removeIf(existing -> existing == placeholder || (existing.getId() != null && Objects.equals(existing.getId(), placeholder.getId())));
            }
            if (placeholder.getId() != null) {
                compliances.delete(placeholder);
            }
        }
        recalculateMilestoneState(m, null);
    }

    private boolean isUnsubmittedDefaultPlaceholder(MilestoneCompliance c) {
        if (c == null || c.getRequirement() != null) return false;
        if (c.getDocumentData() != null && c.getDocumentData().length > 0) return false;
        if (c.getStatus() != ComplianceStatus.PENDING && c.getStatus() != ComplianceStatus.OVERDUE) return false;
        if (c.getReviewHistory() != null && !c.getReviewHistory().isEmpty()) return false;
        String title = c.getRequirementTitle();
        if (title == null) return false;
        return (title.startsWith("Stage ") && title.endsWith("Utilization Certificate & Invoices"))
                || title.equals("Milestone Completion Certificate");
    }

    private List<MilestoneCompliance> getMilestoneCompliances(DisbursementMilestone m, MilestoneCompliance updatedItem) {
        List<MilestoneCompliance> fromRepo = (m.getId() != null) ? compliances.findByMilestone_Id(m.getId()) : Collections.emptyList();
        List<MilestoneCompliance> base = (fromRepo != null && !fromRepo.isEmpty())
                ? new ArrayList<>(fromRepo)
                : (m.getCompliances() != null ? new ArrayList<>(m.getCompliances()) : new ArrayList<>());
        if (updatedItem != null && updatedItem.getId() != null) {
            boolean replaced = false;
            for (int idx = 0; idx < base.size(); idx++) {
                if (Objects.equals(base.get(idx).getId(), updatedItem.getId())) {
                    base.set(idx, updatedItem);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                base.add(updatedItem);
            }
        }
        return base;
    }

    private boolean isActiveObligation(MilestoneCompliance c) {
        if (c == null) return false;
        if (c.getRequirement() != null) {
            return c.getRequirement().isActive();
        }
        return !isUnsubmittedDefaultPlaceholder(c);
    }

    private void recalculateMilestoneState(DisbursementMilestone m, MilestoneCompliance updatedItem) {
        List<MilestoneCompliance> milestoneCompliances = getMilestoneCompliances(m, updatedItem).stream()
                .filter(this::isActiveObligation)
                .toList();
        if (milestoneCompliances.isEmpty()) {
            if (m.getStatus() == MilestoneStatus.COMPLETED) {
                m.setStatus(MilestoneStatus.PENDING);
                m.setProgressPercentage(0);
                m.setCompletedAt(null);
                milestones.save(m);
            }
            return;
        }

        boolean hasRejected = updatedItem != null
                && (updatedItem.getStatus() == ComplianceStatus.REJECTED || updatedItem.getStatus() == ComplianceStatus.CORRECTION_REQUIRED);

        if (hasRejected) {
            m.setNonCompliant(true);
            m.setNonComplianceReason("Compliance " + updatedItem.getStatus().name().toLowerCase(Locale.ROOT) + ": "
                    + (updatedItem.getVerificationRemarks() != null ? updatedItem.getVerificationRemarks() : "Requirements not satisfied"));
        }

        boolean anyRejected = milestoneCompliances.stream()
                .anyMatch(c -> c.isRequired() && (c.getStatus() == ComplianceStatus.REJECTED || c.getStatus() == ComplianceStatus.CORRECTION_REQUIRED));

        boolean allSatisfied = !anyRejected && milestoneCompliances.stream().allMatch(c ->
                !c.isRequired()
                || c.getStatus() == ComplianceStatus.APPROVED
                || c.getStatus() == ComplianceStatus.VERIFIED
                || c.getStatus() == ComplianceStatus.WAIVED
        );

        if (allSatisfied) {
            m.setNonCompliant(false);
            m.setNonComplianceReason(null);
            m.setStatus(MilestoneStatus.COMPLETED);
            m.setProgressPercentage(100);
            if (m.getCompletedAt() == null) {
                m.setCompletedAt(LocalDateTime.now());
            }
        } else {
            long totalReq = milestoneCompliances.stream().filter(MilestoneCompliance::isRequired).count();
            long completedReq = milestoneCompliances.stream().filter(c ->
                    c.isRequired() && (c.getStatus() == ComplianceStatus.APPROVED
                            || c.getStatus() == ComplianceStatus.VERIFIED
                            || c.getStatus() == ComplianceStatus.WAIVED)
            ).count();
            int progress = totalReq > 0 ? (int) ((completedReq * 100) / totalReq) : 0;
            m.setProgressPercentage(progress);
            if (m.getStatus() == MilestoneStatus.COMPLETED) {
                m.setStatus(MilestoneStatus.PENDING);
                m.setCompletedAt(null);
            } else if (m.getStatus() == MilestoneStatus.PLANNED && (completedReq > 0 || anyRejected)) {
                m.setStatus(MilestoneStatus.PENDING);
            }
        }
        milestones.save(m);
    }
    private void createNonCompliance(MilestoneCompliance c,LocalDate today,String reason,String severity) {
        if(nonCompliances.findByCompliance_Id(c.getId()).isPresent()) return;
        ComplianceNonCompliance n=new ComplianceNonCompliance();n.setCompliance(c);n.setReason(reason);
        n.setSeverity(severity!=null?severity:(c.getSubmissionDeadline().plusDays(30).isBefore(today)?"HIGH":"MEDIUM"));
        NonComplianceHistory h=new NonComplianceHistory();h.setStatus(NonComplianceStatus.OPEN);h.setRemarks(reason);n.addHistory(h);nonCompliances.save(n);
    }
    private boolean terminal(ComplianceStatus s){return s==ComplianceStatus.VERIFIED||s==ComplianceStatus.APPROVED||s==ComplianceStatus.WAIVED||s==ComplianceStatus.SUBMITTED||s==ComplianceStatus.UNDER_REVIEW;}
    private void history(MilestoneCompliance c,User actor,String action,String remarks,String filename){ComplianceReviewHistory h=new ComplianceReviewHistory();h.setActor(actor);h.setAction(action);h.setRemarks(remarks);h.setDocumentFileName(filename);c.addHistory(h);}
    private Optional<User> beneficiary(MilestoneCompliance c) {
        Optional<User> user = Optional.ofNullable(c)
                .map(MilestoneCompliance::getMilestone)
                .map(DisbursementMilestone::getInstallment)
                .map(DisbursementInstallment::getPlan)
                .map(DisbursementPlan::getApplication)
                .map(GrantApplication::getBeneficiary)
                .map(Beneficiary::getUser);
        if (user.isPresent()) {
            return user;
        }
        return Optional.ofNullable(c)
                .map(MilestoneCompliance::getRequirement)
                .map(ComplianceRequirement::getApplication)
                .map(GrantApplication::getBeneficiary)
                .map(Beneficiary::getUser);
    }

    private Optional<User> assignedOfficer(MilestoneCompliance c) {
        Optional<User> officer = Optional.ofNullable(c)
                .map(MilestoneCompliance::getMilestone)
                .map(DisbursementMilestone::getInstallment)
                .map(DisbursementInstallment::getPlan)
                .map(DisbursementPlan::getApplication)
                .map(GrantApplication::getAssignedOfficer);
        if (officer.isPresent()) {
            return officer;
        }
        return Optional.ofNullable(c)
                .map(MilestoneCompliance::getRequirement)
                .map(ComplianceRequirement::getApplication)
                .map(GrantApplication::getAssignedOfficer);
    }
    private User currentUser(String email){return users.findByEmail(email).orElseThrow(()->notFound("User not found."));}
    private User user(String email,Role expected){User u=currentUser(email);if(u.getRole()!=expected)throw denied(expected+" access required.");return u;}
    private String normalizeFormats(String value){Set<String> allowed=Set.of("pdf","png","jpg","jpeg","doc","docx","xls","xlsx");List<String> formats=Arrays.stream(value.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s->!s.isBlank()).distinct().toList();if(formats.isEmpty()||!allowed.containsAll(formats))throw bad("Allowed formats are pdf, png, jpg, jpeg, doc, docx, xls, and xlsx.");return String.join(",",formats);}
    private boolean validSignature(String extension,byte[] b){
        if(extension.equals("pdf")) return b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';
        if(extension.equals("png")) return b.length>=4&&(b[0]&255)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G';
        if(extension.equals("jpg")||extension.equals("jpeg")) return b.length>=3&&(b[0]&255)==0xff&&(b[1]&255)==0xd8&&(b[2]&255)==0xff;
        if(extension.equals("doc")||extension.equals("xls")) return b.length>=4&&(b[0]&255)==0xd0&&(b[1]&255)==0xcf&&(b[2]&255)==0x11&&(b[3]&255)==0xe0;
        return (extension.equals("docx")||extension.equals("xlsx"))&&b.length>=4&&b[0]=='P'&&b[1]=='K'&&b[2]==3&&b[3]==4;
    }
    private ComplianceRequirementResponse toRequirement(ComplianceRequirement r){
        return new ComplianceRequirementResponse(
                r.getId(),
                r.getScheme() != null ? r.getScheme().getId() : null,
                r.getScheme() != null ? r.getScheme().getSchemeName() : "Unknown Scheme",
                r.getMilestone()==null?null:r.getMilestone().getId(),
                r.getApplication()==null?null:r.getApplication().getId(),
                r.getTargetStage(),
                r.getTitle(),
                r.getDescription(),
                r.isRequired(),
                r.getSubmissionDeadline(),
                r.getAllowedFormats(),
                r.getMaxFileSizeBytes(),
                r.isActive()
        );
    }
    private ComplianceItemResponse toItem(MilestoneCompliance c) {
        DisbursementMilestone m = c != null ? c.getMilestone() : null;
        DisbursementInstallment installment = m != null ? m.getInstallment() : null;
        DisbursementPlan p = installment != null ? installment.getPlan() : null;
        GrantApplication a = p != null ? p.getApplication() : null;
        Scheme scheme = a != null && a.getScheme() != null ? a.getScheme() : (p != null ? p.getScheme() : null);
        Beneficiary ben = a != null ? a.getBeneficiary() : null;
        User benUser = ben != null ? ben.getUser() : null;

        String format = c != null && c.getRequirement() != null ? c.getRequirement().getAllowedFormats() : "pdf,png,jpg,jpeg";
        long max = c != null && c.getRequirement() != null && c.getRequirement().getMaxFileSizeBytes() > 0
                ? c.getRequirement().getMaxFileSizeBytes()
                : 10485760L;

        return new ComplianceItemResponse(
                c != null ? c.getId() : null,
                c != null && c.getRequirement() != null ? c.getRequirement().getId() : null,
                m != null ? m.getId() : null,
                installment != null ? installment.getId() : null,
                m != null ? m.getTitle() : null,
                a != null ? a.getId() : null,
                scheme != null ? scheme.getId() : null,
                scheme != null ? scheme.getSchemeName() : "Unknown Scheme",
                benUser != null ? benUser.getFullName() : (ben != null ? "Beneficiary #" + ben.getId() : "Beneficiary"),
                c != null ? c.getRequirementTitle() : null,
                c != null ? c.getDescription() : null,
                c != null && c.isRequired(),
                c != null ? c.getSubmissionDeadline() : null,
                format,
                max,
                c != null ? c.getStatus() : null,
                c != null ? c.getDocumentFileName() : null,
                c != null ? c.getDocumentContentType() : null,
                c != null ? c.getDocumentFileSize() : null,
                c != null && c.getDocumentData() != null && c.getDocumentData().length > 0,
                c != null ? c.getSubmittedAt() : null,
                c != null && c.getSubmittedBy() != null ? c.getSubmittedBy().getFullName() : null,
                c != null && c.getVerifiedBy() != null ? c.getVerifiedBy().getFullName() : null,
                c != null ? c.getVerifiedAt() : null,
                c != null ? c.getVerificationRemarks() : null,
                c == null || c.getReviewHistory() == null ? Collections.emptyList() : c.getReviewHistory().stream()
                        .map(h -> new ComplianceHistoryResponse(h.getAction(), h.getActor() == null ? null : h.getActor().getFullName(), h.getRemarks(), h.getDocumentFileName(), h.getOccurredAt()))
                        .toList(),
                c != null && c.getRequirement() != null && c.getRequirement().getTargetStage() != null
                        ? c.getRequirement().getTargetStage()
                        : (installment != null ? installment.getInstallmentNumber() : null),
                c != null && c.getRequirement() != null && c.getRequirement().isActive()
        );
    }

    private NonComplianceResponse toNonCompliance(ComplianceNonCompliance n) {
        MilestoneCompliance c = n != null ? n.getCompliance() : null;
        DisbursementMilestone m = c != null ? c.getMilestone() : null;
        DisbursementInstallment installment = m != null ? m.getInstallment() : null;
        DisbursementPlan plan = installment != null ? installment.getPlan() : null;
        GrantApplication a = plan != null ? plan.getApplication() : null;
        Beneficiary ben = a != null ? a.getBeneficiary() : null;
        User benUser = ben != null ? ben.getUser() : null;

        return new NonComplianceResponse(
                n != null ? n.getId() : null,
                c != null ? c.getId() : null,
                a != null ? a.getId() : null,
                m != null ? m.getId() : null,
                m != null ? m.getTitle() : null,
                benUser != null ? benUser.getFullName() : "Beneficiary",
                n != null ? n.getReason() : null,
                n != null ? n.getSeverity() : null,
                n != null ? n.getStatus() : null,
                n != null ? n.getDetectedAt() : null,
                n != null ? n.getOfficerRemarks() : null,
                n == null || n.getHistory() == null ? Collections.emptyList() : n.getHistory().stream()
                        .map(h -> new NonComplianceHistoryResponse(h.getStatus(), h.getActor() == null ? null : h.getActor().getFullName(), h.getRemarks(), h.getOccurredAt()))
                        .toList()
        );
    }
    private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private ResponseStatusException denied(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
}
