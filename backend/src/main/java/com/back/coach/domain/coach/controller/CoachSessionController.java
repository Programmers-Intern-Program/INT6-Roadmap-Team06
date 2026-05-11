package com.back.coach.domain.coach.controller;

import com.back.coach.domain.coach.dto.CoachSessionResponse;
import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.service.CoachSessionService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/coach/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
public class CoachSessionController {

    private final CoachSessionService coachSessionService;

    public CoachSessionController(CoachSessionService coachSessionService) {
        this.coachSessionService = coachSessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CoachSessionResponse> startSession(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ChatSession session = coachSessionService.startSession(user.userId());
        return ApiResponse.success(CoachSessionResponse.from(session));
    }

    @GetMapping("/active")
    public ApiResponse<CoachSessionResponse> getActiveSession(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ChatSession session = coachSessionService.getActiveSession(user.userId());
        return ApiResponse.success(CoachSessionResponse.from(session));
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeSession(
            @PathVariable Long sessionId,
            Authentication authentication
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        coachSessionService.closeSession(user.userId(), sessionId);
    }
}
