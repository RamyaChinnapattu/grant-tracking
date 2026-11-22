package com.grantora.service;

import com.grantora.dto.FundAnalyticsFilter;
import com.grantora.dto.FundAnalyticsResponse;
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

class FundAnalyticsServiceTest {
    private final GrantApplicationRepository applications=mock(GrantApplicationRepository.class);
    private final SchemeRepository schemes=mock(SchemeRepository.class);
    private final DisbursementPlanRepository plans=mock(DisbursementPlanRepository.class);
    private final GrantUtilizationRepository utilization=mock(GrantUtilizationRepository.class);
    private final UserRepository users=mock(UserRepository.class);
    private final FundAnalyticsService service=new FundAnalyticsService(applications,schemes,plans,utilization,users);

    @Test
    void separatesApprovedScheduledReleasedAndReportedAmountsAndKeepsMissingLocationUnassigned(){
        User admin=new User();admin.setRole(Role.ADMIN);when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));
        Scheme scheme=new Scheme();scheme.setId(8L);scheme.setSchemeName("Rural grant");scheme.setAllocatedFunds(new BigDecimal("1000"));
        User beneficiaryUser=new User();beneficiaryUser.setId(21L);Beneficiary beneficiary=new Beneficiary();beneficiary.setUser(beneficiaryUser);
        GrantApplication app=new GrantApplication();ReflectionTestUtils.setField(app,"id",4L);app.setScheme(scheme);app.setBeneficiary(beneficiary);app.setStatus(GrantApplicationStatus.APPROVED);app.setRequestedAmount(new BigDecimal("100"));
        DisbursementPlan plan=new DisbursementPlan();plan.setId(9L);plan.setApplication(app);plan.setScheme(scheme);plan.setPlanStatus(DisbursementPlanStatus.ACTIVE);
        DisbursementInstallment released=new DisbursementInstallment();released.setId(31L);released.setAmount(new BigDecimal("40"));released.setStatus(InstallmentStatus.DISBURSED);plan.addInstallment(released);
        DisbursementInstallment scheduled=new DisbursementInstallment();scheduled.setId(32L);scheduled.setAmount(new BigDecimal("60"));scheduled.setStatus(InstallmentStatus.PLANNED);plan.addInstallment(scheduled);
        DisbursementInstallment cancelled=new DisbursementInstallment();cancelled.setId(33L);cancelled.setAmount(new BigDecimal("25"));cancelled.setStatus(InstallmentStatus.CANCELLED);plan.addInstallment(cancelled);
        when(applications.findAllByOrderByIdDesc()).thenReturn(List.of(app));when(plans.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(plan));when(schemes.findAll()).thenReturn(List.of(scheme));when(utilization.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of());

        FundAnalyticsResponse result=service.summarize(new FundAnalyticsFilter(null,null,null,null,null,null),"admin@test");

        assertEquals(new BigDecimal("100"),result.totalApprovedAmount());
        assertEquals(new BigDecimal("100"),result.totalScheduledAmount());
        assertEquals(new BigDecimal("40"),result.totalReleasedAmount());
        assertEquals(new BigDecimal("0"),result.totalReportedExpenditure());
        assertEquals(new BigDecimal("60"),result.pendingForRelease());
        assertEquals("Not provided",result.regions().get(0).state());
        assertEquals("Not provided",result.regions().get(0).district());
    }

    @Test
    void rejectsNonAdminAnalyticsAccess(){
        User officer=new User();officer.setRole(Role.OFFICER);when(users.findByEmail("officer@test")).thenReturn(Optional.of(officer));
        assertThrows(ResponseStatusException.class,()->service.summarize(new FundAnalyticsFilter(null,null,null,null,null,null),"officer@test"));
        verifyNoInteractions(applications,plans,utilization,schemes);
    }

    @Test
    void returnsZeroTotalsForEmptyDataset(){
        User admin=new User();admin.setRole(Role.ADMIN);when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));when(applications.findAllByOrderByIdDesc()).thenReturn(List.of());when(plans.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());when(utilization.findAllByOrderBySubmittedAtDesc()).thenReturn(List.of());when(schemes.findAll()).thenReturn(List.of());
        FundAnalyticsResponse result=service.summarize(new FundAnalyticsFilter(null,null,null,null,null,null),"admin@test");
        assertEquals(BigDecimal.ZERO,result.totalApprovedAmount());assertTrue(result.regions().isEmpty());assertTrue(result.schemes().isEmpty());
    }
}
