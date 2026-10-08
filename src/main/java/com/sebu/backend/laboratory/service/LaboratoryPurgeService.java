package com.sebu.backend.laboratory.service;

import com.sebu.backend.laboratory.config.LaboratoryRetentionProperties;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LaboratoryPurgeService {
    private final LaboratoryRepository laboratoryRepository;
    private final LaboratoryRetentionProperties retentionProperties;

    @Transactional
    public int purgeExpiredLaboratories(LocalDateTime currentTime) {
        LocalDateTime threshold = currentTime.minusDays(retentionProperties.getDays());
        return laboratoryRepository.deleteAllSoftDeletedBeforeOrEqual(threshold);
    }
}
