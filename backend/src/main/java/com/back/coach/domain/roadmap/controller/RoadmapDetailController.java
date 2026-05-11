package com.back.coach.domain.roadmap.controller;

import com.back.coach.domain.roadmap.dto.RoadmapConstraintsResponse;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapDetailSnapshot;
import com.back.coach.domain.roadmap.dto.RoadmapSummaryResponse;
import com.back.coach.domain.roadmap.service.RoadmapDetailSnapshotService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/roadmaps", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoadmapDetailController {

    private final RoadmapDetailSnapshotService roadmapDetailSnapshotService;
    private final ObjectMapper objectMapper;
    private final int maxWeeks;

    public RoadmapDetailController(
            RoadmapDetailSnapshotService roadmapDetailSnapshotService,
            ObjectMapper objectMapper,
            @Value("${roadmap.max-weeks}") int maxWeeks
    ) {
        this.roadmapDetailSnapshotService = roadmapDetailSnapshotService;
        this.objectMapper = objectMapper;
        this.maxWeeks = maxWeeks;
    }

    @GetMapping("/constraints")
    public ApiResponse<RoadmapConstraintsResponse> getConstraints() {
        return ApiResponse.success(new RoadmapConstraintsResponse(maxWeeks));
    }

    @GetMapping
    public ApiResponse<List<RoadmapSummaryResponse>> listRoadmaps(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(roadmapDetailSnapshotService.listByUser(authenticatedUser.userId()));
    }

    @GetMapping("/{roadmapId}")
    public ApiResponse<RoadmapDetailResponse> findRoadmap(
            Authentication authentication,
            @PathVariable Long roadmapId
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        RoadmapDetailSnapshot snapshot = roadmapDetailSnapshotService.findSnapshot(
                authenticatedUser.userId(),
                roadmapId
        );

        return ApiResponse.success(RoadmapDetailResponse.from(snapshot, objectMapper));
    }
}
