package com.back.coach.domain;

import com.back.coach.domain.diagnosis.entity.CapabilityDiagnosis;
import com.back.coach.domain.diagnosis.repository.CapabilityDiagnosisRepository;
import com.back.coach.domain.github.entity.GithubAnalysis;
import com.back.coach.domain.github.entity.GithubConnection;
import com.back.coach.domain.github.entity.GithubProject;
import com.back.coach.domain.github.repository.GithubAnalysisRepository;
import com.back.coach.domain.github.repository.GithubConnectionRepository;
import com.back.coach.domain.github.repository.GithubProjectRepository;
import com.back.coach.domain.jobrole.entity.JobRole;
import com.back.coach.domain.jobrole.entity.SkillRequirement;
import com.back.coach.domain.jobrole.repository.JobRoleRepository;
import com.back.coach.domain.jobrole.repository.SkillRequirementRepository;
import com.back.coach.domain.roadmap.entity.LearningRoadmap;
import com.back.coach.domain.roadmap.entity.ProgressLog;
import com.back.coach.domain.roadmap.entity.RoadmapWeek;
import com.back.coach.domain.roadmap.repository.LearningRoadmapRepository;
import com.back.coach.domain.roadmap.repository.ProgressLogRepository;
import com.back.coach.domain.roadmap.repository.RoadmapWeekRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.entity.UserProfile;
import com.back.coach.domain.user.entity.UserSkill;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.domain.user.repository.UserProfileRepository;
import com.back.coach.domain.user.repository.UserSkillRepository;
import com.back.coach.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class DomainStorageMappingIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("v1 핵심 저장소 Repository bean이 등록된다")
    void repositoriesAreRegistered() {
        assertThat(applicationContext.getBean(UserRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(JobRoleRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(SkillRequirementRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(UserProfileRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(UserSkillRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(GithubConnectionRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(GithubProjectRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(GithubAnalysisRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(CapabilityDiagnosisRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(LearningRoadmapRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(RoadmapWeekRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(ProgressLogRepository.class)).isNotNull();
    }

    @Test
    @DisplayName("v1 핵심 저장 Entity가 JPA metamodel에 등록된다")
    void entitiesAreMapped() {
        assertMapped(User.class);
        assertMapped(JobRole.class);
        assertMapped(SkillRequirement.class);
        assertMapped(UserProfile.class);
        assertMapped(UserSkill.class);
        assertMapped(GithubConnection.class);
        assertMapped(GithubProject.class);
        assertMapped(GithubAnalysis.class);
        assertMapped(CapabilityDiagnosis.class);
        assertMapped(LearningRoadmap.class);
        assertMapped(RoadmapWeek.class);
        assertMapped(ProgressLog.class);
    }

    private void assertMapped(Class<?> entityType) {
        assertThat(entityManager.getMetamodel().entity(entityType)).isNotNull();
    }
}
