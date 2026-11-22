package com.grantora.service;
import com.grantora.entity.Role;
import com.grantora.entity.User;
import com.grantora.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Milestone3ReportServiceTest {
    private final UserRepository users=mock(UserRepository.class);private final GrantApplicationRepository applications=mock(GrantApplicationRepository.class);
    private final DisbursementPlanRepository plans=mock(DisbursementPlanRepository.class);private final MilestoneComplianceRepository compliances=mock(MilestoneComplianceRepository.class);
    private final ComplianceNonComplianceRepository cases=mock(ComplianceNonComplianceRepository.class);private final GrantUtilizationRepository utilization=mock(GrantUtilizationRepository.class);private final SchemeRepository schemes=mock(SchemeRepository.class);
    private final Milestone3ReportService service=new Milestone3ReportService(users,applications,plans,compliances,cases,utilization,schemes);

    @Test void rejectsOfficerExportBeforeReadingGrantData(){User officer=new User();officer.setRole(Role.OFFICER);when(users.findByEmail("officer@test")).thenReturn(Optional.of(officer));assertThrows(ResponseStatusException.class,()->service.csv("disbursement","officer@test",null,null,null,null,null,null,null));verifyNoInteractions(applications,plans,compliances,cases,utilization,schemes);}
    @Test void rejectsInvalidDateRange(){User admin=new User();admin.setRole(Role.ADMIN);when(users.findByEmail("admin@test")).thenReturn(Optional.of(admin));assertThrows(ResponseStatusException.class,()->service.csv("regional","admin@test",LocalDate.parse("2026-02-02"),LocalDate.parse("2026-01-01"),null,null,null,null,null));verifyNoInteractions(applications,plans,compliances,cases,utilization,schemes);}
}
