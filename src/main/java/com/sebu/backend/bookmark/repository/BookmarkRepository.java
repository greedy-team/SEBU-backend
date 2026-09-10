package com.sebu.backend.bookmark.repository;

import com.sebu.backend.bookmark.domain.Bookmark;
import com.sebu.backend.bookmark.domain.BookmarkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookmarkRepository extends JpaRepository<Bookmark, BookmarkId> {

    @Modifying
    @Query("delete from Bookmark bookmark where bookmark.user.id = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);

    long countByUser_IdAndLaboratory_DeletedAtIsNull(Long userId);

    @Modifying(flushAutomatically = true)
    @Query(
            value = """
                    INSERT IGNORE INTO bookmark (user_id, laboratory_id)
                    VALUES (:userId, :laboratoryId)
                    """,
            nativeQuery = true
    )
    int insertIgnore(
            @Param("userId") Long userId,
            @Param("laboratoryId") Long laboratoryId
    );

    @Query("""
            select count(b)
            from Bookmark b
            where b.laboratory.id = :laboratoryId
              and b.user.deletedAt is null
            """)
    long countActiveByLaboratoryId(
            @Param("laboratoryId") Long laboratoryId
    );

    @Query("""
            select b
            from Bookmark b
            where b.user.id = :userId
              and b.laboratory.deletedAt is null
            order by b.createdAt desc, b.laboratory.id desc
            """)
    List<Bookmark> findBookmarkedLaboratories(
            @Param("userId") Long userId
    );
}
