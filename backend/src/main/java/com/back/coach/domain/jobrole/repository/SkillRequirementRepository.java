package com.back.coach.domain.jobrole.repository;

import com.back.coach.domain.jobrole.entity.SkillRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRequirementRepository extends JpaRepository<SkillRequirement, Long> {

    List<SkillRequirement> findByJobRoleIdOrderByImportanceDescSkillNameAsc(Long jobRoleId);
}
