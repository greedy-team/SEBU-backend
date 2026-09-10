package com.sebu.backend.auth.repository;

import com.sebu.backend.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    long countByUser_Id(Long userId);

    @Query("select token.user.id from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<RefreshToken> findAllByUser_Id(Long userId);

    // Caller holds the user lock. Locking reads also see rotations committed while waiting on MySQL.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.user.id = :userId and token.sessionId = :sessionId and token.revokedAt is null")
    List<RefreshToken> findUnrevokedSessionForUpdate(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.user.id = :userId and token.revokedAt is null")
    List<RefreshToken> findAllUnrevokedForUpdate(@Param("userId") Long userId);

    @Query("select token.id from RefreshToken token where token.absoluteExpiresAt <= :now order by token.absoluteExpiresAt, token.id")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("delete from RefreshToken token where token.id in :ids and token.absoluteExpiresAt <= :now")
    int deleteExpiredIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
}
