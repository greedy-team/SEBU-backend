package com.sebu.backend.community.comment.repository;

import com.sebu.backend.community.comment.domain.CommunityComment;
import com.sebu.backend.community.common.repository.PostCountProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    Optional<CommunityComment> findByIdAndPost_IdAndDeletedAtIsNull(Long id, Long postId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT c
            FROM CommunityComment c
            WHERE c.id = :commentId
              AND c.post.id = :postId
              AND c.deletedAt IS NULL
            """)
    Optional<CommunityComment> findForUpdate(
            @Param("commentId") Long commentId,
            @Param("postId") Long postId
    );

    @EntityGraph(attributePaths = "author")
    Page<CommunityComment> findByPost_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            Long postId,
            Pageable pageable
    );

    long countByPost_IdAndDeletedAtIsNull(Long postId);

    @Query("""
            SELECT COUNT(c)
            FROM CommunityComment c
            WHERE c.author.id = :authorId
              AND c.deletedAt IS NULL
              AND c.post.deletedAt IS NULL
            """)
    long countActiveByAuthorId(@Param("authorId") Long authorId);

    @Query("""
            SELECT c.post.id AS postId, COUNT(c) AS count
            FROM CommunityComment c
            WHERE c.deletedAt IS NULL AND c.post.id IN :postIds
            GROUP BY c.post.id
            """)
    List<PostCountProjection> countActiveByPostIds(@Param("postIds") List<Long> postIds);
}
