package com.grantora.service;
import com.grantora.dto.DisbursementDashboardResponse;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Milestone3DashboardServiceTest {
    private final GrantApplicationRepository applications=mock(GrantApplicationRepository.class);
    private final DisbursementPlanRepository plans=mock(DisbursementPlanRepository.class);
    private final DisbursementMilestoneRepository milestones=mock(DisbursementMilestoneRepository.class);
    private final MilestoneComplianceRepository compliances=mock(MilestoneComplianceRepository.class);
    private final ComplianceNonComplianceRepository cases=mock(ComplianceNonComplianceRepository.class);
    private final GrantUtilizationRepository utilization=mock(GrantUtilizationRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final Milestone3DashboardService service=new Milestone3DashboardService(applications,plans,milestones,compliances,cases,utilization,users);

    @Test void dashboardSeparatesPlannedAndReleasedAndOmitsCancelledInstallments(){
        User admin=new User();admin.setRole(Role.ADMIN);when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        Scheme scheme=new Scheme();scheme.setId(7L);scheme.setSchemeName("Water grant");
        GrantApplication app=new GrantApplication();ReflectionTestUtils.setField(app,"id",5L);app.setScheme(scheme);app.setStatus(GrantApplicationStatus.APPROVED);app.setRequestedAmount(new BigDecimal("100"));
        DisbursementPlan plan=new DisbursementPlan();plan.setId(9L);plan.setApplication(app);plan.setScheme(scheme);plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        DisbursementInstallment released=new DisbursementInstallment();released.setId(2L);released.setInstallmentNumber(1);released.setAmount(new BigDecimal("40"));released.setStatus(InstallmentStatus.DISBURSED);plan.addInstallment(released);
        DisbursementInstallment scheduled=new DisbursementInstallment();scheduled.setId(3L);scheduled.setInstallmentNumber(2);scheduled.setAmount(new BigDecimal("60"));scheduled.setStatus(InstallmentStatus.PLANNED);plan.addInstallment(scheduled);
        DisbursementInstallment cancelled=new DisbursementInstallment();cancelled.setId(4L);cancelled.setInstallmentNumber(3);cancelled.setAmount(new BigDecimal("20"));cancelled.setStatus(InstallmentStatus.CANCELLED);plan.addInstallment(cancelled);
        when(applications.findAllByOrderByIdDesc()).thenReturn(List.of(app));when(plans.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(plan));when(compliances.findAll()).thenReturn(List.of());when(cases.findAllByOrderByDetectedAtDesc()).thenReturn(List.of());when(milestones.findAllDetailed()).thenReturn(List.of());when(utilization.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of());

        DisbursementDashboardResponse response=service.adminDashboard("admin@test","",null,null,null,null,null,0,20,"applicationId","desc");
        assertEquals(new BigDecimal("100"),response.approvedAmount());assertEquals(new BigDecimal("100"),response.scheduledAmount());assertEquals(new BigDecimal("40"),response.releasedAmount());assertEquals(new BigDecimal("60"),response.pendingDisbursement());assertEquals(1,response.rows().size());assertEquals(2,response.rows().get(0).nextInstallmentNumber());
    }

    @Test void dashboardRejectsNonAdminAndDoesNotLoadRecords(){
        User officer=new User();officer.setRole(Role.OFFICER);when(users.findByEmail("officer@test")).thenReturn(Optional.of(officer));
        assertThrows(ResponseStatusException.class,()->service.adminDashboard("officer@test",null,null,null,null,null,null,0,20,"applicationId","desc"));
        verifyNoInteractions(applications,plans,milestones,compliances,cases,utilization);
    }
}
