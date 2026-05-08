package com.back.coach.domain.coach.service;

import com.back.coach.domain.coach.entity.ChatSession;
import com.back.coach.domain.coach.entity.ReplanProposal;
import com.back.coach.domain.coach.repository.AgentEventRepository;
import com.back.coach.domain.coach.repository.ChatSessionRepository;
import com.back.coach.domain.coach.repository.ReplanProposalRepository;
import com.back.coach.domain.pattern.repository.DetectedPatternRepository;
import com.back.coach.domain.roadmap.dto.RoadmapDetailResponse;
import com.back.coach.domain.roadmap.dto.RoadmapRequest;
import com.back.coach.domain.roadmap.service.RoadmapCommandService;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ReplanProposalStatus;
import com.back.coach.global.exception.ErrorCode;
import com.back.coach.global.exception.ServiceException;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@IntegrationTest
class ReplanServiceIntegrationTest {

    @Autowired
    private ReplanService replanService;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ReplanProposalRepository replanProposalRepository;

    @Autowired
    private AgentEventRepository agentEventRepository;

    @Autowired
    private DetectedPatternRepository detectedPatternRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private RoadmapCommandService roadmapCommandService;

    private Long userId;
    private Long sessionId;
    private Long proposalId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB, "gh-replan-" + System.nanoTime(), "replan@test.com")
        );
        userId = user.getId();

        // 최신 진단이 있어야 confirm 가능 — 최소 행 삽입
        Long jobRoleId = jdbc.queryForObject(
                "SELECT id FROM job_roles WHERE role_code = 'BACKEND_DEVELOPER'", Long.class);
        Long profileId = jdbc.queryForObject("""
                INSERT INTO user_profiles (user_id, job_role_id, current_level)
                VALUES (?, ?, 'JUNIOR') RETURNING id
                """, Long.class, userId, jobRoleId);
        Long connId = jdbc.queryForObject("""
                INSERT INTO github_connections (user_id, github_user_id, github_login, access_type)
                VALUES (?, 'r-gh', 'r-login', 'OAUTH') RETURNING id
                """, Long.class, userId);
        Long analysisId = jdbc.queryForObject("""
                INSERT INTO github_analyses (user_id, github_connection_id, version, summary)
                VALUES (?, ?, 1, 's') RETURNING id
                """, Long.class, userId, connId);
        jdbc.update("""
                INSERT INTO capability_diagnoses
                    (user_id, profile_id, github_analysis_id, job_role_id, version, current_level, summary)
                VALUES (?, ?, ?, ?, 1, 'JUNIOR', 's')
                """, userId, profileId, analysisId, jobRoleId);

        ChatSession session = ChatSession.start(userId, 1, 1);
        sessionId = chatSessionRepository.save(session).getId();

        ReplanProposal proposal = ReplanProposal.create(
                sessionId, userId, null,
                "3일 연속 Redis 미달성",
                Instant.now().plus(24, ChronoUnit.HOURS)
        );
        proposalId = replanProposalRepository.save(proposal).getId();
    }

    @AfterEach
    void cleanUp() {
        agentEventRepository.deleteAll(agentEventRepository.findAll().stream()
                .filter(e -> e.getUserId().equals(userId)).toList());
        jdbc.update("DELETE FROM detected_patterns WHERE user_id = ?", userId);
        replanProposalRepository.deleteAll(replanProposalRepository.findAll().stream()
                .filter(p -> p.getUserId().equals(userId)).toList());
        chatSessionRepository.deleteAll(chatSessionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId)).toList());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("confirmed=true: 새 로드맵 생성, 제안 CONFIRMED, AgentEvent 기록")
    void confirmCreatesNewRoadmapAndMarksProposal() {
        given(roadmapCommandService.createRoadmap(eq(userId), any(RoadmapRequest.class)))
                .willReturn(new RoadmapDetailResponse("99", 2, 4, "재계획 로드맵",
                        Instant.now(), List.of()));

        ReplanService.ReplanResult result = replanService.confirm(userId, sessionId, proposalId);

        assertThat(result.newRoadmapId()).isEqualTo("99");
        assertThat(result.newRoadmapVersion()).isEqualTo(2);

        ReplanProposal updated = replanProposalRepository.findById(proposalId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReplanProposalStatus.CONFIRMED);
        assertThat(updated.getResolvedAt()).isNotNull();

        long agentEvents = agentEventRepository.findAll().stream()
                .filter(e -> e.getUserId().equals(userId)).count();
        assertThat(agentEvents).isEqualTo(1);
    }

    @Test
    @DisplayName("confirmed=true + 미처리 패턴이 있으면 processed_at 마킹")
    void confirmMarksUnprocessedPatterns() {
        seedDetectedPattern();
        given(roadmapCommandService.createRoadmap(eq(userId), any(RoadmapRequest.class)))
                .willReturn(new RoadmapDetailResponse("99", 2, 4, "s", Instant.now(), List.of()));

        replanService.confirm(userId, sessionId, proposalId);

        long unprocessed = detectedPatternRepository
                .findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(userId).size();
        assertThat(unprocessed).isZero();
    }

    @Test
    @DisplayName("confirmed=false: 제안 DISMISSED, 미처리 패턴 processed_at 마킹")
    void dismissMarksDismissedAndProcessesPatterns() {
        seedDetectedPattern();

        replanService.dismiss(userId, proposalId);

        ReplanProposal updated = replanProposalRepository.findById(proposalId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ReplanProposalStatus.DISMISSED);
        assertThat(updated.getResolvedAt()).isNotNull();

        long unprocessed = detectedPatternRepository
                .findByUserIdAndProcessedAtIsNullOrderByCreatedAtDesc(userId).size();
        assertThat(unprocessed).isZero();
    }

    @Test
    @DisplayName("만료된 proposal은 PROPOSAL_EXPIRED")
    void expiredProposalThrows() {
        ReplanProposal expired = ReplanProposal.create(
                sessionId, userId, null, "reason",
                Instant.now().minus(1, ChronoUnit.HOURS)
        );
        Long expiredId = replanProposalRepository.save(expired).getId();

        assertThatThrownBy(() -> replanService.confirm(userId, sessionId, expiredId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROPOSAL_EXPIRED);
    }

    @Test
    @DisplayName("이미 처리된 proposal은 PROPOSAL_EXPIRED")
    void alreadyProcessedProposalThrows() {
        ReplanProposal proposal = replanProposalRepository.findById(proposalId).orElseThrow();
        proposal.dismiss(Instant.now());
        replanProposalRepository.save(proposal);

        assertThatThrownBy(() -> replanService.confirm(userId, sessionId, proposalId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROPOSAL_EXPIRED);
    }

    @Test
    @DisplayName("다른 사용자의 proposal 접근 시 FORBIDDEN")
    void otherUserProposalForbidden() {
        Long otherUserId = userId + 99999L;

        assertThatThrownBy(() -> replanService.confirm(otherUserId, sessionId, proposalId))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("존재하지 않는 proposalId는 PROPOSAL_NOT_FOUND")
    void nonexistentProposalThrows() {
        assertThatThrownBy(() -> replanService.confirm(userId, sessionId, 999_999_999L))
                .isInstanceOf(ServiceException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROPOSAL_NOT_FOUND);
    }

    private void seedDetectedPattern() {
        jdbc.update("""
                INSERT INTO detected_patterns (user_id, pattern_type, severity, metadata, idempotency_key)
                VALUES (?, 'REPEATED_INCOMPLETE', 'MEDIUM', '{}', ?)
                """, userId, "test-" + System.nanoTime());
    }
}
