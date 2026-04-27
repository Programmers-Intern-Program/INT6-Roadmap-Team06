package com.back.coach.domain.user.entity;

import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "user_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile extends BaseTimeEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_role_id", nullable = false)
    private Long jobRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_level", nullable = false, length = 30)
    private CurrentLevel currentLevel;

    @Column(name = "weekly_study_hours")
    private Integer weeklyStudyHours;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interest_areas_json", nullable = false, columnDefinition = "jsonb")
    private String interestAreasJson;

    @Column(name = "resume_asset_id")
    private Long resumeAssetId;

    @Column(name = "portfolio_asset_id")
    private Long portfolioAssetId;
}
