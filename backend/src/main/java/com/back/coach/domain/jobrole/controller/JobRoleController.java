package com.back.coach.domain.jobrole.controller;

import com.back.coach.domain.jobrole.dto.JobRoleResponse;
import com.back.coach.domain.jobrole.service.JobRoleService;
import com.back.coach.global.response.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/job-roles", produces = MediaType.APPLICATION_JSON_VALUE)
public class JobRoleController {

    private final JobRoleService jobRoleService;

    public JobRoleController(JobRoleService jobRoleService) {
        this.jobRoleService = jobRoleService;
    }

    @GetMapping
    public ApiResponse<List<JobRoleResponse>> listJobRoles() {
        return ApiResponse.success(jobRoleService.listActive());
    }
}
