package com.sebu.backend.auth.repository;

import com.sebu.backend.auth.domain.AccountRecoveryToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountRecoveryTokenRepository extends JpaRepository<AccountRecoveryToken, Long> {
    @Query("select token.user.id from AccountRecoveryToken token where token.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from AccountRecoveryToken token where token.tokenHash = :tokenHash")
    Optional<AccountRecoveryToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("delete from AccountRecoveryToken token where token.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Query("select token.id from AccountRecoveryToken token where token.expiresAt <= :now order by token.expiresAt, token.id")
    List<Long> findExpiredIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("delete from AccountRecoveryToken token where token.id in :ids and token.expiresAt <= :now")
    int deleteExpiredIds(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);

    long countByUser_Id(Long userId);
}
