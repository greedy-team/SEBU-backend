package com.sebu.backend.laboratory.repository;

import com.sebu.backend.laboratory.domain.LaboratoryDepartment;
import com.sebu.backend.laboratory.domain.LaboratoryDepartmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LaboratoryDepartmentRepository
        extends JpaRepository<LaboratoryDepartment, LaboratoryDepartmentId> {

    boolean existsByLaboratory_IdAndDepartment_Id(
            Long laboratoryId,
            Long departmentId
    );

    @Query("""
        select case when count(affiliation) > 0 then true else false end
        from LaboratoryDepartment affiliation
        join affiliation.laboratory laboratory
        where affiliation.department.id = :departmentId
          and laboratory.name = :name
          and laboratory.deletedAt is null
          and (:excludedLaboratoryId is null or laboratory.id <> :excludedLaboratoryId)
        """)
    boolean existsActiveLaboratoryName(
            @Param("departmentId") Long departmentId,
            @Param("name") String name,
            @Param("excludedLaboratoryId") Long excludedLaboratoryId
    );

    @Query("""
        select laboratory.id as laboratoryId,
               college.id as collegeId,
               college.name as collegeName,
               department.id as departmentId,
               department.name as departmentName
        from LaboratoryDepartment affiliation
        join affiliation.laboratory laboratory
        join affiliation.department department
        join department.college college
        where laboratory.id in :laboratoryIds
        order by laboratory.id,
                 case when department.id = laboratory.department.id then 0 else 1 end,
                 department.id
        """)
    List<LaboratoryAffiliationProjection> findAffiliationsByLaboratoryIds(
            @Param("laboratoryIds") Collection<Long> laboratoryIds
    );

    @Query("""
        select d.college.id as collegeId,
               count(distinct ld.laboratory.id) as laboratoryCount
        from LaboratoryDepartment ld
        join ld.department d
        where ld.laboratory.deletedAt is null
        group by d.college.id
        """)
    List<CollegeLaboratoryCountProjection> countActiveLaboratoriesByCollege();
}
