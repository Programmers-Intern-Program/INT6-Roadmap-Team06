package com.back.coach.domain.roadmap.controller;

import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapDetailSnapshot;
import com.back.coach.domain.roadmap.service.RoadmapDetailSnapshotService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/roadmaps", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoadmapDetailController {

    private final RoadmapDetailSnapshotService roadmapDetailSnapshotService;
    private final ObjectMapper objectMapper;

    public RoadmapDetailController(
            RoadmapDetailSnapshotService roadmapDetailSnapshotService,
            ObjectMapper objectMapper
    ) {
        this.roadmapDetailSnapshotService = roadmapDetailSnapshotService;
        this.objectMapper = objectMapper;
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
