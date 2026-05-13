package com.back.coach.domain.portfolio.controller;

import com.back.coach.domain.portfolio.dto.PortfolioDraftDetailResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftSummaryResponse;
import com.back.coach.domain.portfolio.dto.PortfolioDraftUpdateRequest;
import com.back.coach.domain.portfolio.service.PortfolioDraftService;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/portfolio/drafts", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortfolioDraftController {

    private final PortfolioDraftService portfolioDraftService;

    public PortfolioDraftController(PortfolioDraftService portfolioDraftService) {
        this.portfolioDraftService = portfolioDraftService;
    }

    @PostMapping
    public ApiResponse<PortfolioDraftDetailResponse> createDraft(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(portfolioDraftService.createDraft(user.userId()));
    }

    @PostMapping("/{draftId}/variants/{variantKey}/generate")
    public ApiResponse<PortfolioDraftDetailResponse> generateVariant(
            Authentication authentication,
            @PathVariable Long draftId,
            @PathVariable String variantKey
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(portfolioDraftService.generateVariant(user.userId(), draftId, variantKey));
    }

    @GetMapping
    public ApiResponse<List<PortfolioDraftSummaryResponse>> listDrafts(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(portfolioDraftService.listDrafts(user.userId()));
    }

    @GetMapping("/{draftId}")
    public ApiResponse<PortfolioDraftDetailResponse> getDraft(
            Authentication authentication,
            @PathVariable Long draftId
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(portfolioDraftService.getDraft(user.userId(), draftId));
    }

    @PatchMapping(path = "/{draftId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PortfolioDraftDetailResponse> updateDraft(
            Authentication authentication,
            @PathVariable Long draftId,
            @Valid @RequestBody PortfolioDraftUpdateRequest request
    ) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return ApiResponse.success(portfolioDraftService.updateDraft(user.userId(), draftId, request));
    }
}
