package com.back.coach.domain.jobrole.entity;

import com.back.coach.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skill_requirements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillRequirement extends BaseTimeEntity {

    @Column(name = "job_role_id", nullable = false)
    private Long jobRoleId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "importance", nullable = false)
    private Integer importance;
}
