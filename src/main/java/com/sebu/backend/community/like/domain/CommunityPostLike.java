package com.sebu.backend.community.like.domain;

import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.user.domain.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(name = "community_post_like")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostLike {
    @EmbeddedId
    private CommunityPostLikeId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CommunityPostLike(AppUser user, CommunityPost post) {
        this.user = Objects.requireNonNull(user, "COMMUNITY_POST_LIKE_USER_REQUIRED");
        this.post = Objects.requireNonNull(post, "COMMUNITY_POST_LIKE_POST_REQUIRED");
        this.id = new CommunityPostLikeId(
                Objects.requireNonNull(user.getId(), "COMMUNITY_POST_LIKE_USER_ID_REQUIRED"),
                Objects.requireNonNull(post.getId(), "COMMUNITY_POST_LIKE_POST_ID_REQUIRED")
        );
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
