package com.grantora.service;

import com.grantora.dto.ComplianceReviewRequest;
import com.grantora.dto.ComplianceRequirementRequest;
import com.grantora.entity.*;
import com.grantora.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComplianceOperationsServiceTest {
    private ComplianceRequirementRepository requirementRepo;
    private MilestoneComplianceRepository complianceRepo;
    private ComplianceReminderHistoryRepository reminderRepo;
    private ComplianceNonComplianceRepository nonComplianceRepo;
    private DisbursementMilestoneRepository milestoneRepo;
    private DisbursementPlanRepository planRepo;
    private SchemeRepository schemeRepo;
    private GrantApplicationRepository applicationRepo;
    private UserRepository userRepo;
    private WorkflowAutomationService notificationService;
    private DisbursementReminderConfigRepository configRepo;
    private ComplianceOperationsService service;
    private User admin, officer, beneficiaryUser;
    private Scheme scheme;
    private GrantApplication application;
    private MilestoneCompliance compliance;

    @BeforeEach void setup(){
        requirementRepo=mock(ComplianceRequirementRepository.class);complianceRepo=mock(MilestoneComplianceRepository.class);
        reminderRepo=mock(ComplianceReminderHistoryRepository.class);nonComplianceRepo=mock(ComplianceNonComplianceRepository.class);
        milestoneRepo=mock(DisbursementMilestoneRepository.class);planRepo=mock(DisbursementPlanRepository.class);
        schemeRepo=mock(SchemeRepository.class);applicationRepo=mock(GrantApplicationRepository.class);userRepo=mock(UserRepository.class);
        notificationService=mock(WorkflowAutomationService.class);configRepo=mock(DisbursementReminderConfigRepository.class);
        service=new ComplianceOperationsService(requirementRepo,complianceRepo,reminderRepo,nonComplianceRepo,milestoneRepo,planRepo,schemeRepo,applicationRepo,userRepo,notificationService,configRepo);
        admin=user(1L,Role.ADMIN);officer=user(2L,Role.OFFICER);beneficiaryUser=user(3L,Role.BENEFICIARY);
        scheme=new Scheme();scheme.setId(20L);scheme.setSchemeName("Farm grant");
        Beneficiary beneficiary=new Beneficiary();beneficiary.setUser(beneficiaryUser);
        application=new GrantApplication();application.setBeneficiary(beneficiary);application.setScheme(scheme);
        application.setRequestedAmount(BigDecimal.valueOf(1000));application.setStatus(GrantApplicationStatus.APPROVED);
        application.setAssignedOfficer(officer);
        DisbursementPlan plan=new DisbursementPlan();plan.setApplication(application);plan.setScheme(scheme);
        DisbursementInstallment installment=new DisbursementInstallment();installment.setPlan(plan);
        DisbursementMilestone milestone=new DisbursementMilestone();milestone.setId(31L);milestone.setTitle("Progress report");milestone.setInstallment(installment);
        compliance=new MilestoneCompliance();compliance.setId(41L);compliance.setMilestone(milestone);compliance.setRequirementTitle("Progress report");compliance.setStatus(ComplianceStatus.SUBMITTED);
    }
    private User user(Long id,Role role){User u=new User();u.setId(id);u.setRole(role);u.setFullName(role.name());u.setEmail(role.name().toLowerCase()+"@example.test");return u;}

    @Test void adminCanConfigureMilestoneRequirement(){
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));when(schemeRepo.findById(20L)).thenReturn(Optional.of(scheme));
        when(milestoneRepo.findById(31L)).thenReturn(Optional.of(compliance.getMilestone()));when(requirementRepo.findByActiveTrue()).thenReturn(List.of());
        when(requirementRepo.save(any())).thenAnswer(inv->{ComplianceRequirement r=inv.getArgument(0);r.setId(8L);return r;});
        when(planRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        var response=service.createRequirement(new ComplianceRequirementRequest(20L,31L,null,"Progress report","Submit progress",true,LocalDate.now().plusDays(5),"pdf,png",1048576),admin.getEmail());
        assertEquals("Progress report",response.title());assertEquals("pdf,png",response.allowedFormats());verify(requirementRepo).save(any());
    }

    @Test void officerReviewRecordsApprovalAndRejectsUnassignedWork(){
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        when(complianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));
        var response=service.review(41L,new ComplianceReviewRequest("APPROVED",null),officer.getEmail());
        assertEquals(ComplianceStatus.APPROVED,response.status());assertEquals("APPROVED",response.reviewHistory().get(0).action());
        User stranger=user(9L,Role.OFFICER);when(userRepo.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        compliance.setStatus(ComplianceStatus.SUBMITTED);
        assertThrows(ResponseStatusException.class,()->service.review(41L,new ComplianceReviewRequest("APPROVED",null),stranger.getEmail()));
    }

    @Test void reviewRequiresRemarksForCorrection(){
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->service.review(41L,new ComplianceReviewRequest("CORRECTION_REQUIRED"," "),officer.getEmail()));
        assertTrue(ex.getReason().contains("Remarks are required"));
    }

    @Test void beneficiaryCanReplaceARequestedCorrection() throws Exception {
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));when(complianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));
        ComplianceRequirement req=new ComplianceRequirement();req.setAllowedFormats("pdf");req.setMaxFileSizeBytes(2048);compliance.setRequirement(req);compliance.setStatus(ComplianceStatus.CORRECTION_REQUIRED);
        MultipartFile file=mock(MultipartFile.class);when(file.isEmpty()).thenReturn(false);when(file.getOriginalFilename()).thenReturn("corrected.pdf");
        when(file.getContentType()).thenReturn("application/pdf");when(file.getSize()).thenReturn(8L);when(file.getBytes()).thenReturn("%PDF-1.7".getBytes());
        var response=service.submit(41L,"Updated statement",file,beneficiaryUser.getEmail());
        assertEquals(ComplianceStatus.SUBMITTED,response.status());assertEquals("corrected.pdf",response.documentFileName());
        assertEquals("SUBMITTED",response.reviewHistory().get(0).action());
    }

    @Test void beneficiaryCannotReadAnotherBeneficiarysCompliance(){
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));when(complianceRepo.findAll()).thenReturn(List.of(compliance));
        when(requirementRepo.findByActiveTrue()).thenReturn(List.of());when(planRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        assertEquals(1,service.getMine(beneficiaryUser.getEmail()).size());
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        assertThrows(ResponseStatusException.class,()->service.getMine(admin.getEmail()));
    }

    @Test void duplicateReminderEventIsNotRecordedTwice(){
        ComplianceRequirement req=new ComplianceRequirement();req.setId(8L);req.setScheme(scheme);req.setTitle("Progress report");req.setActive(true);req.setAllowedFormats("pdf");
        compliance.setRequirement(req);compliance.setRequired(true);compliance.setSubmissionDeadline(LocalDate.now().plusDays(3));compliance.setStatus(ComplianceStatus.PENDING);
        when(requirementRepo.findByActiveTrue()).thenReturn(List.of(req));when(planRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(complianceRepo.existsByRequirement_IdAndMilestone_Id(8L,31L)).thenReturn(true);when(complianceRepo.findAll()).thenReturn(List.of(compliance));
        when(configRepo.findById(1L)).thenReturn(Optional.of(new DisbursementReminderConfig()));
        when(reminderRepo.existsByCompliance_IdAndEventTypeAndEventDate(eq(41L),eq("UPCOMING"),any())).thenReturn(true);
        service.scanDeadlines();verify(reminderRepo,never()).save(any());verify(notificationService,never()).notify(any(),anyString(),anyString(),any());
    }

    @Test void overdueMandatoryRequirementCreatesNonComplianceCase(){
        ComplianceRequirement req=new ComplianceRequirement();req.setId(8L);req.setScheme(scheme);req.setTitle("Progress report");req.setActive(true);req.setAllowedFormats("pdf");
        compliance.setRequirement(req);compliance.setRequired(true);compliance.setSubmissionDeadline(LocalDate.now().minusDays(1));compliance.setStatus(ComplianceStatus.PENDING);
        when(requirementRepo.findByActiveTrue()).thenReturn(List.of(req));when(planRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(complianceRepo.existsByRequirement_IdAndMilestone_Id(8L,31L)).thenReturn(true);when(complianceRepo.findAll()).thenReturn(List.of(compliance));
        when(configRepo.findById(1L)).thenReturn(Optional.of(new DisbursementReminderConfig()));when(nonComplianceRepo.findByCompliance_Id(41L)).thenReturn(Optional.empty());
        when(reminderRepo.existsByCompliance_IdAndEventTypeAndEventDate(eq(41L),eq("OVERDUE"),any())).thenReturn(false);
        when(nonComplianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));when(reminderRepo.save(any())).thenAnswer(inv->inv.getArgument(0));
        var result=service.scanDeadlines();
        assertEquals(ComplianceStatus.OVERDUE,compliance.getStatus());assertEquals(1,result.get("overdueDetected"));verify(nonComplianceRepo).save(any());
    }

    @Test void assignedOfficerCanResolveCaseAndResolutionIsRecorded(){
        ComplianceNonCompliance item=new ComplianceNonCompliance();ReflectionTestUtils.setField(item,"id",17L);item.setCompliance(compliance);item.setReason("Late report");item.setSeverity("MEDIUM");
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));when(nonComplianceRepo.findById(17L)).thenReturn(Optional.of(item));
        when(nonComplianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));
        var response=service.resolveNonCompliance(17L,new com.grantora.dto.NonComplianceResolutionRequest(NonComplianceStatus.RESOLVED,"Report received and checked."),officer.getEmail());
        assertEquals(NonComplianceStatus.RESOLVED,response.status());assertEquals(1,response.history().size());
        assertEquals("Report received and checked.",response.history().get(0).remarks());
    }
}
