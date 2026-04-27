package com.back.coach.domain.roadmap.entity;

import com.back.coach.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "roadmap_weeks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoadmapWeek extends BaseEntity {

    @Column(name = "roadmap_id", nullable = false)
    private Long roadmapId;

    @Column(name = "week_number", nullable = false)
    private Integer weekNumber;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "reason_text", nullable = false, columnDefinition = "text")
    private String reasonText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tasks_json", nullable = false, columnDefinition = "jsonb")
    private String tasksJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "materials_json", nullable = false, columnDefinition = "jsonb")
    private String materialsJson;

    @Column(name = "estimated_hours", nullable = false, precision = 4, scale = 1)
    private BigDecimal estimatedHours;
}
