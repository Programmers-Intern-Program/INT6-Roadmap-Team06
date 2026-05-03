package com.back.coach.domain.roadmap.controller;

import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.service.RoadmapCommandService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/roadmaps", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoadmapCommandController {

    private final RoadmapCommandService roadmapCommandService;

    public RoadmapCommandController(RoadmapCommandService roadmapCommandService) {
        this.roadmapCommandService = roadmapCommandService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<RoadmapDetailResponse> createRoadmap(
            Authentication authentication,
            @Valid @RequestBody RoadmapRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(roadmapCommandService.createRoadmap(
                authenticatedUser.userId(),
                request
        ));
    }
}
