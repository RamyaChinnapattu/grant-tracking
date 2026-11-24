package com.grantora.service;

import com.grantora.dto.*;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FundAnalyticsService {
    private static final BigDecimal HUNDRED=BigDecimal.valueOf(100);
    private final GrantApplicationRepository applications;private final SchemeRepository schemes;private final DisbursementPlanRepository plans;private final GrantUtilizationRepository utilization;private final UserRepository users;
    public FundAnalyticsService(GrantApplicationRepository applications,SchemeRepository schemes,DisbursementPlanRepository plans,GrantUtilizationRepository utilization,UserRepository users){this.applications=applications;this.schemes=schemes;this.plans=plans;this.utilization=utilization;this.users=users;}

    @Transactional(readOnly=true)
    public FundAnalyticsResponse summarize(FundAnalyticsFilter f,String email){
        User actor=users.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found."));
        if(actor.getRole()!=Role.ADMIN && !actor.isFinanceOfficer())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Admin or Finance Officer analytics access required.");
        if(f.from()!=null&&f.to()!=null&&f.from().isAfter(f.to()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Start date must be on or before end date.");
        List<GrantApplication> all=applications.findAllByOrderByIdDesc();
        List<DisbursementPlan> allPlans=plans.findAllByOrderByCreatedAtDesc();
        Map<Long,DisbursementPlan> planByApp=allPlans.stream().collect(Collectors.toMap(p->p.getApplication().getId(),p->p,(a,b)->a));
        List<GrantApplication> filtered=all.stream().filter(a->matches(a,f,planByApp.get(a.getId()))).toList();
        Set<Long> appIds=filtered.stream().map(GrantApplication::getId).collect(Collectors.toSet());
        List<DisbursementPlan> filteredPlans=allPlans.stream().filter(p->appIds.contains(p.getApplication().getId())).toList();
        List<GrantUtilizationRecord> reports=utilization.findAllByOrderBySubmittedAtDesc().stream().filter(r->appIds.contains(r.getApplication().getId())).toList();
        BigDecimal approved=sum(filtered.stream().filter(a->a.getStatus()==GrantApplicationStatus.APPROVED).map(GrantApplication::getRequestedAmount).toList());
        BigDecimal scheduled=sum(filteredPlans.stream().filter(p->p.getPlanStatus()!=DisbursementPlanStatus.CANCELLED).flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).map(DisbursementInstallment::getAmount).toList());
        BigDecimal released=sum(filteredPlans.stream().flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList());
        BigDecimal reported=sum(reports.stream().map(GrantUtilizationRecord::getReportedAmount).toList());
        BigDecimal verified=sum(reports.stream().filter(r->r.getStatus()==GrantUtilizationStatus.VERIFIED).map(GrantUtilizationRecord::getReportedAmount).toList());
        BigDecimal underReview=sum(reports.stream().filter(r->r.getStatus()==GrantUtilizationStatus.SUBMITTED||r.getStatus()==GrantUtilizationStatus.UNDER_REVIEW).map(GrantUtilizationRecord::getReportedAmount).toList());
        BigDecimal pending=approved.subtract(released).max(BigDecimal.ZERO);
        BigDecimal allocated=sum(schemes.findAll().stream().filter(s->f.schemeId()==null||Objects.equals(s.getId(),f.schemeId())).map(Scheme::getAllocatedFunds).toList());
        List<SchemeFundAnalyticsResponse> schemeRows=schemeRows(filtered,filteredPlans,reports,f.schemeId());
        List<RegionalFundAnalyticsResponse> regions=regionalRows(filtered,filteredPlans,reports);
        Map<InstallmentStatus,List<DisbursementInstallment>> byStatus=filteredPlans.stream().filter(p->p.getPlanStatus()!=DisbursementPlanStatus.CANCELLED).flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).collect(Collectors.groupingBy(DisbursementInstallment::getStatus));
        List<FundAnalyticsStatusBreakdown> statuses=Arrays.stream(InstallmentStatus.values()).filter(s->s!=InstallmentStatus.CANCELLED).map(s->{List<DisbursementInstallment> group=byStatus.getOrDefault(s,List.of());return new FundAnalyticsStatusBreakdown(s.name(),group.size(),sum(group.stream().map(DisbursementInstallment::getAmount).toList()));}).toList();
        return new FundAnalyticsResponse(LocalDateTime.now(),allocated,approved,scheduled,released,reported,verified,released.subtract(reported),pending,underReview,schemeRows,regions,statuses);
    }
    private boolean matches(GrantApplication a,FundAnalyticsFilter f,DisbursementPlan p){
        if(f.schemeId()!=null&&!Objects.equals(a.getScheme().getId(),f.schemeId()))return false;
        if(f.from()!=null&&(a.getSubmittedAt()==null||a.getSubmittedAt().toLocalDate().isBefore(f.from())))return false;
        if(f.to()!=null&&(a.getSubmittedAt()==null||a.getSubmittedAt().toLocalDate().isAfter(f.to())))return false;
        String state=location(a.getBeneficiary()==null?null:a.getBeneficiary().getState());String district=location(a.getBeneficiary()==null?null:a.getBeneficiary().getDistrict());
        if(f.state()!=null&&!f.state().isBlank()&&!state.equalsIgnoreCase(f.state().trim()))return false;
        if(f.district()!=null&&!f.district().isBlank()&&!district.equalsIgnoreCase(f.district().trim()))return false;
        return f.disbursementStatus()==null||f.disbursementStatus().isBlank()||disbursementStatus(p).equalsIgnoreCase(f.disbursementStatus().trim());
    }
    private List<SchemeFundAnalyticsResponse> schemeRows(List<GrantApplication> apps,List<DisbursementPlan> plans,List<GrantUtilizationRecord> reports,Long schemeId){
        return schemes.findAll().stream().map(s->{List<GrantApplication> sa=apps.stream().filter(a->Objects.equals(a.getScheme().getId(),s.getId())).toList();Set<Long> ids=sa.stream().map(GrantApplication::getId).collect(Collectors.toSet());List<DisbursementPlan> sp=plans.stream().filter(p->ids.contains(p.getApplication().getId())).toList();List<GrantUtilizationRecord> sr=reports.stream().filter(r->ids.contains(r.getApplication().getId())).toList();
            BigDecimal approved=sum(sa.stream().filter(a->a.getStatus()==GrantApplicationStatus.APPROVED).map(GrantApplication::getRequestedAmount).toList());
            BigDecimal scheduled=sum(sp.stream().filter(p->p.getPlanStatus()!=DisbursementPlanStatus.CANCELLED).flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).map(DisbursementInstallment::getAmount).toList());
            BigDecimal released=sum(sp.stream().flatMap(p->p.getInstallments().stream()).filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList());
            BigDecimal reported=sum(sr.stream().map(GrantUtilizationRecord::getReportedAmount).toList());BigDecimal verified=sum(sr.stream().filter(r->r.getStatus()==GrantUtilizationStatus.VERIFIED).map(GrantUtilizationRecord::getReportedAmount).toList());
            return new SchemeFundAnalyticsResponse(s.getId(),s.getSchemeName(),s.getDepartment(),zero(s.getAllocatedFunds()),approved,scheduled,released,reported,verified,approved.subtract(released).max(BigDecimal.ZERO),released.subtract(reported),sp.stream().filter(p->p.getPlanStatus()==DisbursementPlanStatus.ACTIVE).count());})
            .filter(r->(schemeId==null||Objects.equals(r.schemeId(),schemeId))&&(!apps.isEmpty()||r.allocatedFunds().signum()>0)).sorted(Comparator.comparing(SchemeFundAnalyticsResponse::schemeName,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private List<RegionalFundAnalyticsResponse> regionalRows(List<GrantApplication> apps,List<DisbursementPlan> plans,List<GrantUtilizationRecord> reports){
        Map<String,List<GrantApplication>> groups=apps.stream().collect(Collectors.groupingBy(a->{DisbursementPlan p=plans.stream().filter(q->Objects.equals(q.getApplication().getId(),a.getId())).findFirst().orElse(null);return String.join("\u001f",location(a.getBeneficiary()==null?null:a.getBeneficiary().getState()),location(a.getBeneficiary()==null?null:a.getBeneficiary().getDistrict()),String.valueOf(a.getScheme().getId()),a.getStatus().name(),disbursementStatus(p));}));
        List<RegionalFundAnalyticsResponse> rows=new ArrayList<>();for(List<GrantApplication> group:groups.values()){
            GrantApplication first=group.get(0);DisbursementPlan p=plans.stream().filter(q->Objects.equals(q.getApplication().getId(),first.getId())).findFirst().orElse(null);String ds=disbursementStatus(p);Set<Long> ids=group.stream().map(GrantApplication::getId).collect(Collectors.toSet());List<DisbursementPlan> groupPlans=plans.stream().filter(q->ids.contains(q.getApplication().getId())).toList();List<GrantUtilizationRecord> ur=reports.stream().filter(r->ids.contains(r.getApplication().getId())).toList();
            BigDecimal approved=sum(group.stream().filter(a->a.getStatus()==GrantApplicationStatus.APPROVED).map(GrantApplication::getRequestedAmount).toList());BigDecimal released=sum(groupPlans.stream().flatMap(q->q.getInstallments().stream()).filter(i->i.getStatus()==InstallmentStatus.DISBURSED).map(DisbursementInstallment::getAmount).toList());BigDecimal scheduled=sum(groupPlans.stream().filter(q->q.getPlanStatus()!=DisbursementPlanStatus.CANCELLED).flatMap(q->q.getInstallments().stream()).filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).map(DisbursementInstallment::getAmount).toList());BigDecimal reported=sum(ur.stream().map(GrantUtilizationRecord::getReportedAmount).toList());BigDecimal verified=sum(ur.stream().filter(r->r.getStatus()==GrantUtilizationStatus.VERIFIED).map(GrantUtilizationRecord::getReportedAmount).toList());
            rows.add(new RegionalFundAnalyticsResponse(location(first.getBeneficiary()==null?null:first.getBeneficiary().getState()),location(first.getBeneficiary()==null?null:first.getBeneficiary().getDistrict()),null,first.getScheme().getId(),first.getScheme().getSchemeName(),first.getStatus().name(),ds,group.size(),group.stream().filter(a->a.getStatus()==GrantApplicationStatus.APPROVED).count(),approved,scheduled,released,reported,verified,approved.subtract(released).max(BigDecimal.ZERO)));
        }
        return rows.stream().sorted(Comparator.comparing(RegionalFundAnalyticsResponse::state).thenComparing(RegionalFundAnalyticsResponse::district).thenComparing(RegionalFundAnalyticsResponse::schemeName).thenComparing(RegionalFundAnalyticsResponse::applicationStatus).thenComparing(RegionalFundAnalyticsResponse::disbursementStatus)).toList();
    }
    private String disbursementStatus(DisbursementPlan p){if(p==null)return "UNPLANNED";if(p.getPlanStatus()==DisbursementPlanStatus.CANCELLED)return "CANCELLED";List<DisbursementInstallment> is=p.getInstallments().stream().filter(i->i.getStatus()!=InstallmentStatus.CANCELLED).toList();if(is.isEmpty())return "NO_ACTIVE_INSTALLMENTS";if(is.stream().allMatch(i->i.getStatus()==InstallmentStatus.DISBURSED))return "COMPLETED";if(is.stream().anyMatch(i->i.getStatus()==InstallmentStatus.ON_HOLD))return "ON_HOLD";if(is.stream().anyMatch(i->i.getStatus()==InstallmentStatus.READY_FOR_DISBURSEMENT))return "READY_FOR_RELEASE";return "PLANNED";}
    private String location(String v){return v==null||v.isBlank()?"Not provided":v.trim();}
    private BigDecimal sum(List<BigDecimal> values){return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private BigDecimal zero(BigDecimal value){return value==null?BigDecimal.ZERO:value;}
}
