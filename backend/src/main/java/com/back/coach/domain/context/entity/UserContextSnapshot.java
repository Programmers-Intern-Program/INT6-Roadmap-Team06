package com.back.coach.domain.context.entity;

import com.back.coach.global.code.ContextType;
import com.back.coach.global.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Entity
@Table(name = "user_context_snapshots")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserContextSnapshot extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 30)
    private ContextType contextType;

    @Column(name = "version", nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to")
    private Instant validTo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static UserContextSnapshot create(
            Long userId,
            ContextType contextType,
            Integer version,
            String payload,
            Instant validFrom
    ) {
        UserContextSnapshot snapshot = new UserContextSnapshot();
        snapshot.userId = userId;
        snapshot.contextType = contextType;
        snapshot.version = version;
        snapshot.payload = payload;
        snapshot.validFrom = validFrom;
        return snapshot;
    }

    public void close(Instant validTo) {
        this.validTo = validTo;
    }
}
