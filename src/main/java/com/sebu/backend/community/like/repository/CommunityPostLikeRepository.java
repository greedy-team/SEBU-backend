package com.sebu.backend.community.like.repository;

import com.sebu.backend.community.like.domain.CommunityPostLike;
import com.sebu.backend.community.like.domain.CommunityPostLikeId;
import com.sebu.backend.community.common.repository.PostCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, CommunityPostLikeId> {
    @Modifying
    @Query("delete from CommunityPostLike communityLike where communityLike.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO community_post_like (user_id, post_id) VALUES (:userId, :postId)", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query(value = "DELETE FROM community_post_like WHERE user_id = :userId AND post_id = :postId", nativeQuery = true)
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    @Query("""
            SELECT COUNT(communityLike)
            FROM CommunityPostLike communityLike
            WHERE communityLike.post.id = :postId
              AND communityLike.post.deletedAt IS NULL
              AND communityLike.user.deletedAt IS NULL
            """)
    long countActiveByPostId(@Param("postId") Long postId);

    @Query("""
            SELECT COUNT(communityLike)
            FROM CommunityPostLike communityLike
            WHERE communityLike.post.author.id = :authorId
              AND communityLike.post.deletedAt IS NULL
              AND communityLike.user.deletedAt IS NULL
            """)
    long countReceivedByAuthorId(@Param("authorId") Long authorId);

    @Query("""
            SELECT communityLike.post.id AS postId, COUNT(communityLike) AS count
            FROM CommunityPostLike communityLike
            WHERE communityLike.post.id IN :postIds
              AND communityLike.post.deletedAt IS NULL
              AND communityLike.user.deletedAt IS NULL
            GROUP BY communityLike.post.id
            """)
    List<PostCountProjection> countByPostIds(@Param("postIds") List<Long> postIds);
}
