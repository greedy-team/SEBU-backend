package com.sebu.backend.laboratory.repository;

import com.sebu.backend.laboratory.domain.Laboratory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    boolean existsByDepartmentIdAndNameAndDeletedAtIsNull(
            Long departmentId,
            String name
    );

    boolean existsByDepartmentIdAndNameAndDeletedAtIsNullAndIdNot(
            Long departmentId,
            String name,
            Long id
    );

    Optional<Laboratory> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select laboratory
        from Laboratory laboratory
        where laboratory.id = :laboratoryId
        """)
    Optional<Laboratory> findByIdForUpdate(
            @Param("laboratoryId") Long laboratoryId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select laboratory
        from Laboratory laboratory
        where laboratory.professor.id = :professorId
          and laboratory.deletedAt is null
        order by laboratory.id
        """)
    List<Laboratory> findActiveByProfessorIdForUpdate(
            @Param("professorId") Long professorId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        DELETE FROM laboratory
        WHERE deleted_at IS NOT NULL
          AND deleted_at <= :threshold
        """, nativeQuery = true)
    int deleteAllSoftDeletedBeforeOrEqual(
            @Param("threshold") LocalDateTime threshold
    );

    @Query("""
        select l.id as id,
               l.name as name,
               l.nameSource as nameSource,
               l.websiteUrl as websiteUrl,
               p.id as professorId,
               p.name as professorName,
               p.email as professorEmail,
               c.id as collegeId,
               c.name as collegeName,
               d.id as departmentId,
               d.name as departmentName,
               l.recruitmentStatus as recruitmentStatus,

               count(distinct b.user.id) as bookmarkCount,

               case when sum(
                    case when b.user.id = :userId then 1 else 0 end
               ) > 0 then true else false end as bookmarked

        from Laboratory l

        join l.professor p
        join l.department d
        join d.college c

        left join Bookmark b
            on b.laboratory = l
            and b.user.deletedAt is null

        where l.deletedAt is null

        group by
            l.id,
            l.name,
            l.nameSource,
            l.websiteUrl,
            p.id,
            p.name,
            p.email,
            c.id,
            c.name,
            d.id,
            d.name,
            l.recruitmentStatus

        order by l.id
        """)
    List<LaboratorySummaryProjection> findAllSummaries(
            @Param("userId") Long userId
    );

    /*
     * 특정 ID 목록의 연구실 요약 조회
     *
     * 후기 수는 LaboratoryReviewQueryRepository에서
     * laboratory ID 목록 기준으로 별도 batch 집계한다.
     */
    @Query("""
        select l.id as id,
               l.name as name,
               l.nameSource as nameSource,
               l.websiteUrl as websiteUrl,
               p.id as professorId,
               p.name as professorName,
               p.email as professorEmail,
               c.id as collegeId,
               c.name as collegeName,
               d.id as departmentId,
               d.name as departmentName,
               l.recruitmentStatus as recruitmentStatus,

               count(distinct b.user.id) as bookmarkCount,

               case when sum(
                    case when b.user.id = :userId then 1 else 0 end
               ) > 0 then true else false end as bookmarked

        from Laboratory l

        join l.professor p
        join l.department d
        join d.college c

        left join Bookmark b
            on b.laboratory = l
            and b.user.deletedAt is null

        where l.deletedAt is null
          and l.id in :laboratoryIds

        group by
            l.id,
            l.name,
            l.nameSource,
            l.websiteUrl,
            p.id,
            p.name,
            p.email,
            c.id,
            c.name,
            d.id,
            d.name,
            l.recruitmentStatus
        """)
    List<LaboratorySummaryProjection> findSummariesByIds(
            @Param("userId") Long userId,
            @Param("laboratoryIds") List<Long> laboratoryIds
    );
}
