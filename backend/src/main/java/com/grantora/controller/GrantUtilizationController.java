package com.grantora.controller;

import com.grantora.dto.*;
import com.grantora.service.GrantUtilizationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/utilization")
public class GrantUtilizationController {
    private final GrantUtilizationService service;
    public GrantUtilizationController(GrantUtilizationService service) { this.service = service; }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public List<GrantUtilizationResponse> mine(Authentication auth) {
        return service.mine(auth.getName());
    }

    @PostMapping
    @PreAuthorize("hasRole('BENEFICIARY')")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantUtilizationResponse submit(Authentication auth, @Valid @RequestBody GrantUtilizationRequest request) {
        return service.submit(request, auth.getName());
    }

    @PostMapping(value = "/submit-with-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BENEFICIARY')")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantUtilizationResponse submitWithDocument(
            Authentication auth,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("installmentId") Long installmentId,
            @RequestParam("reportedAmount") BigDecimal reportedAmount,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "supportingComplianceIds", required = false) List<Long> supportingComplianceIds,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.submitWithDocument(
                new GrantUtilizationRequest(applicationId, installmentId, reportedAmount, category, description, supportingComplianceIds),
                file, auth.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public GrantUtilizationResponse replace(@PathVariable Long id, Authentication auth, @Valid @RequestBody GrantUtilizationRequest request) {
        return service.replace(id, request, auth.getName());
    }

    @PutMapping(value = "/{id}/resubmit-with-document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BENEFICIARY')")
    public GrantUtilizationResponse replaceWithDocument(
            @PathVariable Long id,
            Authentication auth,
            @RequestParam("applicationId") Long applicationId,
            @RequestParam("installmentId") Long installmentId,
            @RequestParam("reportedAmount") BigDecimal reportedAmount,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "supportingComplianceIds", required = false) List<Long> supportingComplianceIds,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return service.replaceWithDocument(
                id,
                new GrantUtilizationRequest(applicationId, installmentId, reportedAmount, category, description, supportingComplianceIds),
                file, auth.getName());
    }

    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public List<GrantUtilizationResponse> queue(Authentication auth) {
        return service.reviewQueue(auth.getName());
    }

    @PatchMapping("/{id}/review")
    @PreAuthorize("hasRole('OFFICER')")
    public GrantUtilizationResponse review(@PathVariable Long id, Authentication auth, @Valid @RequestBody GrantUtilizationReviewRequest request) {
        return service.review(id, request, auth.getName());
    }

    @GetMapping("/{id}/document")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'BENEFICIARY')")
    public ResponseEntity<byte[]> document(
            @PathVariable Long id,
            @RequestParam(value = "version", required = false) Integer version,
            Authentication auth) {
        byte[] bytes = service.document(id, version, auth.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("utilization-" + id + (version != null ? "-v" + version : ""), StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}
