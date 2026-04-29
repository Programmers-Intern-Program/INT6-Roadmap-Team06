package com.back.coach.domain.user.repository;

import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.global.code.SkillSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserIdOrderBySkillNameAsc(Long userId);

    List<UserSkill> findByUserIdAndSourceTypeOrderBySkillNameAsc(Long userId, SkillSourceType sourceType);

    boolean existsByIdAndUserId(Long id, Long userId);
}
