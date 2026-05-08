package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.dto.ReplanRequest;
import com.back.coach.domain.coach.dto.ReplanResponse;
import com.back.coach.domain.coach.service.ReplanService;
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
@RequestMapping(
        path = "/api/coach/sessions/{sessionId}/replan",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
)
public class ReplanController {

    private final ReplanService replanService;

    public ReplanController(ReplanService replanService) {
        this.replanService = replanService;
    }

    @PostMapping
    public ApiResponse<ReplanResponse> replan(
            @PathVariable Long sessionId,
            @Valid @RequestBody ReplanRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();

        if (Boolean.TRUE.equals(request.confirmed())) {
            ReplanService.ReplanResult result = replanService.confirm(
                    user.userId(), sessionId, request.proposalId()
            );
            return ApiResponse.success(ReplanResponse.confirmed(
                    result.newRoadmapId(), result.newRoadmapVersion()));
        } else {
            replanService.dismiss(user.userId(), request.proposalId());
            return ApiResponse.success(ReplanResponse.deferred());
        }
    }
}
