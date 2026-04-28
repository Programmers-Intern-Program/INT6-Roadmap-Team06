package com.back.coach.domain.user.repository;

import com.back.coach.domain.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserIdOrderBySkillNameAsc(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
