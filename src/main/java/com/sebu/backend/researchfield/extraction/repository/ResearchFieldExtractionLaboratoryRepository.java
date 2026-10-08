package com.sebu.backend.researchfield.extraction.repository;

import com.sebu.backend.laboratory.domain.Laboratory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResearchFieldExtractionLaboratoryRepository
    extends Repository<Laboratory, Long> {

    @Query("select laboratory.id from Laboratory laboratory order by laboratory.id")
    List<Long> findAllLaboratoryIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select laboratory from Laboratory laboratory where laboratory.id = :laboratoryId")
    Optional<Laboratory> findByIdForUpdate(@Param("laboratoryId") Long laboratoryId);
}
