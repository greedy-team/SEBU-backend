package com.sebu.backend.user.repository;

import com.sebu.backend.user.domain.AppUser;
import com.sebu.backend.user.domain.AuthProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") Long userId);

    Optional<AppUser> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    @Query("select user.id from AppUser user where user.provider = :provider and user.providerUserId = :providerUserId")
    Optional<Long> findIdByProviderIdentity(@Param("provider") AuthProvider provider,
                                           @Param("providerUserId") String providerUserId);

    @Query("""
        select user.id from AppUser user
        where user.deletedAt <= :threshold
          and user.anonymizedAt is null
        order by user.deletedAt, user.id
        """)
    List<Long> findExpiredWithdrawalIds(@Param("threshold") LocalDateTime threshold, Pageable pageable);

    boolean existsByNicknameNormalizedAndIdNot(String nicknameNormalized, Long id);
}
