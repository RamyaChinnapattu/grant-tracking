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
        if(req.maxFileSizeBytes()<1 || req.maxFileSizeBytes()>10485760L) throw bad("Maximum file size must be between 1 byte and 10 MB.");
        requirement.setMaxFileSizeBytes(req.maxFileSizeBytes()); requirement.setCreatedBy(actor);
        if(req.applicationId()!=null){
            GrantApplication app=applications.findById(req.applicationId()).orElseThrow(()->notFound("Application not found."));
            if(!Objects.equals(app.getScheme().getId(),scheme.getId())) throw bad("Application does not belong to the selected scheme.");
            if(app.getStatus()!=GrantApplicationStatus.APPROVED) throw bad("Compliance requirements can only be scoped to an approved application.");
            requirement.setApplication(app);
        }
        if(req.milestoneId()!=null){
            DisbursementMilestone milestone=milestones.findById(req.milestoneId()).orElseThrow(()->notFound("Milestone not found."));
            if(!Objects.equals(milestone.getInstallment().getPlan().getScheme().getId(),scheme.getId())) throw bad("Milestone does not belong to the selected scheme.");
            if(req.applicationId()!=null && !Objects.equals(milestone.getInstallment().getPlan().getApplication().getId(),req.applicationId())) throw bad("Milestone does not belong to the selected application.");
            requirement.setMilestone(milestone);
        }
        boolean duplicate=requirements.findByActiveTrue().stream().anyMatch(existing ->
                Objects.equals(existing.getScheme().getId(),scheme.getId())
                && Objects.equals(existing.getMilestone()==null?null:existing.getMilestone().getId(),req.milestoneId())
                && Objects.equals(existing.getApplication()==null?null:existing.getApplication().getId(),req.applicationId())
                && existing.getTitle().equalsIgnoreCase(req.title().trim()));
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
        return toRequirement(requirements.save(item));
    }

    @Transactional
    public List<ComplianceItemResponse> getMine(String email) {
        User actor=user(email, Role.BENEFICIARY);
        syncRequirements();
        return compliances.findAll().stream().filter(c->beneficiary(c).map(b->Objects.equals(b.getId(), actor.getId())).orElse(false))
                .map(this::toItem).toList();
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
        if(item.getStatus()!=ComplianceStatus.PENDING && item.getStatus()!=ComplianceStatus.OVERDUE && item.getStatus()!=ComplianceStatus.REJECTED && item.getStatus()!=ComplianceStatus.CORRECTION_REQUIRED)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This requirement is not accepting a submission or replacement.");
        if(file==null || file.isEmpty()) throw bad("A supporting document is required.");
        String allowed=item.getRequirement()!=null?item.getRequirement().getAllowedFormats():"pdf,png,jpg,jpeg";
        long maxBytes=item.getRequirement()!=null?item.getRequirement().getMaxFileSizeBytes():10485760L;
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/'); filename=filename.substring(filename.lastIndexOf('/')+1);
        String extension=filename.contains(".")?filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        if(!Arrays.asList(allowed.split(",")).contains(extension)) throw bad("This file format is not allowed. Allowed: "+allowed+".");
        if(file.getSize()>maxBytes) throw bad("Document exceeds the configured maximum file size.");
        if(file.getContentType()==null || file.getContentType().isBlank()) throw bad("Document content type is required.");
        Map<String,String> mimeByExtension=Map.of("pdf","application/pdf","png","image/png","jpg","image/jpeg","jpeg","image/jpeg",
                "doc","application/msword","docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "xls","application/vnd.ms-excel","xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if(!mimeByExtension.getOrDefault(extension,"").equalsIgnoreCase(file.getContentType())) throw bad("Document content type does not match its file format.");
        byte[] documentBytes;
        try{ documentBytes=file.getBytes(); }catch(IOException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Could not read uploaded document.");}
        if(!validSignature(extension,documentBytes)) throw bad("Document contents do not match the selected file format.");
        item.setDocumentData(documentBytes);
        item.setDocumentFileName(filename); item.setDocumentFileSize(file.getSize()); item.setDocumentContentType(file.getContentType());
        item.setSubmissionNotes(notes); item.setSubmittedBy(actor); item.setSubmittedAt(LocalDateTime.now()); item.setStatus(ComplianceStatus.SUBMITTED);
        item.setVerificationRemarks(null); item.setVerifiedBy(null); item.setVerifiedAt(null);
        history(item,actor,"SUBMITTED",notes,filename);
        DisbursementMilestone m=item.getMilestone();
        if (m != null && m.getNonCompliant() && m.getNonComplianceReason() != null && m.getNonComplianceReason().toLowerCase(Locale.ROOT).contains("compliance")) {
            m.setNonCompliant(false);
            m.setNonComplianceReason(null);
            milestones.save(m);
        }
        GrantApplication app=m.getInstallment().getPlan().getApplication();
        if(app.getAssignedOfficer()!=null) notifications.notify(app.getAssignedOfficer(),"compliance_submitted:"+id+":"+item.getSubmittedAt(),"A compliance document is ready for review: "+item.getRequirementTitle()+".",app.getId());
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

        DisbursementMilestone m = item.getMilestone();
        if (m != null) {
            if (decision.equals("APPROVED")) {
                boolean allSatisfied = m.getCompliances().stream().allMatch(c ->
                        !c.isRequired() || c.getId().equals(item.getId())
                        || c.getStatus() == ComplianceStatus.APPROVED
                        || c.getStatus() == ComplianceStatus.VERIFIED
                        || c.getStatus() == ComplianceStatus.WAIVED
                );
                if (allSatisfied) {
                    m.setNonCompliant(false);
                    m.setNonComplianceReason(null);
                    m.setStatus(MilestoneStatus.COMPLETED);
                    m.setProgressPercentage(100);
                    m.setCompletedAt(LocalDateTime.now());
                } else {
                    long totalReq = m.getCompliances().stream().filter(MilestoneCompliance::isRequired).count();
                    long completedReq = m.getCompliances().stream().filter(c ->
                            c.isRequired() && (c.getId().equals(item.getId()) || c.getStatus() == ComplianceStatus.APPROVED || c.getStatus() == ComplianceStatus.VERIFIED || c.getStatus() == ComplianceStatus.WAIVED)
                    ).count();
                    int progress = totalReq > 0 ? (int) ((completedReq * 100) / totalReq) : 50;
                    m.setProgressPercentage(progress);
                    if (m.getStatus() == MilestoneStatus.PLANNED) {
                        m.setStatus(MilestoneStatus.PENDING);
                    }
                }
            } else {
                m.setNonCompliant(true);
                m.setNonComplianceReason("Compliance " + decision.toLowerCase(Locale.ROOT) + ": " + (req.remarks() != null ? req.remarks() : "Requirements not satisfied"));
                if (m.getStatus() == MilestoneStatus.COMPLETED) {
                    m.setStatus(MilestoneStatus.PENDING);
                    m.setProgressPercentage(50);
                }
            }
            milestones.save(m);
        }

        long correctionCount=item.getReviewHistory().stream().filter(h->h.getAction().equals("REJECTED")||h.getAction().equals("CORRECTION_REQUIRED")).count();
        if(!decision.equals("APPROVED") && correctionCount>=3) createNonCompliance(item,LocalDate.now(),"Repeated unsuccessful compliance submissions.","HIGH");
        GrantApplication app=item.getMilestone().getInstallment().getPlan().getApplication();
        if(app.getBeneficiary()!=null && app.getBeneficiary().getUser()!=null) notifications.notify(app.getBeneficiary().getUser(),"compliance_review:"+id+":"+item.getVerifiedAt(),"Your compliance submission was "+decision.toLowerCase(Locale.ROOT)+"."+(req.remarks()==null?"":" Remarks: "+req.remarks()),app.getId());
        return toItem(compliances.save(item));
    }

    @Transactional
    public ComplianceItemResponse waive(Long id,ComplianceWaiverRequest req,String email){
        User actor=user(email,Role.ADMIN);
        MilestoneCompliance item=compliances.findById(id).orElseThrow(()->notFound("Compliance requirement not found."));
        if(item.getStatus()==ComplianceStatus.VERIFIED || item.getStatus()==ComplianceStatus.APPROVED || item.getStatus()==ComplianceStatus.WAIVED) throw new ResponseStatusException(HttpStatus.CONFLICT,"This requirement is already closed.");
        item.setStatus(ComplianceStatus.WAIVED);item.setVerifiedBy(actor);item.setVerifiedAt(LocalDateTime.now());item.setVerificationRemarks(req.remarks());
        history(item,actor,"WAIVED",req.remarks(),null);

        DisbursementMilestone m = item.getMilestone();
        if (m != null) {
            boolean allSatisfied = m.getCompliances().stream().allMatch(c ->
                    !c.isRequired() || c.getId().equals(item.getId())
                    || c.getStatus() == ComplianceStatus.APPROVED
                    || c.getStatus() == ComplianceStatus.VERIFIED
                    || c.getStatus() == ComplianceStatus.WAIVED
            );
            if (allSatisfied) {
                m.setNonCompliant(false);
                m.setNonComplianceReason(null);
                m.setStatus(MilestoneStatus.COMPLETED);
                m.setProgressPercentage(100);
                m.setCompletedAt(LocalDateTime.now());
                milestones.save(m);
            }
        }
        return toItem(compliances.save(item));
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
            GrantApplication app=c.getMilestone().getInstallment().getPlan().getApplication();
            String message=event.equals("OVERDUE")?"Mandatory compliance is overdue: "+c.getRequirementTitle()+".":"Reminder: "+c.getRequirementTitle()+" is due on "+c.getSubmissionDeadline()+".";
            if(app.getBeneficiary()!=null && app.getBeneficiary().getUser()!=null) notifications.notify(app.getBeneficiary().getUser(),"compliance_reminder:"+c.getId()+":"+event+":"+today,message,app.getId());
            if(app.getAssignedOfficer()!=null) notifications.notify(app.getAssignedOfficer(),"compliance_reminder:"+c.getId()+":"+event+":"+today+":officer",message+" Application #"+app.getId()+".",app.getId());
            ComplianceReminderHistory h=new ComplianceReminderHistory();h.setCompliance(c);h.setEventType(event);h.setEventDate(today);reminders.save(h);notified++;
        }
        return Map.of("scannedAt",LocalDateTime.now(),"remindersRecorded",notified,"overdueDetected",overdue);
    }

    private void syncRequirements() { for(ComplianceRequirement r:requirements.findByActiveTrue()) materialize(r); }
    private void materialize(ComplianceRequirement r) {
        for(DisbursementPlan p:plans.findAllByOrderByCreatedAtDesc()) {
            if(p.getPlanStatus()==DisbursementPlanStatus.CANCELLED) continue;
            if(!Objects.equals(p.getScheme().getId(),r.getScheme().getId())) continue;
            if(r.getApplication()!=null && !Objects.equals(p.getApplication().getId(),r.getApplication().getId())) continue;
            for(DisbursementInstallment i:p.getInstallments()) for(DisbursementMilestone m:i.getMilestones()) {
                if(r.getMilestone()!=null && !Objects.equals(r.getMilestone().getId(),m.getId())) continue;
                if(!compliances.existsByRequirement_IdAndMilestone_Id(r.getId(),m.getId())) {
                    MilestoneCompliance c=new MilestoneCompliance();c.setMilestone(m);c.setRequirement(r);c.setRequirementTitle(r.getTitle());c.setDescription(r.getDescription());
                    c.setRequired(r.isRequired());c.setSubmissionDeadline(r.getSubmissionDeadline());c.setStatus(ComplianceStatus.PENDING);compliances.save(c);
                }
            }
        }
    }
    private void createNonCompliance(MilestoneCompliance c,LocalDate today,String reason,String severity) {
        if(nonCompliances.findByCompliance_Id(c.getId()).isPresent()) return;
        ComplianceNonCompliance n=new ComplianceNonCompliance();n.setCompliance(c);n.setReason(reason);
        n.setSeverity(severity!=null?severity:(c.getSubmissionDeadline().plusDays(30).isBefore(today)?"HIGH":"MEDIUM"));
        NonComplianceHistory h=new NonComplianceHistory();h.setStatus(NonComplianceStatus.OPEN);h.setRemarks(reason);n.addHistory(h);nonCompliances.save(n);
    }
    private boolean terminal(ComplianceStatus s){return s==ComplianceStatus.VERIFIED||s==ComplianceStatus.APPROVED||s==ComplianceStatus.WAIVED||s==ComplianceStatus.SUBMITTED||s==ComplianceStatus.UNDER_REVIEW;}
    private void history(MilestoneCompliance c,User actor,String action,String remarks,String filename){ComplianceReviewHistory h=new ComplianceReviewHistory();h.setActor(actor);h.setAction(action);h.setRemarks(remarks);h.setDocumentFileName(filename);c.addHistory(h);}
    private Optional<User> beneficiary(MilestoneCompliance c){return Optional.ofNullable(c.getMilestone()).map(m->m.getInstallment().getPlan().getApplication().getBeneficiary().getUser());}
    private Optional<User> assignedOfficer(MilestoneCompliance c){return Optional.ofNullable(c.getMilestone()).map(m->m.getInstallment().getPlan().getApplication().getAssignedOfficer());}
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
    private ComplianceRequirementResponse toRequirement(ComplianceRequirement r){return new ComplianceRequirementResponse(r.getId(),r.getScheme().getId(),r.getScheme().getSchemeName(),r.getMilestone()==null?null:r.getMilestone().getId(),r.getApplication()==null?null:r.getApplication().getId(),r.getTitle(),r.getDescription(),r.isRequired(),r.getSubmissionDeadline(),r.getAllowedFormats(),r.getMaxFileSizeBytes(),r.isActive());}
    private ComplianceItemResponse toItem(MilestoneCompliance c){DisbursementMilestone m=c.getMilestone();DisbursementInstallment installment=m.getInstallment();DisbursementPlan p=installment.getPlan();GrantApplication a=p.getApplication();String format=c.getRequirement()==null?"pdf,png,jpg,jpeg":c.getRequirement().getAllowedFormats();long max=c.getRequirement()==null?10485760L:c.getRequirement().getMaxFileSizeBytes();return new ComplianceItemResponse(c.getId(),c.getRequirement()==null?null:c.getRequirement().getId(),m.getId(),installment.getId(),m.getTitle(),a.getId(),a.getScheme().getId(),a.getScheme().getSchemeName(),a.getBeneficiary().getUser().getFullName(),c.getRequirementTitle(),c.getDescription(),c.isRequired(),c.getSubmissionDeadline(),format,max,c.getStatus(),c.getDocumentFileName(),c.getDocumentContentType(),c.getDocumentFileSize(),c.getDocumentData()!=null,c.getSubmittedAt(),c.getSubmittedBy()==null?null:c.getSubmittedBy().getFullName(),c.getVerifiedBy()==null?null:c.getVerifiedBy().getFullName(),c.getVerifiedAt(),c.getVerificationRemarks(),c.getReviewHistory().stream().map(h->new ComplianceHistoryResponse(h.getAction(),h.getActor()==null?null:h.getActor().getFullName(),h.getRemarks(),h.getDocumentFileName(),h.getOccurredAt())).toList());}
    private NonComplianceResponse toNonCompliance(ComplianceNonCompliance n){MilestoneCompliance c=n.getCompliance();DisbursementMilestone m=c.getMilestone();GrantApplication a=m.getInstallment().getPlan().getApplication();return new NonComplianceResponse(n.getId(),c.getId(),a.getId(),m.getId(),m.getTitle(),a.getBeneficiary().getUser().getFullName(),n.getReason(),n.getSeverity(),n.getStatus(),n.getDetectedAt(),n.getOfficerRemarks(),n.getHistory().stream().map(h->new NonComplianceHistoryResponse(h.getStatus(),h.getActor()==null?null:h.getActor().getFullName(),h.getRemarks(),h.getOccurredAt())).toList());}
    private ResponseStatusException notFound(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);} private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);} private ResponseStatusException denied(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
}
