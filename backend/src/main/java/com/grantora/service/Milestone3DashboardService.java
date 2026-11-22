package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class Milestone3DashboardService {
    private final GrantApplicationRepository applications; private final DisbursementPlanRepository plans;
    private final DisbursementMilestoneRepository milestones; private final MilestoneComplianceRepository compliances;
    private final ComplianceNonComplianceRepository cases; private final GrantUtilizationRepository utilization;
    private final UserRepository users;
    public Milestone3DashboardService(GrantApplicationRepository applications,DisbursementPlanRepository plans,
            DisbursementMilestoneRepository milestones,MilestoneComplianceRepository compliances,
            ComplianceNonComplianceRepository cases,GrantUtilizationRepository utilization,UserRepository users){
        this.applications=applications;this.plans=plans;this.milestones=milestones;this.compliances=compliances;
        this.cases=cases;this.utilization=utilization;this.users=users;
    }

    @Transactional(readOnly=true)
    public DisbursementDashboardResponse adminDashboard(String email,String search,Long schemeId,String state,String district,
            String applicationStatus,String disbursementStatus,int page,int pageSize,String sort,String direction){
        User actor=users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
        if(actor.getRole()!=Role.ADMIN)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin dashboard access required.");
        if(page<0||pageSize<1||pageSize>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page must be nonnegative and page size must be between 1 and 100.");
        List<DisbursementPlan> allPlans=plans.findAllByOrderByCreatedAtDesc();
        Map<Long,DisbursementPlan> byApplication=allPlans.stream().collect(Collectors.toMap(p->p.getApplication().getId(),p->p,(a,b)->a));
        List<GrantApplication> matched=applications.findAllByOrderByIdDesc().stream().filter(a->matches(a,byApplication.get(a.getId()),search,schemeId,state,district,applicationStatus,disbursementStatus)).toList();
        Set<Long> ids=matched.stream().map(GrantApplication::getId).collect(Collectors.toSet());
        List<DisbursementPlan> selected=allPlans.stream().filter(p->ids.contains(p.getApplication().getId())).toList();
        LocalDate today=LocalDate.now();
        List<DisbursementInstallment> selectedInstallments=selected.stream().filter(p->p.getPlanStatus()!=DisbursementPlanStatus.CANCELLED).flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).toList();
        BigDecimal approved=sum(matched.stream().filter(a->a.getStatus()==GrantApplicationStatus.APPROVED).map(GrantApplication::getRequestedAmount).toList());
        BigDecimal scheduled=sum(selectedInstallments.stream().map(DisbursementInstallment::getAmount).toList());
        BigDecimal released=sum(selectedInstallments.stream().filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList());
        List<MilestoneCompliance> cs=compliances.findAll().stream().filter(c->ids.contains(c.getMilestone().getInstallment().getPlan().getApplication().getId())).toList();
        List<MilestoneCompliance> required=cs.stream().filter(MilestoneCompliance::isRequired).toList();
        long completed=required.stream().filter(c->c.getStatus()==ComplianceStatus.APPROVED||c.getStatus()==ComplianceStatus.VERIFIED||c.getStatus()==ComplianceStatus.WAIVED).count();
        double completion=required.isEmpty()?0d:completed*100d/required.size();
        Set<Long> selectedComplianceIds=cs.stream().map(MilestoneCompliance::getId).collect(Collectors.toSet());
        int openCases=(int)cases.findAllByOrderByDetectedAtDesc().stream().filter(c->selectedComplianceIds.contains(c.getCompliance().getId())).filter(c->c.getStatus()==NonComplianceStatus.OPEN||c.getStatus()==NonComplianceStatus.UNDER_REVIEW).count();
        List<DisbursementMilestone> selectedMilestones=milestones.findAllDetailed().stream().filter(m->ids.contains(m.getInstallment().getPlan().getApplication().getId())).toList();
        int delayed=(int)selectedMilestones.stream().filter(m->m.getDueDate()!=null&&m.getDueDate().isBefore(today)&&m.getStatus()!=MilestoneStatus.COMPLETED&&m.getStatus()!=MilestoneStatus.CANCELLED).count();
        int upcoming=(int)selectedInstallments.stream().filter(i->(i.getStatus()==InstallmentStatus.PLANNED||i.getStatus()==InstallmentStatus.READY_FOR_DISBURSEMENT)&&i.getPlannedReleaseDate()!=null&&!i.getPlannedReleaseDate().isBefore(today)&&!i.getPlannedReleaseDate().isAfter(today.plusDays(30))).count();
        List<GrantUtilizationRecord> reports=utilization.findAllByOrderBySubmittedAtDesc().stream().filter(r->ids.contains(r.getApplication().getId())).toList();
        BigDecimal reported=sum(reports.stream().map(GrantUtilizationRecord::getReportedAmount).toList());
        BigDecimal verified=sum(reports.stream().filter(r->r.getStatus()==GrantUtilizationStatus.VERIFIED).map(GrantUtilizationRecord::getReportedAmount).toList());
        Comparator<GrantApplication> comparator=sortComparator(sort,byApplication);
        if("desc".equalsIgnoreCase(direction))comparator=comparator.reversed();
        List<GrantApplication> sorted=matched.stream().sorted(comparator).toList();long total=sorted.size();int start=(int)Math.min(sorted.size(),(long)page*pageSize);
        List<DisbursementDashboardRow> rows=sorted.subList(start,Math.min(sorted.size(),start+pageSize)).stream().map(a->row(a,byApplication.get(a.getId()))).toList();
        return new DisbursementDashboardResponse(approved,scheduled,released,approved.subtract(released).max(BigDecimal.ZERO),upcoming,delayed,completion,openCases,reported,verified,total,page,pageSize,rows);
    }

    @Transactional(readOnly=true)
    public List<DisbursementMilestoneResponse> assignedMilestones(String email){
        User actor=users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
        if(actor.getRole()!=Role.ADMIN&&actor.getRole()!=Role.OFFICER)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Officer or admin access required.");
        return milestones.findAllDetailed().stream().filter(m->actor.getRole()==Role.ADMIN||m.getInstallment().getPlan().getApplication().getAssignedOfficer()!=null&&Objects.equals(m.getInstallment().getPlan().getApplication().getAssignedOfficer().getId(),actor.getId())).map(m->new DisbursementMilestoneResponse(m.getId(),m.getInstallment().getId(),m.getInstallment().getInstallmentNumber(),m.getInstallment().getTitle(),m.getInstallment().getPlan().getId(),m.getInstallment().getPlan().getApplication().getId(),m.getInstallment().getPlan().getScheme().getSchemeName(),m.getInstallment().getPlan().getApplication().getBeneficiary().getUser().getFullName(),m.getTitle(),m.getDescription(),m.getDueDate(),m.getAmount(),m.getStatus(),m.getProgressPercentage(),m.getNonCompliant(),m.getNonComplianceReason(),m.getCompletedAt(),m.getCompliances().stream().map(c->new MilestoneComplianceResponse(c.getId(),m.getId(),m.getTitle(),c.getRequirementTitle(),c.getDescription(),c.getSubmissionNotes(),c.getStatus(),c.getDocumentFileName(),c.getDocumentContentType(),c.getDocumentFileSize(),c.getDocumentData()!=null,c.getSubmittedAt(),c.getSubmittedBy()==null?null:c.getSubmittedBy().getFullName(),c.getVerifiedBy()==null?null:c.getVerifiedBy().getFullName(),c.getVerifiedAt(),c.getVerificationRemarks())).toList())).toList();
    }
    private DisbursementDashboardRow row(GrantApplication a,DisbursementPlan p){
        List<DisbursementInstallment> installments=p==null?List.of():p.getInstallments();
        BigDecimal released=sum(installments.stream().filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList());
        DisbursementInstallment next=installments.stream().filter(i->i.getStatus()!=InstallmentStatus.DISBURSED&&i.getStatus()!=InstallmentStatus.CANCELLED).min(Comparator.comparing(DisbursementInstallment::getPlannedReleaseDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(DisbursementInstallment::getInstallmentNumber)).orElse(null);
        List<MilestoneCompliance> obligations=p==null?List.of():p.getInstallments().stream().flatMap(i->i.getMilestones().stream()).flatMap(m->m.getCompliances().stream()).toList();
        String compliance=obligations.isEmpty()?"NOT_CONFIGURED":obligations.stream().allMatch(c->c.getStatus()==ComplianceStatus.APPROVED||c.getStatus()==ComplianceStatus.VERIFIED||c.getStatus()==ComplianceStatus.WAIVED)?"COMPLETE":obligations.stream().anyMatch(c->c.getStatus()==ComplianceStatus.OVERDUE)?"OVERDUE":"PENDING";
        List<DisbursementInstallment> active=installments.stream().filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).toList();
        String ds=p==null?"UNPLANNED":p.getPlanStatus()==DisbursementPlanStatus.CANCELLED?"CANCELLED":active.isEmpty()?"NO_ACTIVE_INSTALLMENTS":active.stream().allMatch(i->i.getStatus()==InstallmentStatus.DISBURSED)?"COMPLETED":active.stream().anyMatch(i->i.getStatus()==InstallmentStatus.ON_HOLD)?"ON_HOLD":active.stream().anyMatch(i->i.getStatus()==InstallmentStatus.READY_FOR_DISBURSEMENT)?"READY_FOR_RELEASE":"PLANNED";
        return new DisbursementDashboardRow(a.getId(),a.getBeneficiary()==null||a.getBeneficiary().getUser()==null?"":a.getBeneficiary().getUser().getFullName(),a.getScheme().getSchemeName(),a.getBeneficiary()==null?null:a.getBeneficiary().getState(),a.getBeneficiary()==null?null:a.getBeneficiary().getDistrict(),a.getStatus().name(),ds,a.getStatus()==GrantApplicationStatus.APPROVED?a.getRequestedAmount():BigDecimal.ZERO,released,next==null?null:next.getInstallmentNumber(),next==null?null:next.getAmount(),next==null?null:next.getPlannedReleaseDate(),compliance);
    }
    private boolean matches(GrantApplication a,DisbursementPlan p,String search,Long schemeId,String state,String district,String appStatus,String disbStatus){
        if(schemeId!=null&&!Objects.equals(a.getScheme().getId(),schemeId))return false;
        if(state!=null&&!state.isBlank()&&!safe(a.getBeneficiary()==null?null:a.getBeneficiary().getState()).equalsIgnoreCase(state.trim()))return false;
        if(district!=null&&!district.isBlank()&&!safe(a.getBeneficiary()==null?null:a.getBeneficiary().getDistrict()).equalsIgnoreCase(district.trim()))return false;
        if(appStatus!=null&&!appStatus.isBlank()&&!a.getStatus().name().equalsIgnoreCase(appStatus.trim()))return false;
        if(disbStatus!=null&&!disbStatus.isBlank()&&!disbursementStatus(p).equalsIgnoreCase(disbStatus.trim()))return false;
        return search==null||search.isBlank()||String.valueOf(a.getId()).contains(search.trim())||safe(a.getBeneficiary()==null||a.getBeneficiary().getUser()==null?null:a.getBeneficiary().getUser().getFullName()).toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT))||a.getScheme().getSchemeName().toLowerCase(Locale.ROOT).contains(search.trim().toLowerCase(Locale.ROOT));
    }
    private Comparator<GrantApplication> sortComparator(String sort,Map<Long,DisbursementPlan> plans){return switch(sort==null?"applicationId":sort){case "beneficiary"->Comparator.comparing((GrantApplication a)->safe(a.getBeneficiary()==null||a.getBeneficiary().getUser()==null?null:a.getBeneficiary().getUser().getFullName()),String.CASE_INSENSITIVE_ORDER);case "scheme"->Comparator.comparing((GrantApplication a)->a.getScheme().getSchemeName(),String.CASE_INSENSITIVE_ORDER);case "approvedAmount"->Comparator.comparing((GrantApplication a)->a.getStatus()==GrantApplicationStatus.APPROVED?a.getRequestedAmount():BigDecimal.ZERO);case "releasedAmount"->Comparator.comparing((GrantApplication a)->sum(Optional.ofNullable(plans.get(a.getId())).map(DisbursementPlan::getInstallments).orElse(List.of()).stream().filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList()));case "nextInstallmentDate"->Comparator.comparing((GrantApplication a)->Optional.ofNullable(plans.get(a.getId())).map(DisbursementPlan::getInstallments).orElse(List.of()).stream().filter(i->i.getStatus()!=InstallmentStatus.DISBURSED&&i.getStatus()!=InstallmentStatus.CANCELLED).map(DisbursementInstallment::getPlannedReleaseDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null),Comparator.nullsLast(Comparator.naturalOrder()));case "complianceStatus"->Comparator.comparing((GrantApplication a)->row(a,plans.get(a.getId())).complianceStatus());case "disbursementStatus"->Comparator.comparing((GrantApplication a)->disbursementStatus(plans.get(a.getId())));case "status"->Comparator.comparing((GrantApplication a)->a.getStatus().name());default->Comparator.comparing(GrantApplication::getId);};}
    private String disbursementStatus(DisbursementPlan p){if(p==null)return "UNPLANNED";if(p.getPlanStatus()==DisbursementPlanStatus.CANCELLED)return "CANCELLED";List<DisbursementInstallment> active=p.getInstallments().stream().filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).toList();if(active.isEmpty())return "NO_ACTIVE_INSTALLMENTS";if(active.stream().allMatch(i->i.getStatus()==InstallmentStatus.DISBURSED))return "COMPLETED";if(active.stream().anyMatch(i->i.getStatus()==InstallmentStatus.ON_HOLD))return "ON_HOLD";if(active.stream().anyMatch(i->i.getStatus()==InstallmentStatus.READY_FOR_DISBURSEMENT))return "READY_FOR_RELEASE";return "PLANNED";}
    private String safe(String s){return s==null?"":s.trim();} private BigDecimal sum(List<BigDecimal> xs){return xs.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
}
