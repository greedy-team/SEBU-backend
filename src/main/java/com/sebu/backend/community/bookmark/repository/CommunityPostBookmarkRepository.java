package com.sebu.backend.community.bookmark.repository;

import com.sebu.backend.community.bookmark.domain.CommunityPostBookmark;
import com.sebu.backend.community.bookmark.domain.CommunityPostBookmarkId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunityPostBookmarkRepository extends JpaRepository<CommunityPostBookmark, CommunityPostBookmarkId> {
    long countByUser_IdAndPost_DeletedAtIsNull(Long userId);

    @EntityGraph(attributePaths = {"post", "post.author"})
    List<CommunityPostBookmark> findByUser_IdAndPost_DeletedAtIsNullOrderByCreatedAtDescPost_IdDesc(
            Long userId
    );

    @Query("""
            SELECT COUNT(bookmark)
            FROM CommunityPostBookmark bookmark
            WHERE bookmark.post.author.id = :authorId
              AND bookmark.post.deletedAt IS NULL
              AND bookmark.user.deletedAt IS NULL
            """)
    long countActiveReceivedByAuthorId(@Param("authorId") Long authorId);

    @Modifying
    @Query(value = "INSERT IGNORE INTO community_post_bookmark (user_id, post_id) VALUES (:userId, :postId)", nativeQuery = true)
    int insertIgnore(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query(value = "DELETE FROM community_post_bookmark WHERE user_id = :userId AND post_id = :postId", nativeQuery = true)
    int deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
}
