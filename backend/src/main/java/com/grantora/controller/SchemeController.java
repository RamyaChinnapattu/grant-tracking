package com.grantora.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import com.grantora.entity.Scheme;
import com.grantora.service.SchemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/schemes")
@CrossOrigin(origins = "http://localhost:5173")
public class SchemeController {

    private final SchemeService schemeService;

    public SchemeController(SchemeService schemeService) {
        this.schemeService = schemeService;
    }

    @GetMapping
    public List<Scheme> getAllSchemes(Authentication authentication) {
        if (authentication != null && hasAdminOrOfficerRole(authentication)) {
            return schemeService.getAllSchemes();
        }
        return schemeService.getPublicSchemes();
    }

    @GetMapping("/{id}")
    public Scheme getSchemeById(@PathVariable Long id, Authentication authentication) {
        if (authentication != null && hasAdminOrOfficerRole(authentication)) {
            return schemeService.getSchemeById(id);
        }
        return schemeService.getPublicSchemeById(id);
    }

    @PostMapping
    public Scheme createScheme(@RequestBody Scheme scheme) {
        return schemeService.createScheme(scheme);
    }

    @PutMapping("/{id}")
    public Scheme updateScheme(
            @PathVariable Long id,
            @RequestBody Scheme scheme) {
        return schemeService.updateScheme(id, scheme);
    }

    @DeleteMapping("/{id}")
    public String deleteScheme(@PathVariable Long id) {
        schemeService.deleteScheme(id);
        return "Scheme deleted successfully";
    }

    @PostMapping(value = { "/{id}/check-eligibility", "/{id}/evaluate-eligibility" })
    public ResponseEntity<EligibilityEvaluationResult> checkEligibility(
            @PathVariable Long id,
            @RequestBody(required = false) JsonNode requestBody,
            Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        Long beneficiaryId = null;
        List<EligibilityAnswer> answers = new ArrayList<>();

        if (requestBody != null && !requestBody.isNull() && !requestBody.isMissingNode()) {
            ObjectMapper mapper = new ObjectMapper();
            if (requestBody.isArray()) {
                try {
                    answers = mapper.convertValue(requestBody, new TypeReference<List<EligibilityAnswer>>() {
                    });
                } catch (Exception ignored) {
                }
            } else if (requestBody.isObject()) {
                if (hasAdminRole(authentication)
                        && requestBody.has("beneficiaryId")
                        && !requestBody.get("beneficiaryId").isNull()) {
                    beneficiaryId = requestBody.get("beneficiaryId").asLong();
                }
                if (requestBody.has("answers") && requestBody.get("answers").isArray()) {
                    try {
                        answers = mapper.convertValue(requestBody.get("answers"),
                                new TypeReference<List<EligibilityAnswer>>() {
                                });
                    } catch (Exception ignored) {
                    }
                } else if (requestBody.has("questionId")) {
                    try {
                        EligibilityAnswer single = mapper.convertValue(requestBody, EligibilityAnswer.class);
                        answers = List.of(single);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        if (authentication == null || !hasAdminRole(authentication)) {
            schemeService.getPublicSchemeById(id);
        }

        EligibilityEvaluationResult result = schemeService.evaluateEligibility(
                id, beneficiaryId, email, answers);
        return ResponseEntity.ok(result);
    }

    private boolean hasAdminRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_ADMIN"::equals);
    }

    private boolean hasAdminOrOfficerRole(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(auth -> "ROLE_ADMIN".equals(auth) || "ROLE_OFFICER".equals(auth));
    }
}