package com.sebu.backend.user.repository;

import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Long userId);

    Optional<AppUser> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByNicknameNormalizedAndIdNot(String nicknameNormalized, Long id);
}
