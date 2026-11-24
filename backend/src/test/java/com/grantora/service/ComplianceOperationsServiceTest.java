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
        compliance.setDocumentData("%PDF-1.7 dummy".getBytes());
        milestone.addCompliance(compliance);
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

    @Test void officerReviewCannotApproveWithoutDocumentData(){
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));
        compliance.setDocumentData(null);
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->
                service.review(41L,new ComplianceReviewRequest("APPROVED",null),officer.getEmail()));
        assertTrue(ex.getReason().contains("Cannot approve compliance evidence without an uploaded supporting document"));
    }

    @Test void officerReviewApprovalCompletesMilestone(){
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        when(complianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));

        service.review(41L,new ComplianceReviewRequest("APPROVED","Approved by officer"),officer.getEmail());

        assertEquals(MilestoneStatus.COMPLETED, compliance.getMilestone().getStatus());
        assertEquals(100, compliance.getMilestone().getProgressPercentage());
        assertFalse(compliance.getMilestone().getNonCompliant());
        verify(milestoneRepo).save(compliance.getMilestone());
    }

    @Test void officerReviewRejectionFlagsMilestoneNonCompliant(){
        when(userRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        when(complianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));

        service.review(41L,new ComplianceReviewRequest("REJECTED","Invalid receipts"),officer.getEmail());

        assertTrue(compliance.getMilestone().getNonCompliant());
        assertTrue(compliance.getMilestone().getNonComplianceReason().contains("rejected"));
        verify(milestoneRepo).save(compliance.getMilestone());
    }

    @Test void adminWaiverCompletesMilestone(){
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        when(complianceRepo.save(any())).thenAnswer(inv->inv.getArgument(0));

        service.waive(41L,new com.grantora.dto.ComplianceWaiverRequest("Waived by special order"),admin.getEmail());

        assertEquals(MilestoneStatus.COMPLETED, compliance.getMilestone().getStatus());
        assertEquals(100, compliance.getMilestone().getProgressPercentage());
        assertFalse(compliance.getMilestone().getNonCompliant());
        verify(milestoneRepo).save(compliance.getMilestone());
    }

    @Test void unauthorizedUserCannotDownloadComplianceDocument(){
        User stranger=user(99L,Role.BENEFICIARY);
        when(userRepo.findByEmail(stranger.getEmail())).thenReturn(Optional.of(stranger));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));

        ResponseStatusException ex=assertThrows(ResponseStatusException.class,()->
                service.document(41L,stranger.getEmail()));
        assertTrue(ex.getReason().contains("You do not have access to this compliance document"));
    }

    @Test void getMineHandlesOrphanedAndIncompleteRelationshipsSafely(){
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));
        when(requirementRepo.findByActiveTrue()).thenReturn(List.of());
        when(planRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        // 1. Orphan: milestone is null
        MilestoneCompliance cNullMilestone = new MilestoneCompliance();
        cNullMilestone.setId(101L);
        cNullMilestone.setMilestone(null);

        // 2. Orphan: milestone without installment
        DisbursementMilestone mNoInst = new DisbursementMilestone();
        mNoInst.setId(102L);
        mNoInst.setInstallment(null);
        MilestoneCompliance cNoInst = new MilestoneCompliance();
        cNoInst.setId(103L);
        cNoInst.setMilestone(mNoInst);

        // 3. Orphan: installment without plan
        DisbursementInstallment instNoPlan = new DisbursementInstallment();
        instNoPlan.setId(104L);
        instNoPlan.setPlan(null);
        DisbursementMilestone mNoPlan = new DisbursementMilestone();
        mNoPlan.setId(105L);
        mNoPlan.setInstallment(instNoPlan);
        MilestoneCompliance cNoPlan = new MilestoneCompliance();
        cNoPlan.setId(106L);
        cNoPlan.setMilestone(mNoPlan);

        // 4. Orphan: plan without application
        DisbursementPlan planNoApp = new DisbursementPlan();
        planNoApp.setId(107L);
        planNoApp.setApplication(null);
        DisbursementInstallment instNoApp = new DisbursementInstallment();
        instNoApp.setId(108L);
        instNoApp.setPlan(planNoApp);
        DisbursementMilestone mNoApp = new DisbursementMilestone();
        mNoApp.setId(109L);
        mNoApp.setInstallment(instNoApp);
        MilestoneCompliance cNoApp = new MilestoneCompliance();
        cNoApp.setId(110L);
        cNoApp.setMilestone(mNoApp);

        // 5. Orphan: application without beneficiary
        GrantApplication appNoBen = new GrantApplication();
        ReflectionTestUtils.setField(appNoBen, "id", 111L);
        appNoBen.setBeneficiary(null);
        DisbursementPlan planNoBen = new DisbursementPlan();
        planNoBen.setId(112L);
        planNoBen.setApplication(appNoBen);
        DisbursementInstallment instNoBen = new DisbursementInstallment();
        instNoBen.setId(113L);
        instNoBen.setPlan(planNoBen);
        DisbursementMilestone mNoBen = new DisbursementMilestone();
        mNoBen.setId(114L);
        mNoBen.setInstallment(instNoBen);
        MilestoneCompliance cNoBen = new MilestoneCompliance();
        cNoBen.setId(115L);
        cNoBen.setMilestone(mNoBen);

        // 6. Orphan: beneficiary without user
        Beneficiary benNoUser = new Beneficiary();
        benNoUser.setId(116L);
        benNoUser.setUser(null);
        GrantApplication appNoUser = new GrantApplication();
        ReflectionTestUtils.setField(appNoUser, "id", 117L);
        appNoUser.setBeneficiary(benNoUser);
        DisbursementPlan planNoUser = new DisbursementPlan();
        planNoUser.setId(118L);
        planNoUser.setApplication(appNoUser);
        DisbursementInstallment instNoUser = new DisbursementInstallment();
        instNoUser.setId(119L);
        instNoUser.setPlan(planNoUser);
        DisbursementMilestone mNoUser = new DisbursementMilestone();
        mNoUser.setId(120L);
        mNoUser.setInstallment(instNoUser);
        MilestoneCompliance cNoUser = new MilestoneCompliance();
        cNoUser.setId(121L);
        cNoUser.setMilestone(mNoUser);

        when(complianceRepo.findAll()).thenReturn(List.of(
                compliance, // valid, belongs to beneficiaryUser
                cNullMilestone, cNoInst, cNoPlan, cNoApp, cNoBen, cNoUser
        ));

        var mine = service.getMine(beneficiaryUser.getEmail());
        assertNotNull(mine);
        assertEquals(1, mine.size());
        assertEquals(41L, mine.get(0).id());
    }

    @Test
    void beneficiaryCanUploadDocumentWithSpacedAllowedFormats() throws Exception {
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));
        when(complianceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ComplianceRequirement req = new ComplianceRequirement();
        req.setAllowedFormats("pdf, png, jpg");
        req.setMaxFileSizeBytes(10485760L);
        compliance.setRequirement(req);
        compliance.setStatus(ComplianceStatus.PENDING);

        byte[] pngHeader = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn((long) pngHeader.length);
        when(file.getBytes()).thenReturn(pngHeader);

        var response = service.submit(41L, "Milestone progress image", file, beneficiaryUser.getEmail());
        assertNotNull(response);
        assertEquals(ComplianceStatus.SUBMITTED, response.status());
        assertEquals("photo.png", response.documentFileName());
    }

    @Test
    void beneficiaryUploadExceedingMaxSizeThrowsException() {
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));

        ComplianceRequirement req = new ComplianceRequirement();
        req.setAllowedFormats("pdf");
        req.setMaxFileSizeBytes(1000L);
        compliance.setRequirement(req);
        compliance.setStatus(ComplianceStatus.PENDING);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("large.pdf");
        when(file.getSize()).thenReturn(5000L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(41L, "Too big", file, beneficiaryUser.getEmail()));
        assertTrue(ex.getReason().contains("exceeds the configured maximum file size"));
    }

    @Test
    void beneficiaryUploadDisallowedFormatThrowsException() {
        when(userRepo.findByEmail(beneficiaryUser.getEmail())).thenReturn(Optional.of(beneficiaryUser));
        when(complianceRepo.findById(41L)).thenReturn(Optional.of(compliance));

        ComplianceRequirement req = new ComplianceRequirement();
        req.setAllowedFormats("pdf,png");
        req.setMaxFileSizeBytes(10485760L);
        compliance.setRequirement(req);
        compliance.setStatus(ComplianceStatus.PENDING);

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("script.sh");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submit(41L, "Invalid script", file, beneficiaryUser.getEmail()));
        assertTrue(ex.getReason().contains("This file format is not allowed"));
    }
}
