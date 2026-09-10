package com.sebu.backend.auth.domain;

import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;

@Getter
@Entity
@Table(
    name = "refresh_token",
    uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_hash", columnNames = "token_hash")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "session_id", nullable = false, updatable = false, length = 36)
    private String sessionId;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private LocalDateTime absoluteExpiresAt;

    public RefreshToken(AppUser user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt) {
        this(user, tokenHash, expiresAt, createdAt, UUID.randomUUID().toString(), expiresAt);
    }

    private RefreshToken(AppUser user, String tokenHash, LocalDateTime expiresAt, LocalDateTime createdAt,
                         String sessionId, LocalDateTime absoluteExpiresAt) {
        if (user == null) {
            throw new IllegalArgumentException("REFRESH_TOKEN_USER_REQUIRED");
        }
        if (tokenHash == null || tokenHash.isBlank()) {
            throw new IllegalArgumentException("REFRESH_TOKEN_HASH_REQUIRED");
        }
        if (tokenHash.length() != 64) {
            throw new IllegalArgumentException("REFRESH_TOKEN_HASH_INVALID");
        }
        if (createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("REFRESH_TOKEN_EXPIRATION_INVALID");
        }
        if (absoluteExpiresAt == null || expiresAt.isAfter(absoluteExpiresAt)) {
            throw new IllegalArgumentException("REFRESH_TOKEN_ABSOLUTE_EXPIRATION_INVALID");
        }
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.sessionId = sessionId;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public static RefreshToken start(AppUser user, String hash, LocalDateTime now,
                                     Duration idleLifetime, Duration absoluteLifetime) {
        LocalDateTime absoluteExpiry = now.plus(absoluteLifetime);
        return new RefreshToken(user, hash, earlier(now.plus(idleLifetime), absoluteExpiry), now,
            UUID.randomUUID().toString(), absoluteExpiry);
    }

    public RefreshToken rotate(String nextHash, LocalDateTime now, Duration idleLifetime) {
        if (!isUsableAt(now)) {
            throw new IllegalStateException("REFRESH_TOKEN_NOT_USABLE");
        }
        RefreshToken next = new RefreshToken(user, nextHash, earlier(now.plus(idleLifetime), absoluteExpiresAt),
            now, sessionId, absoluteExpiresAt);
        revoke(now);
        return next;
    }

    private static LocalDateTime earlier(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt) && now.isBefore(absoluteExpiresAt);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
