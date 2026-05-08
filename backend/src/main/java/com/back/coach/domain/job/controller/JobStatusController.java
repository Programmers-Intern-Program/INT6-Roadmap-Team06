package com.back.coach.domain.job.controller;

import com.back.coach.domain.job.dto.JobStatusResponse;
import com.back.coach.domain.job.service.JobStatusService;
import com.back.coach.domain.job.service.JobStatusSnapshot;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.global.response.ApiResponse;
import com.back.coach.global.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class JobStatusController {

    private final JobStatusService jobStatusService;

    public JobStatusController(JobStatusService jobStatusService) {
        this.jobStatusService = jobStatusService;
    }

    @GetMapping("/{jobId}/status")
    public ApiResponse<JobStatusResponse> findStatus(
            Authentication authentication,
            @PathVariable String jobId
    ) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        JobStatusSnapshot snapshot = jobStatusService.find(authenticatedUser.userId(), jobId)
                .orElseThrow(() -> new ServiceException(ErrorCode.RESOURCE_NOT_FOUND, "작업 상태를 찾을 수 없습니다."));

        return ApiResponse.success(JobStatusResponse.from(snapshot));
    }
}
