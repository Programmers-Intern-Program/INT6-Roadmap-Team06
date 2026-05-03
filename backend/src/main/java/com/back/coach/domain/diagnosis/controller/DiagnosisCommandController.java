package com.back.coach.domain.diagnosis.controller;

import com.back.coach.domain.diagnosis.dto.DiagnosisDetailResponse;
import com.back.coach.domain.diagnosis.dto.DiagnosisRequest;
import com.back.coach.domain.diagnosis.service.DiagnosisCommandService;
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
@RequestMapping(path = "/api/diagnoses", produces = MediaType.APPLICATION_JSON_VALUE)
public class DiagnosisCommandController {

    private final DiagnosisCommandService diagnosisCommandService;

    public DiagnosisCommandController(DiagnosisCommandService diagnosisCommandService) {
        this.diagnosisCommandService = diagnosisCommandService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<DiagnosisDetailResponse> createDiagnosis(
            Authentication authentication,
            @Valid @RequestBody DiagnosisRequest request
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();

        return ApiResponse.success(diagnosisCommandService.createDiagnosis(
                authenticatedUser.userId(),
                request
        ));
    }
}
