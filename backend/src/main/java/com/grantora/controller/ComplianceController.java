package com.grantora.controller;

import com.grantora.dto.*;
import com.grantora.service.ComplianceOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {
    private final ComplianceOperationsService service;
    public ComplianceController(ComplianceOperationsService service){this.service=service;}

    @GetMapping("/requirements")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ComplianceRequirementResponse> requirements(Authentication auth){return service.getRequirements(auth.getName());}
    @PostMapping("/requirements")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplianceRequirementResponse createRequirement(Authentication auth,@Valid @RequestBody ComplianceRequirementRequest request){return service.createRequirement(request,auth.getName());}
    @PatchMapping("/requirements/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplianceRequirementResponse deactivateRequirement(@PathVariable Long id,Authentication auth){return service.deactivateRequirement(id,auth.getName());}
    @GetMapping("/mine")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public List<ComplianceItemResponse> mine(Authentication auth){return service.getMine(auth.getName());}
    @GetMapping("/review-queue")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public List<ComplianceItemResponse> reviewQueue(Authentication auth){return service.getReviewQueue(auth.getName());}
    @PostMapping(value="/submissions/{id}",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('BENEFICIARY')")
    public ComplianceItemResponse submit(@PathVariable Long id,Authentication auth,
            @RequestParam(value="submissionNotes",required=false) String notes,
            @RequestPart("file") MultipartFile file){return service.submit(id,notes,file,auth.getName());}
    @PatchMapping("/submissions/{id}/review")
    @PreAuthorize("hasRole('OFFICER')")
    public ComplianceItemResponse review(@PathVariable Long id,Authentication auth,@Valid @RequestBody ComplianceReviewRequest request){return service.review(id,request,auth.getName());}
    @PatchMapping("/submissions/{id}/waive")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplianceItemResponse waive(@PathVariable Long id,Authentication auth,@Valid @RequestBody ComplianceWaiverRequest request){return service.waive(id,request,auth.getName());}
    @PatchMapping("/submissions/{id}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public ComplianceItemResponse reopen(@PathVariable Long id,Authentication auth,@Valid @RequestBody ComplianceReopenRequest request){return service.reopen(id,request,auth.getName());}
    @GetMapping("/submissions/{id}/document")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'BENEFICIARY')")
    public ResponseEntity<byte[]> document(@PathVariable Long id,
            @RequestParam(value="version", required=false) Integer version,
            Authentication auth){
        byte[] bytes=service.document(id,version,auth.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("compliance-"+id+(version!=null?"-v"+version:""), StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
    @GetMapping("/non-compliances")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public List<NonComplianceResponse> nonCompliances(Authentication auth){return service.getNonCompliances(auth.getName());}
    @PatchMapping("/non-compliances/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public NonComplianceResponse resolve(@PathVariable Long id,Authentication auth,@Valid @RequestBody NonComplianceResolutionRequest request){return service.resolveNonCompliance(id,request,auth.getName());}
}
