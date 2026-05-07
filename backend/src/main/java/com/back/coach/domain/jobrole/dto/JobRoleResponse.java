package com.back.coach.domain.jobrole.dto;

import com.back.coach.domain.jobrole.entity.JobRole;

public record JobRoleResponse(
        String roleCode,
        String roleName,
        String description
) {
    public static JobRoleResponse from(JobRole jobRole) {
        return new JobRoleResponse(
                jobRole.getRoleCode(),
                jobRole.getRoleName(),
                jobRole.getDescription()
        );
    }
}
