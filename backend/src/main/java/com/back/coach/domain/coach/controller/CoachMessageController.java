package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.dto.CoachMessageRequest;
import com.back.coach.domain.coach.dto.CoachMessageResponse;
import com.back.coach.domain.coach.entity.CoachConversation;
import com.back.coach.domain.coach.service.CoachMessageService;
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
        path = "/api/coach/sessions/{sessionId}/messages",
        produces = MediaType.APPLICATION_JSON_VALUE,
        consumes = MediaType.APPLICATION_JSON_VALUE
)
public class CoachMessageController {

    private final CoachMessageService coachMessageService;

    public CoachMessageController(CoachMessageService coachMessageService) {
        this.coachMessageService = coachMessageService;
    }

    @PostMapping
    public ApiResponse<CoachMessageResponse> sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody CoachMessageRequest request,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        CoachConversation coachMessage = coachMessageService.sendMessage(
                user.userId(), sessionId, request.message()
        );
        return ApiResponse.success(CoachMessageResponse.from(coachMessage));
    }
}
