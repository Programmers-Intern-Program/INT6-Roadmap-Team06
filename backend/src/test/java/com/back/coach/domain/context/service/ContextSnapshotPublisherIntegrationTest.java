package com.back.coach.domain.context.service;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.domain.context.repository.UserContextSnapshotRepository;
import com.back.coach.domain.user.entity.User;
import com.back.coach.domain.user.repository.UserRepository;
import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.code.ContextType;
import com.back.coach.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ContextSnapshotPublisherIntegrationTest {

    @Autowired
    private ContextSnapshotPublisher publisher;

    @Autowired
    private UserContextSnapshotRepository snapshotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long userId;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(
                User.signupFromOAuth(AuthProvider.GITHUB,
                        "gh-snapshot-pub-" + System.nanoTime(),
                        "snapshot-pub@test.com")
        );
        userId = user.getId();
    }

    @AfterEach
    void cleanUp() {
        snapshotRepository.deleteAll(snapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .toList());
        userRepository.deleteById(userId);
    }

    @Test
    @DisplayName("publishProfile: 활성 트랜잭션 없이 호출 시 PROFILE snapshot 1건 active로 저장")
    void publishProfileCreatesActiveSnapshot() {
        publisher.publishProfile(userId);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishPlan: 활성 트랜잭션 없이 호출 시 PLAN snapshot 1건 active로 저장")
    void publishPlanCreatesActiveSnapshot() {
        publisher.publishPlan(userId);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PLAN);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishProfile: 활성 트랜잭션 afterCommit 이후에도 PROFILE snapshot이 저장된다")
    void publishProfileInsideTransactionCreatesSnapshotAfterCommit() {
        transactionTemplate.executeWithoutResult(status -> publisher.publishProfile(userId));

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(1);
        assertThat(active.get().getValidTo()).isNull();
    }

    @Test
    @DisplayName("publishProfile 두 번 호출 시 이전 snapshot은 close되고 새 version active")
    void publishProfileTwiceClosesPreviousVersion() {
        publisher.publishProfile(userId);
        publisher.publishProfile(userId);

        long total = snapshotRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(userId))
                .filter(s -> s.getContextType() == ContextType.PROFILE)
                .count();
        assertThat(total).isEqualTo(2);

        Optional<UserContextSnapshot> active =
                snapshotRepository.findActiveByUserIdAndContextType(userId, ContextType.PROFILE);
        assertThat(active).isPresent();
        assertThat(active.get().getVersion()).isEqualTo(2);
    }
}
