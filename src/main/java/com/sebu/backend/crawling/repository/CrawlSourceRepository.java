package com.sebu.backend.crawling.repository;

import com.sebu.backend.crawling.domain.CrawlSource;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CrawlSourceRepository extends JpaRepository<CrawlSource, Long> {
    List<CrawlSource> findAllByActiveTrueOrderByIdAsc();
    Optional<CrawlSource> findBySourceUrl(String sourceUrl);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select source from CrawlSource source where source.id = :sourceId")
    Optional<CrawlSource> findByIdForUpdate(@Param("sourceId") Long sourceId);
}
