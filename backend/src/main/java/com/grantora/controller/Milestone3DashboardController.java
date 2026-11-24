package com.grantora.controller;
import com.grantora.dto.DisbursementDashboardResponse;
import com.grantora.dto.DisbursementMilestoneResponse;
import com.grantora.service.Milestone3DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/milestone-3")
public class Milestone3DashboardController {
    private final Milestone3DashboardService service;
    public Milestone3DashboardController(Milestone3DashboardService service){this.service=service;}
    @GetMapping("/admin-dashboard") public DisbursementDashboardResponse adminDashboard(Authentication auth,
            @RequestParam(required=false) String search,@RequestParam(required=false) Long schemeId,
            @RequestParam(required=false) String state,@RequestParam(required=false) String district,
            @RequestParam(required=false) String applicationStatus,@RequestParam(required=false) String disbursementStatus,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize,
            @RequestParam(defaultValue="applicationId") String sort,@RequestParam(defaultValue="desc") String direction){
        return service.adminDashboard(auth.getName(),search,schemeId,state,district,applicationStatus,disbursementStatus,page,pageSize,sort,direction);
    }
    @GetMapping("/assigned-milestones") public List<DisbursementMilestoneResponse> assignedMilestones(Authentication auth){return service.assignedMilestones(auth.getName());}
}
