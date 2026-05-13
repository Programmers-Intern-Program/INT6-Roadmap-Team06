package com.back.coach.domain.roadmap.service;

import com.back.coach.domain.diagnosis.dto.DiagnosisPayload;
import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.global.code.CurrentLevel;
import com.back.coach.global.code.DiagnosisSeverity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RoadmapPromptBuilderTest {

    private final RoadmapPromptBuilder builder = new RoadmapPromptBuilder(8);

    @Test
    void build_includesKoreanUserVisibleOutputRulesAndStrictJsonShape() {
        String prompt = builder.build(new RoadmapPromptBuilder.Input(
                jobRole(),
                profile(),
                diagnosis(),
                diagnosisPayload(),
                8,
                LocalDate.of(2099, 12, 31)
        ));

        assertThat(prompt).contains("Write user-visible natural-language values in Korean");
        assertThat(prompt).contains("summary, weeks[].topic, weeks[].reason, tasks[].title, materials[].title");
        assertThat(prompt).contains("Keep JSON field names and enum values unchanged");
        assertThat(prompt).contains("Redis 캐시 기초");
        assertThat(prompt).contains("READ_DOCS", "DOCS", "weekNumber");
    }

    private JobRole jobRole() {
        JobRole jobRole = mock(JobRole.class);
        given(jobRole.getRoleCode()).willReturn("BACKEND_DEVELOPER");
        return jobRole;
    }

    private UserProfile profile() {
        return UserProfile.create(
                1L,
                10L,
                CurrentLevel.JUNIOR,
                8,
                LocalDate.of(2099, 12, 31),
                "[]",
                null,
                null
        );
    }

    private CapabilityDiagnosis diagnosis() {
        CapabilityDiagnosis diagnosis = CapabilityDiagnosis.create(
                1L,
                10L,
                20L,
                30L,
                1,
                CurrentLevel.JUNIOR,
                "Redis 보완 필요",
                "{}"
        );
        ReflectionTestUtils.setField(diagnosis, "id", 100L);
        return diagnosis;
    }

    private DiagnosisPayload diagnosisPayload() {
        return new DiagnosisPayload(
                List.of(new DiagnosisPayload.MissingSkill(
                        "Redis",
                        DiagnosisSeverity.HIGH,
                        "캐시 설계 경험이 부족함",
                        1
                )),
                List.of("Spring Boot"),
                List.of("Redis 캐시와 TTL 기반 설계를 먼저 학습")
        );
    }
}
