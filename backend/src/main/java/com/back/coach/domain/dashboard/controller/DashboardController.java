package com.back.coach.domain.dashboard.controller;

import com.back.coach.domain.dashboard.dto.DashboardResponse;
import com.back.coach.domain.dashboard.dto.DashboardSnapshot;
import com.back.coach.domain.dashboard.service.DashboardSnapshotService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
public class DashboardController {

    private final DashboardSnapshotService dashboardSnapshotService;

    public DashboardController(DashboardSnapshotService dashboardSnapshotService) {
        this.dashboardSnapshotService = dashboardSnapshotService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> findDashboard(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        DashboardSnapshot snapshot = dashboardSnapshotService.findSnapshot(authenticatedUser.userId());

        return ApiResponse.success(DashboardResponse.from(snapshot));
    }
}
