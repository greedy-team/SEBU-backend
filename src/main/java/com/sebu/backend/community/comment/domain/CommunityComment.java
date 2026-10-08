package com.sebu.backend.community.comment.domain;

import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "community_comment",
        indexes = {
                @Index(name = "idx_community_comment_post_active_created", columnList = "post_id, deleted_at, created_at, id"),
                @Index(name = "idx_community_comment_author_active", columnList = "author_id, deleted_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityComment extends BaseTimeEntity {
    private static final int CONTENT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public CommunityComment(CommunityPost post, AppUser author, String content) {
        this.post = Objects.requireNonNull(post, "COMMUNITY_COMMENT_POST_REQUIRED");
        this.author = Objects.requireNonNull(author, "COMMUNITY_COMMENT_AUTHOR_REQUIRED");
        this.content = requireContent(content);
    }

    public void updateContent(String content) {
        this.content = requireContent(content);
    }

    public void softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String requireContent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("COMMUNITY_COMMENT_CONTENT_REQUIRED");
        }
        String normalized = value.trim();
        if (normalized.length() > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("COMMUNITY_COMMENT_CONTENT_TOO_LONG");
        }
        return normalized;
    }
}
