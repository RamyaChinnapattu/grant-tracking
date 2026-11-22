package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
    public GrantUtilizationService(GrantUtilizationRepository records,GrantApplicationRepository applications,
            DisbursementInstallmentRepository installments,MilestoneComplianceRepository evidence,
            UserRepository users,WorkflowAutomationService notifications){this.records=records;this.applications=applications;this.installments=installments;this.evidence=evidence;this.users=users;this.notifications=notifications;}

    @Transactional
    public GrantUtilizationResponse submit(GrantUtilizationRequest request,String email){
        User beneficiary=user(email);if(beneficiary.getRole()!=Role.BENEFICIARY)throw denied("Beneficiary access required.");
        GrantApplication app=applications.findById(request.applicationId()).orElseThrow(()->missing("Application not found."));
        DisbursementInstallment installment=installments.findById(request.installmentId()).orElseThrow(()->missing("Installment not found."));
        validateOwnershipAndRelease(app,installment,beneficiary);
        validateEvidence(request.supportingComplianceIds(),installment);
        validateAmount(request.reportedAmount(),installment,null);
        GrantUtilizationRecord record=new GrantUtilizationRecord();record.setApplication(app);record.setInstallment(installment);record.setSubmittedBy(beneficiary);
        applyRequest(record,request);record.setStatus(GrantUtilizationStatus.SUBMITTED);record.setSubmittedAt(LocalDateTime.now());
        history(record,beneficiary,GrantUtilizationStatus.SUBMITTED,"Initial utilization report submitted.");
        GrantUtilizationRecord saved=records.save(record);
        if(app.getAssignedOfficer()!=null)notifications.notify(app.getAssignedOfficer(),"utilization_submitted:"+saved.getId(),"A utilization report is ready for review for Application #"+app.getId()+".",app.getId());
        return toResponse(saved);
    }

    @Transactional
    public GrantUtilizationResponse replace(Long id,GrantUtilizationRequest request,String email){
        User beneficiary=user(email);if(beneficiary.getRole()!=Role.BENEFICIARY)throw denied("Beneficiary access required.");
        GrantUtilizationRecord record=records.findById(id).orElseThrow(()->missing("Utilization record not found."));
        if(!Objects.equals(record.getSubmittedBy().getId(),beneficiary.getId()))throw denied("You can update only your own utilization reports.");
        if(record.getStatus()!=GrantUtilizationStatus.CORRECTION_REQUIRED && record.getStatus()!=GrantUtilizationStatus.REJECTED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only rejected reports or reports needing correction can be resubmitted.");
        if(!Objects.equals(record.getApplication().getId(),request.applicationId())||!Objects.equals(record.getInstallment().getId(),request.installmentId()))throw bad("Application and installment references cannot change when correcting a report.");
        validateEvidence(request.supportingComplianceIds(),record.getInstallment());validateAmount(request.reportedAmount(),record.getInstallment(),id);
        applyRequest(record,request);record.setStatus(GrantUtilizationStatus.SUBMITTED);record.setSubmittedAt(LocalDateTime.now());record.setVerifiedAt(null);record.setVerifiedBy(null);record.setReviewRemarks(null);
        history(record,beneficiary,GrantUtilizationStatus.SUBMITTED,"Corrected utilization report resubmitted.");return toResponse(records.save(record));
    }

    @Transactional(readOnly=true)
    public List<GrantUtilizationResponse> mine(String email){User beneficiary=user(email);if(beneficiary.getRole()!=Role.BENEFICIARY)throw denied("Beneficiary access required.");return records.findByApplication_Beneficiary_User_IdOrderBySubmittedAtDesc(beneficiary.getId()).stream().map(this::toResponse).toList();}

    @Transactional(readOnly=true)
    public List<GrantUtilizationResponse> reviewQueue(String email){User actor=user(email);if(actor.getRole()!=Role.ADMIN&&actor.getRole()!=Role.OFFICER)throw denied("Officer or admin access required.");return records.findAllByOrderBySubmittedAtDesc().stream().filter(r->r.getStatus()==GrantUtilizationStatus.SUBMITTED||r.getStatus()==GrantUtilizationStatus.UNDER_REVIEW).filter(r->actor.getRole()==Role.ADMIN||assigned(r,actor)).map(this::toResponse).toList();}

    @Transactional
    public GrantUtilizationResponse review(Long id,GrantUtilizationReviewRequest request,String email){
        User actor=user(email);if(actor.getRole()!=Role.ADMIN&&actor.getRole()!=Role.OFFICER)throw denied("Officer or admin access required.");
        GrantUtilizationRecord record=records.findById(id).orElseThrow(()->missing("Utilization record not found."));
        if(actor.getRole()==Role.OFFICER&&!assigned(record,actor))throw denied("This application is outside your assigned workflow.");
        if(record.getStatus()!=GrantUtilizationStatus.SUBMITTED&&record.getStatus()!=GrantUtilizationStatus.UNDER_REVIEW)throw new ResponseStatusException(HttpStatus.CONFLICT,"Only submitted utilization reports can be reviewed.");
        String decision=request.decision().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("APPROVED","REJECTED","CORRECTION_REQUIRED").contains(decision))throw bad("Decision must be APPROVED, REJECTED, or CORRECTION_REQUIRED.");
        if(!decision.equals("APPROVED")&&(request.remarks()==null||request.remarks().isBlank()))throw bad("Remarks are required for rejection or correction.");
        GrantUtilizationStatus status=decision.equals("APPROVED")?GrantUtilizationStatus.VERIFIED:decision.equals("REJECTED")?GrantUtilizationStatus.REJECTED:GrantUtilizationStatus.CORRECTION_REQUIRED;
        record.setStatus(status);record.setVerifiedBy(actor);record.setVerifiedAt(LocalDateTime.now());record.setReviewRemarks(request.remarks());history(record,actor,status,request.remarks());
        GrantApplication app=record.getApplication();if(app.getBeneficiary()!=null&&app.getBeneficiary().getUser()!=null)notifications.notify(app.getBeneficiary().getUser(),"utilization_review:"+record.getId()+":"+record.getVerifiedAt(),"Your utilization report was "+decision.toLowerCase(Locale.ROOT)+"."+(request.remarks()==null?"":" Remarks: "+request.remarks()),app.getId());
        return toResponse(records.save(record));
    }

    private void validateOwnershipAndRelease(GrantApplication app,DisbursementInstallment installment,User beneficiary){
        if(app.getStatus()!=GrantApplicationStatus.APPROVED)throw bad("Utilization can only be reported for an approved application.");
        if(app.getBeneficiary()==null||app.getBeneficiary().getUser()==null||!Objects.equals(app.getBeneficiary().getUser().getId(),beneficiary.getId()))throw denied("This application does not belong to you.");
        if(installment.getPlan()==null||!Objects.equals(installment.getPlan().getApplication().getId(),app.getId()))throw bad("Installment does not belong to this application.");
        if(installment.getStatus()!=InstallmentStatus.DISBURSED)throw bad("Utilization can be reported only against an installment recorded as released.");
    }
    private void validateEvidence(List<Long> ids,DisbursementInstallment installment){
        if(ids==null||ids.isEmpty())throw bad("Attach at least one existing compliance document reference.");
        Set<Long> unique=new HashSet<>(ids);if(unique.size()!=ids.size())throw bad("Duplicate supporting document references are not allowed.");
        for(Long id:unique){MilestoneCompliance item=evidence.findById(id).orElseThrow(()->missing("Supporting compliance document not found."));
            if(item.getMilestone()==null||item.getMilestone().getInstallment()==null||!Objects.equals(item.getMilestone().getInstallment().getId(),installment.getId())||item.getDocumentData()==null||item.getDocumentData().length==0)throw bad("Each supporting document must be attached to this installment.");}
    }
    private void validateAmount(BigDecimal amount,DisbursementInstallment installment,Long currentId){
        if(amount==null||amount.compareTo(BigDecimal.ZERO)<=0)throw bad("Reported expenditure must be greater than zero.");
        BigDecimal existing=records.findAllByOrderBySubmittedAtDesc().stream().filter(r->Objects.equals(r.getInstallment().getId(),installment.getId())&&!Objects.equals(r.getId(),currentId)&&r.getStatus()!=GrantUtilizationStatus.REJECTED).map(GrantUtilizationRecord::getReportedAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(existing.add(amount).compareTo(installment.getAmount())>0)throw bad("Reported expenditure cannot exceed the released installment amount.");
    }
    private void applyRequest(GrantUtilizationRecord r,GrantUtilizationRequest req){r.setReportedAmount(req.reportedAmount());r.setCategory(req.category().trim());r.setDescription(req.description());Set<MilestoneCompliance> attached=new LinkedHashSet<>();for(Long id:req.supportingComplianceIds())attached.add(evidence.findById(id).orElseThrow(()->missing("Supporting compliance document not found.")));r.setSupportingDocuments(attached);}
    private void history(GrantUtilizationRecord r,User actor,GrantUtilizationStatus status,String remarks){UtilizationReviewHistory h=new UtilizationReviewHistory();h.setActor(actor);h.setStatus(status);h.setRemarks(remarks);r.addHistory(h);}
    private boolean assigned(GrantUtilizationRecord r,User actor){return r.getApplication().getAssignedOfficer()!=null&&Objects.equals(r.getApplication().getAssignedOfficer().getId(),actor.getId());}
    private User user(String email){return users.findByEmail(email).orElseThrow(()->missing("User not found."));}
    private GrantUtilizationResponse toResponse(GrantUtilizationRecord r){return new GrantUtilizationResponse(r.getId(),r.getApplication().getId(),r.getInstallment().getId(),r.getInstallment().getInstallmentNumber(),r.getApplication().getScheme().getSchemeName(),r.getApplication().getBeneficiary().getUser().getFullName(),r.getInstallment().getAmount(),r.getReportedAmount(),r.getCategory(),r.getDescription(),r.getSupportingDocuments().stream().map(MilestoneCompliance::getId).toList(),r.getSupportingDocuments().stream().map(MilestoneCompliance::getDocumentFileName).toList(),r.getStatus(),r.getSubmittedAt(),r.getSubmittedBy().getFullName(),r.getVerifiedAt(),r.getVerifiedBy()==null?null:r.getVerifiedBy().getFullName(),r.getReviewRemarks(),r.getHistory().stream().map(h->new UtilizationHistoryResponse(h.getStatus(),h.getActor()==null?null:h.getActor().getFullName(),h.getRemarks(),h.getOccurredAt())).toList());}
    private ResponseStatusException missing(String m){return new ResponseStatusException(HttpStatus.NOT_FOUND,m);}private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}private ResponseStatusException denied(String m){return new ResponseStatusException(HttpStatus.FORBIDDEN,m);}
}
