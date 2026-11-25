package com.grantora.controller;

import com.grantora.dto.*;
import com.grantora.entity.DisbursementReminderConfig;
import com.grantora.entity.MilestoneCompliance;
import com.grantora.service.DisbursementService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/disbursements")
public class DisbursementController {

    private final DisbursementService disbursementService;

    public DisbursementController(DisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    // ==========================================
    // PLANS
    // ==========================================

    @PostMapping("/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DisbursementPlanResponse> createPlan(
            Authentication authentication,
            @Valid @RequestBody DisbursementPlanRequest request) {
        DisbursementPlanResponse response = disbursementService.createPlan(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/plans/mine")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public List<DisbursementPlanResponse> getMyPlans(Authentication authentication) {
        return disbursementService.getBeneficiaryPlans(authentication.getName());
    }

    @PatchMapping("/plans/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public DisbursementPlanResponse deactivatePlan(@PathVariable Long id, Authentication authentication) {
        return disbursementService.deactivatePlan(id, authentication.getName());
    }

    @GetMapping("/plans")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEVEL_FINANCE')")
    public ResponseEntity<?> getAllPlans(
            Authentication authentication,
            @RequestParam(required = false, defaultValue = "false") boolean paged,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (paged) {
            Page<DisbursementPlanResponse> page = disbursementService.getAllPlans(pageable, authentication.getName());
            return ResponseEntity.ok(page);
        } else {
            List<DisbursementPlanResponse> list = disbursementService.getAllPlansList(authentication.getName());
            return ResponseEntity.ok(list);
        }
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEVEL_FINANCE')")
    public DisbursementPlanResponse getPlanById(@PathVariable Long id, Authentication authentication) {
        return disbursementService.getPlanById(id, authentication.getName());
    }

    @GetMapping("/plans/by-application/{applicationId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('LEVEL_FINANCE')")
    public DisbursementPlanResponse getPlanByApplicationId(@PathVariable Long applicationId,
            Authentication authentication) {
        return disbursementService.getPlanByApplicationId(applicationId, authentication.getName());
    }

    @GetMapping("/eligible-applications")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GrantApplicationResponse> getEligibleApplications() {
        return disbursementService.getEligibleApplicationsWithoutPlan();
    }

    // ==========================================
    // MILESTONES & PROGRESS
    // ==========================================

    @GetMapping("/milestones")
    public List<DisbursementMilestoneResponse> getAllMilestones() {
        return disbursementService.getAllMilestones();
    }

    @PatchMapping("/milestones/{id}/progress")
    public DisbursementMilestoneResponse updateMilestoneProgress(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody MilestoneProgressUpdateRequest request) {
        return disbursementService.updateMilestoneProgress(id, request, authentication.getName());
    }

    // ==========================================
    // COMPLIANCE
    // ==========================================

    @GetMapping("/compliances")
    public List<MilestoneComplianceResponse> getAllCompliances() {
        return disbursementService.getAllCompliances();
    }

    @GetMapping("/milestones/{id}/compliances")
    public List<MilestoneComplianceResponse> getCompliancesByMilestone(@PathVariable Long id) {
        return disbursementService.getCompliancesByMilestone(id);
    }

    @Deprecated
    @PostMapping(value = "/milestones/{id}/compliance", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MilestoneComplianceResponse submitCompliance(
            @PathVariable Long id,
            Authentication authentication,
            @RequestParam(value = "requirementTitle", required = false) String requirementTitle,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "submissionNotes", required = false) String submissionNotes,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.GONE,
                "Legacy compliance upload endpoint is retired. Use POST /api/compliance/submissions/{id}.");
    }

    @GetMapping("/compliance/{id}/document")
    public ResponseEntity<byte[]> getComplianceDocument(
            @PathVariable Long id,
            Authentication authentication) {
        MilestoneCompliance entity = disbursementService.getComplianceEntity(id, authentication.getName());
        byte[] data = entity.getDocumentData();
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (entity.getDocumentContentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(entity.getDocumentContentType());
            } catch (Exception ignored) {
            }
        }

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(entity.getDocumentFileName() != null ? entity.getDocumentFileName() : "compliance_document",
                        StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(data);
    }

    @Deprecated
    @PostMapping("/compliance/{id}/verify")
    public MilestoneComplianceResponse verifyCompliance(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody ComplianceVerifyRequest request) {
        throw new org.springframework.web.server.ResponseStatusException(
                HttpStatus.GONE,
                "Legacy compliance verification endpoint is retired. Use PATCH /api/compliance/submissions/{id}/review.");
    }

    // ==========================================
    // DISBURSEMENT RELEASE
    // ==========================================

    @PostMapping("/installments/{id}/disburse")
    @PreAuthorize("hasAuthority('LEVEL_FINANCE')")
    public DisbursementInstallmentResponse disburseInstallment(
            @PathVariable Long id,
            Authentication authentication,
            @Valid @RequestBody DisburseFundsRequest request) {
        return disbursementService.disburseInstallment(id, request, authentication.getName());
    }

    // ==========================================
    // REMINDER AUTOMATION
    // ==========================================

    @GetMapping("/reminders/config")
    public DisbursementReminderConfig getReminderConfig() {
        return disbursementService.getReminderConfig();
    }

    @PutMapping("/reminders/config")
    public DisbursementReminderConfig updateReminderConfig(
            Authentication authentication,
            @RequestBody ReminderConfigDto dto) {
        return disbursementService.updateReminderConfig(dto, authentication.getName());
    }

    // ==========================================
    // FUND UTILIZATION & REGIONAL ANALYTICS
    // ==========================================

    @GetMapping("/fund-utilization")
    public FundUtilizationSummaryResponse getFundUtilization() {
        return disbursementService.getFundUtilizationSummary();
    }

    @GetMapping("/regional-analytics")
    public List<RegionalAnalyticsResponse> getRegionalAnalytics() {
        return disbursementService.getRegionalAnalytics();
    }

    private boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
