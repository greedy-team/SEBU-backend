package com.sebu.backend.community.post.domain;

import com.sebu.backend.global.domain.BaseTimeEntity;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "community_post",
        indexes = {
                @Index(name = "idx_community_post_active_created", columnList = "deleted_at, created_at, id"),
                @Index(name = "idx_community_post_category_active_created", columnList = "category, deleted_at, created_at, id"),
                @Index(name = "idx_community_post_author_active_created", columnList = "author_id, deleted_at, created_at, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPost extends BaseTimeEntity {
    private static final int TITLE_MAX_LENGTH = 100;
    private static final int CONTENT_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommunityPostCategory category;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public CommunityPost(AppUser author, CommunityPostCategory category, String title, String content) {
        this.author = Objects.requireNonNull(author, "COMMUNITY_POST_AUTHOR_REQUIRED");
        this.category = Objects.requireNonNull(category, "COMMUNITY_POST_CATEGORY_REQUIRED");
        this.title = requireText(title, TITLE_MAX_LENGTH, "COMMUNITY_POST_TITLE");
        this.content = requireText(content, CONTENT_MAX_LENGTH, "COMMUNITY_POST_CONTENT");
    }

    public void update(CommunityPostCategory category, String title, String content) {
        this.category = Objects.requireNonNull(category, "COMMUNITY_POST_CATEGORY_REQUIRED");
        this.title = requireText(title, TITLE_MAX_LENGTH, "COMMUNITY_POST_TITLE");
        this.content = requireText(content, CONTENT_MAX_LENGTH, "COMMUNITY_POST_CONTENT");
    }

    public void increaseViewCount() {
        viewCount++;
    }

    public void softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "_REQUIRED");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + "_TOO_LONG");
        }
        return normalized;
    }
}
