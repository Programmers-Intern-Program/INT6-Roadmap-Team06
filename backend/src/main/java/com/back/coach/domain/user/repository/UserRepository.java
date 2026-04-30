package com.back.coach.domain.user.repository;

import com.back.coach.domain.user.entity.User;
import com.back.coach.global.code.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);

    Optional<User> findByEmail(String email);
}
