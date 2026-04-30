package com.back.coach.domain.github.controller;

import com.back.coach.domain.github.dto.GithubConnectionRequest;
import com.back.coach.domain.github.dto.GithubConnectionResponse;
import com.back.coach.domain.github.dto.GithubRepositoryListResponse;
import com.back.coach.domain.github.service.GithubConnectionService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/github", produces = MediaType.APPLICATION_JSON_VALUE)
public class GithubConnectionController {

    private final GithubConnectionService connectionService;

    public GithubConnectionController(GithubConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    @PostMapping(path = "/connections", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<GithubConnectionResponse>> connect(
            Authentication authentication,
            @Valid @RequestBody GithubConnectionRequest request) {

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        GithubConnectionService.ConnectResult result = connectionService.connect(user.userId(), request.authorizationCode());
        return ResponseEntity.ok(ApiResponse.success(GithubConnectionResponse.from(result)));
    }

    @GetMapping("/repositories")
    public ResponseEntity<ApiResponse<GithubRepositoryListResponse>> listRepositories(
            Authentication authentication,
            @RequestParam Long githubConnectionId) {

        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        GithubRepositoryListResponse response = GithubRepositoryListResponse.from(
                githubConnectionId,
                connectionService.listRepositories(user.userId(), githubConnectionId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
