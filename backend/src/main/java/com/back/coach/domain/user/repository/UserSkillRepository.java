package com.back.coach.domain.user.repository;

import com.back.coach.domain.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {
}
