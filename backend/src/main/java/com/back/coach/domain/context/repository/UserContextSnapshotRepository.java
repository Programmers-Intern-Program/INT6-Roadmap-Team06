package com.back.coach.domain.context.repository;

import com.back.coach.domain.context.entity.UserContextSnapshot;
import com.back.coach.global.code.ContextType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserContextSnapshotRepository extends JpaRepository<UserContextSnapshot, Long> {

    Optional<UserContextSnapshot> findTopByUserIdAndContextTypeAndValidToIsNullOrderByVersionDesc(
            Long userId,
            ContextType contextType
    );

    Optional<UserContextSnapshot> findTopByUserIdAndContextTypeOrderByVersionDescCreatedAtDesc(
            Long userId,
            ContextType contextType
    );

    default Optional<UserContextSnapshot> findActiveByUserIdAndContextType(Long userId, ContextType contextType) {
        return findTopByUserIdAndContextTypeAndValidToIsNullOrderByVersionDesc(userId, contextType);
    }

    default Optional<UserContextSnapshot> findLatestByUserIdAndContextType(Long userId, ContextType contextType) {
        return findTopByUserIdAndContextTypeOrderByVersionDescCreatedAtDesc(userId, contextType);
    }

    @Query("""
            select max(s.version)
            from UserContextSnapshot s
            where s.userId = :userId
              and s.contextType = :contextType
            """)
    Integer findMaxVersionByUserIdAndContextType(
            @Param("userId") Long userId,
            @Param("contextType") ContextType contextType
    );
}
