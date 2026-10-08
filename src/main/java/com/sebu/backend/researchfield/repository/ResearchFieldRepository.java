package com.sebu.backend.researchfield.repository;
import com.sebu.backend.researchfield.domain.ResearchField;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResearchFieldRepository extends JpaRepository<ResearchField, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select researchField
        from ResearchField researchField
        where lower(researchField.name) = lower(:name)
        order by researchField.id
        """)
    List<ResearchField> findAllByNameIgnoreCaseForUpdate(
        @Param("name") String name
    );
}
