package com.back.coach.api.roadmap;

import com.back.coach.domain.roadmap.RoadmapProgressCommandResult;
import com.back.coach.domain.roadmap.RoadmapProgressCommandService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/roadmaps/{roadmapId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoadmapProgressController {

    private final RoadmapProgressCommandService roadmapProgressCommandService;

    public RoadmapProgressController(RoadmapProgressCommandService roadmapProgressCommandService) {
        this.roadmapProgressCommandService = roadmapProgressCommandService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<RoadmapProgressResponse> appendProgress(
            Authentication authentication,
            @PathVariable Long roadmapId,
            @Valid @RequestBody RoadmapProgressRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        RoadmapProgressCommandResult result = roadmapProgressCommandService.appendProgress(
                authenticatedUser.userId(),
                roadmapId,
                request.roadmapWeekId(),
                request.status(),
                request.note()
        );

        return ApiResponse.success(RoadmapProgressResponse.from(result));
    }
}
