package com.grantora.controller;
import com.grantora.dto.FundAnalyticsFilter;
import com.grantora.dto.FundAnalyticsResponse;
import com.grantora.service.FundAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/api/fund-analytics")
public class FundAnalyticsController {
    private final FundAnalyticsService service;
    public FundAnalyticsController(FundAnalyticsService service){this.service=service;}
    @GetMapping public FundAnalyticsResponse summary(Authentication auth,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long schemeId,@RequestParam(required=false) String state,
            @RequestParam(required=false) String district,@RequestParam(required=false) String disbursementStatus){
        return service.summarize(new FundAnalyticsFilter(from,to,schemeId,state,district,disbursementStatus),auth.getName());
    }
}
