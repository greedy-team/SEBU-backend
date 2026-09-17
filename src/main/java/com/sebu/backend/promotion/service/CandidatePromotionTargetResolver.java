package com.sebu.backend.promotion.service;

import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.laboratory.domain.Laboratory;
import com.sebu.backend.laboratory.domain.LaboratoryNameSource;
import com.sebu.backend.laboratory.domain.RecruitmentStatus;
import com.sebu.backend.laboratory.domain.WebsiteUrlSource;
import com.sebu.backend.laboratory.repository.LaboratoryRepository;
import com.sebu.backend.professor.domain.Professor;
import com.sebu.backend.professor.repository.ProfessorRepository;
import com.sebu.backend.promotion.exception.CandidatePromotionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class CandidatePromotionTargetResolver {
    private final ProfessorRepository professorRepository;
    private final LaboratoryRepository laboratoryRepository;

    PromotionTargets resolve(ProfessorCrawlCandidate candidate) {
        ProfessorResolution professorResolution = resolveProfessor(candidate);
        LaboratoryResolution laboratoryResolution = resolveLaboratory(
            candidate,
            professorResolution.professor()
        );
        return new PromotionTargets(
            professorResolution.professor(),
            laboratoryResolution.laboratory(),
            professorResolution.created() || laboratoryResolution.created(),
            professorResolution.updated() || laboratoryResolution.updated()
        );
    }

    PromotionTargets refresh(ProfessorCrawlCandidate candidate) {
        if (candidate.getPromotedProfessor() == null
            || candidate.getPromotedLaboratory() == null) {
            throw new CandidatePromotionException("PROMOTED_ENTITY_WAS_REMOVED");
        }
        Professor professor = professorRepository.findByIdForUpdate(
                candidate.getPromotedProfessor().getId()
            )
            .orElseThrow(() -> new CandidatePromotionException(
                "PROMOTED_PROFESSOR_NOT_FOUND"
            ));
        Laboratory laboratory = laboratoryRepository.findByIdForUpdate(
                candidate.getPromotedLaboratory().getId()
            )
            .orElseThrow(() -> new CandidatePromotionException(
                "PROMOTED_LABORATORY_NOT_FOUND"
            ));
        if (laboratory.isDeleted()) {
            throw new CandidatePromotionException("PROMOTED_LABORATORY_IS_DELETED");
        }
        boolean updated = refresh(candidate, professor, laboratory);
        return new PromotionTargets(professor, laboratory, false, updated);
    }

    private boolean refresh(
        ProfessorCrawlCandidate candidate,
        Professor professor,
        Laboratory laboratory
    ) {
        ensureLaboratoryOwnedByProfessor(professor, laboratory);
        if (candidate.getEmail() != null && professorRepository.existsByEmailAndIdNot(
            candidate.getEmail(),
            professor.getId()
        )) {
            throw new CandidatePromotionException("PROFESSOR_EMAIL_CONFLICT");
        }
        PromotionLaboratoryName laboratoryName = resolveLaboratoryName(candidate);
        boolean professorChanged = refreshProfessor(candidate, professor);
        boolean laboratoryChanged = refreshLaboratory(
            candidate,
            laboratory,
            laboratoryName
        );
        return professorChanged || laboratoryChanged;
    }

    private boolean refreshProfessor(
        ProfessorCrawlCandidate candidate,
        Professor professor
    ) {
        if (belongsToPrimaryDepartment(candidate, professor)) {
            boolean changed = !professor.hasPromotionProfile(
                candidate.getProfessorName(),
                candidate.getPosition(),
                candidate.getEmail()
            );
            if (!changed) {
                return false;
            }
            professor.updateFromPromotion(
                candidate.getProfessorName(),
                candidate.getPosition(),
                candidate.getEmail()
            );
            return true;
        }
        if (!professor.hasPromotionIdentity(
            candidate.getProfessorName(),
            candidate.getEmail()
        )) {
            throw new CandidatePromotionException("PROFESSOR_IDENTITY_CONFLICT");
        }
        return false;
    }

    private boolean refreshLaboratory(
        ProfessorCrawlCandidate candidate,
        Laboratory laboratory,
        PromotionLaboratoryName laboratoryName
    ) {
        if (belongsToPrimaryDepartment(candidate, laboratory)) {
            PromotionLaboratoryName effectiveName = preserveOfficialName(
                laboratory,
                laboratoryName
            );
            boolean changed = !laboratory.hasPromotionDetails(
                effectiveName.name(),
                candidate.getHomepageUrl(),
                candidate.getResearchIntroduction(),
                effectiveName.source()
            );
            if (!changed) {
                return false;
            }
            laboratory.updateFromPromotion(
                effectiveName.name(),
                candidate.getHomepageUrl(),
                candidate.getResearchIntroduction(),
                effectiveName.source()
            );
            return true;
        }
        try {
            return laboratory.mergeFromPromotion(
                laboratoryName.name(),
                candidate.getHomepageUrl(),
                candidate.getResearchIntroduction(),
                laboratoryName.source()
            );
        } catch (IllegalStateException exception) {
            throw new CandidatePromotionException(exception.getMessage(), exception);
        }
    }

    private PromotionLaboratoryName preserveOfficialName(
        Laboratory laboratory,
        PromotionLaboratoryName requested
    ) {
        if (laboratory.getNameSource() == LaboratoryNameSource.OFFICIAL
            && requested.source() == LaboratoryNameSource.GENERATED) {
            return new PromotionLaboratoryName(
                laboratory.getName(),
                LaboratoryNameSource.OFFICIAL
            );
        }
        return requested;
    }

    private ProfessorResolution resolveProfessor(ProfessorCrawlCandidate candidate) {
        if (candidate.getEmail() == null) {
            return new ProfessorResolution(createProfessor(candidate), true, false);
        }
        return professorRepository.findByEmailForUpdate(candidate.getEmail())
            .map(professor -> mergeProfessor(professor, candidate))
            .orElseGet(() -> new ProfessorResolution(createProfessor(candidate), true, false));
    }

    private Professor createProfessor(ProfessorCrawlCandidate candidate) {
        return professorRepository.save(new Professor(
            candidate.getSource().getDepartment(),
            candidate.getProfessorName(),
            candidate.getPosition(),
            candidate.getEmail()
        ));
    }

    private ProfessorResolution mergeProfessor(
        Professor professor,
        ProfessorCrawlCandidate candidate
    ) {
        if (!professor.hasPromotionIdentity(candidate.getProfessorName(), candidate.getEmail())) {
            throw new CandidatePromotionException("PROFESSOR_IDENTITY_CONFLICT");
        }
        if (!belongsToPrimaryDepartment(candidate, professor)) {
            return new ProfessorResolution(professor, false, false);
        }
        boolean updated;
        try {
            updated = professor.mergePromotionPosition(candidate.getPosition());
        } catch (IllegalStateException exception) {
            throw new CandidatePromotionException(exception.getMessage(), exception);
        }
        return new ProfessorResolution(professor, false, updated);
    }

    private LaboratoryResolution resolveLaboratory(
        ProfessorCrawlCandidate candidate,
        Professor professor
    ) {
        PromotionLaboratoryName requested = resolveLaboratoryName(candidate);
        List<Laboratory> activeLaboratories = professor.getId() == null
            ? List.of()
            : laboratoryRepository.findActiveByProfessorIdForUpdate(professor.getId());
        if (activeLaboratories.isEmpty()) {
            return new LaboratoryResolution(
                createLaboratory(candidate, professor, requested),
                true,
                false
            );
        }

        List<Laboratory> nameMatches = activeLaboratories.stream()
            .filter(laboratory -> Objects.equals(laboratory.getName(), requested.name()))
            .toList();
        Laboratory laboratory;
        if (nameMatches.size() == 1) {
            laboratory = nameMatches.getFirst();
        } else if (nameMatches.isEmpty() && activeLaboratories.size() == 1) {
            laboratory = activeLaboratories.getFirst();
        } else {
            throw new CandidatePromotionException("AMBIGUOUS_PROFESSOR_LABORATORY");
        }

        try {
            boolean updated = laboratory.mergeFromPromotion(
                requested.name(),
                candidate.getHomepageUrl(),
                candidate.getResearchIntroduction(),
                requested.source()
            );
            return new LaboratoryResolution(laboratory, false, updated);
        } catch (IllegalStateException exception) {
            throw new CandidatePromotionException(exception.getMessage(), exception);
        }
    }

    private Laboratory createLaboratory(
        ProfessorCrawlCandidate candidate,
        Professor professor,
        PromotionLaboratoryName laboratoryName
    ) {
        return laboratoryRepository.save(new Laboratory(
            professor,
            professor.getDepartment(),
            laboratoryName.name(),
            candidate.getHomepageUrl(),
            candidate.getResearchIntroduction(),
            RecruitmentStatus.UNKNOWN,
            laboratoryName.source(),
            WebsiteUrlSource.CRAWLED
        ));
    }

    private PromotionLaboratoryName resolveLaboratoryName(
        ProfessorCrawlCandidate candidate
    ) {
        if (candidate.getLaboratoryName() != null) {
            return new PromotionLaboratoryName(
                candidate.getLaboratoryName(),
                LaboratoryNameSource.OFFICIAL
            );
        }
        return new PromotionLaboratoryName(
            candidate.getProfessorName() + " 교수님 연구실",
            LaboratoryNameSource.GENERATED
        );
    }

    private void ensureLaboratoryOwnedByProfessor(
        Professor professor,
        Laboratory laboratory
    ) {
        if (!Objects.equals(laboratory.getProfessor().getId(), professor.getId())) {
            throw new CandidatePromotionException("PROMOTED_ENTITY_OWNERSHIP_CONFLICT");
        }
    }

    private boolean belongsToPrimaryDepartment(
        ProfessorCrawlCandidate candidate,
        Professor professor
    ) {
        return Objects.equals(
            candidate.getSource().getDepartment().getId(),
            professor.getDepartment().getId()
        );
    }

    private boolean belongsToPrimaryDepartment(
        ProfessorCrawlCandidate candidate,
        Laboratory laboratory
    ) {
        return Objects.equals(
            candidate.getSource().getDepartment().getId(),
            laboratory.getDepartment().getId()
        );
    }

    private record ProfessorResolution(Professor professor, boolean created, boolean updated) {
    }

    private record LaboratoryResolution(
        Laboratory laboratory,
        boolean created,
        boolean updated
    ) {
    }

    private record PromotionLaboratoryName(String name, LaboratoryNameSource source) {
    }
}
