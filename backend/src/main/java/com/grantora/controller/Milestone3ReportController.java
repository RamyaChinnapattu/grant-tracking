package com.grantora.controller;
import com.grantora.service.Milestone3ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
@RestController @RequestMapping("/api/reports/milestone-3")
public class Milestone3ReportController {
    private final Milestone3ReportService service;public Milestone3ReportController(Milestone3ReportService service){this.service=service;}
    @GetMapping("/{type}.csv") public ResponseEntity<byte[]> download(@PathVariable String type,Authentication auth,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long schemeId,@RequestParam(required=false) String state,@RequestParam(required=false) String district,
            @RequestParam(required=false) String applicationStatus,@RequestParam(required=false) String disbursementStatus){
        if(!java.util.Set.of("disbursement","compliance","utilization","regional").contains(type.toLowerCase(java.util.Locale.ROOT)))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown report type.");
        byte[] body=service.csv(type,auth.getName(),from,to,schemeId,state,district,applicationStatus,disbursementStatus).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text","csv",StandardCharsets.UTF_8)).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=grantora-"+type.toLowerCase(java.util.Locale.ROOT)+"-report.csv").body(body);
    }
}
