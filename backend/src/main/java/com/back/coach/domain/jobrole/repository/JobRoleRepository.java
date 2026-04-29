package com.back.coach.domain.jobrole.repository;

import com.back.coach.domain.jobrole.entity.JobRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRoleRepository extends JpaRepository<JobRole, Long> {

    Optional<JobRole> findByRoleCodeAndActiveTrue(String roleCode);
}
