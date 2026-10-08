package com.sebu.backend.community.post.repository;

import com.sebu.backend.community.post.domain.CommunityPost;
import com.sebu.backend.community.post.domain.CommunityPostCategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = "author")
    Optional<CommunityPost> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p
            FROM CommunityPost p
            WHERE p.id = :postId AND p.deletedAt IS NULL
            """)
    Optional<CommunityPost> findForUpdate(@Param("postId") Long postId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT p
            FROM CommunityPost p
            WHERE p.id = :postId AND p.deletedAt IS NULL
            """)
    Optional<CommunityPost> findForShare(@Param("postId") Long postId);

    boolean existsByIdAndDeletedAtIsNull(Long id);

    Page<CommunityPost> findByAuthor_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
            Long authorId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query("""
            SELECT p
            FROM CommunityPost p
            WHERE p.deletedAt IS NULL
              AND (:category IS NULL OR p.category = :category)
              AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
            ORDER BY p.createdAt DESC, p.id DESC
            """)
    Page<CommunityPost> findLatest(
            @Param("category") CommunityPostCategory category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "author")
    @Query(
            value = """
                    SELECT p
                    FROM CommunityPost p
                    LEFT JOIN CommunityPostBookmark b
                      ON b.post = p AND b.user.deletedAt IS NULL
                    WHERE p.deletedAt IS NULL
                      AND (:category IS NULL OR p.category = :category)
                      AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
                    GROUP BY p
                    ORDER BY COUNT(b) DESC, p.createdAt DESC, p.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM CommunityPost p
                    WHERE p.deletedAt IS NULL
                      AND (:category IS NULL OR p.category = :category)
                      AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
                    """
    )
    Page<CommunityPost> findPopular(
            @Param("category") CommunityPostCategory category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CommunityPost p
            SET p.viewCount = p.viewCount + 1
            WHERE p.id = :postId AND p.deletedAt IS NULL
            """)
    int increaseViewCount(@Param("postId") Long postId);
}
