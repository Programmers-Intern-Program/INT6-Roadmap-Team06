package com.back.coach.global.jpa;

import com.back.coach.global.jpa.entity.BaseTimeEntity;
import com.back.coach.support.IntegrationTest;
import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DbContractIntegrationTest {

    private static final List<String> V1_TABLES = List.of(
            "users",
            "job_roles",
            "user_profiles",
            "skill_requirements",
            "user_skills",
            "github_connections",
            "github_projects",
            "github_analyses",
            "capability_diagnoses",
            "learning_roadmaps",
            "roadmap_weeks",
            "progress_logs"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway v1 migration이 핵심 테이블을 생성한다")
    void flywayCreatesV1Tables() {
        for (String table : V1_TABLES) {
            assertThat(tableExists(table))
                    .as("table %s should exist", table)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("공통 시간 컬럼은 물리 스키마의 snake_case 이름을 사용한다")
    void timeColumnsUsePhysicalSchemaNames() throws NoSuchFieldException {
        assertThat(columnName("createdAt")).isEqualTo("created_at");
        assertThat(columnName("updatedAt")).isEqualTo("updated_at");

        Set<String> userColumns = columns("users");
        assertThat(userColumns)
                .contains("created_at", "updated_at")
                .doesNotContain("createDate", "modifyDate");
    }

    @Test
    @DisplayName("기본 백엔드 직무와 기술 요구사항 seed data가 생성된다")
    void jobRoleSeedDataExists() {
        Long backendRoleId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM job_roles
                WHERE role_code = 'BACKEND_DEVELOPER'
                """, Long.class);

        assertThat(backendRoleId).isNotNull();

        Set<String> skillNames = Set.copyOf(jdbcTemplate.queryForList("""
                SELECT skill_name
                FROM skill_requirements
                WHERE job_role_id = ?
                """, String.class, backendRoleId));

        assertThat(skillNames)
                .contains("Java", "Spring Boot", "REST API", "PostgreSQL", "Redis")
                .hasSizeGreaterThanOrEqualTo(10);

        Integer springBootImportance = jdbcTemplate.queryForObject("""
                SELECT importance
                FROM skill_requirements
                WHERE job_role_id = ?
                  AND skill_name = 'Spring Boot'
                """, Integer.class, backendRoleId);

        assertThat(springBootImportance).isEqualTo(5);
    }

    private boolean tableExists(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = ?
                )
                """, Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    private Set<String> columns(String tableName) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                """, String.class, tableName));
    }

    private String columnName(String fieldName) throws NoSuchFieldException {
        Field field = BaseTimeEntity.class.getDeclaredField(fieldName);
        return field.getAnnotation(Column.class).name();
    }
}
