package com.back.coach.domain.jobrole.service;

import com.back.coach.domain.jobrole.dto.JobRoleResponse;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class JobRoleService {

    private final JobRoleRepository jobRoleRepository;

    public JobRoleService(JobRoleRepository jobRoleRepository) {
        this.jobRoleRepository = jobRoleRepository;
    }

    public List<JobRoleResponse> listActive() {
        return jobRoleRepository.findAllByActiveTrueOrderByRoleNameAsc().stream()
                .map(JobRoleResponse::from)
                .toList();
    }
}
