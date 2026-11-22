package com.grantora.controller;
import com.grantora.dto.*;
import com.grantora.service.GrantUtilizationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/utilization")
public class GrantUtilizationController {
    private final GrantUtilizationService service;
    public GrantUtilizationController(GrantUtilizationService service){this.service=service;}
    @GetMapping("/mine") public List<GrantUtilizationResponse> mine(Authentication auth){return service.mine(auth.getName());}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public GrantUtilizationResponse submit(Authentication auth,@Valid @RequestBody GrantUtilizationRequest request){return service.submit(request,auth.getName());}
    @PutMapping("/{id}") public GrantUtilizationResponse replace(@PathVariable Long id,Authentication auth,@Valid @RequestBody GrantUtilizationRequest request){return service.replace(id,request,auth.getName());}
    @GetMapping("/review-queue") public List<GrantUtilizationResponse> queue(Authentication auth){return service.reviewQueue(auth.getName());}
    @PatchMapping("/{id}/review") public GrantUtilizationResponse review(@PathVariable Long id,Authentication auth,@Valid @RequestBody GrantUtilizationReviewRequest request){return service.review(id,request,auth.getName());}
}
