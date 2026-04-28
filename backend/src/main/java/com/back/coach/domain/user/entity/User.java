package com.back.coach.domain.user.entity;

import com.back.coach.global.code.AuthProvider;
import com.back.coach.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 30)
    private AuthProvider authProvider;

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    private User(AuthProvider authProvider, String providerUserId, String email) {
        this.authProvider = authProvider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.active = Boolean.TRUE;
    }

    public static User signupFromOAuth(AuthProvider authProvider, String providerUserId, String email) {
        if (authProvider == null || authProvider == AuthProvider.LOCAL) {
            throw new IllegalArgumentException("authProvider must be an OAuth provider");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        return new User(authProvider, providerUserId, email);
    }
}
