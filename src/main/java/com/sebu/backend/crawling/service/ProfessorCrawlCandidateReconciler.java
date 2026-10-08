package com.sebu.backend.crawling.service;

import com.sebu.backend.crawling.dto.ProfessorCrawlReconciliation;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.domain.ProfessorCrawlCandidate;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class ProfessorCrawlCandidateReconciler {
    public ProfessorCrawlReconciliation reconcile(
        List<ProfessorCrawlCandidate> existingCandidates,
        List<ProfessorCrawlData> crawledData
    ) {
        List<ProfessorCrawlCandidate> existing = List.copyOf(existingCandidates);
        List<ProfessorCrawlData> uniqueData = deduplicate(crawledData);
        CandidateIndexes indexes = CandidateIndexes.from(existing);
        Map<String, Integer> incomingNameCounts = countByProfessorName(uniqueData);
        Map<String, ProfessorCrawlCandidate> matches = reserveExactMatches(
            uniqueData,
            indexes
        );
        Set<ProfessorCrawlCandidate> claimedCandidates = new HashSet<>(matches.values());
        reserveEmailMatches(uniqueData, indexes, matches, claimedCandidates);
        reserveHomepageAndNameMatches(uniqueData, indexes, matches, claimedCandidates);
        reserveUniqueNameMatches(
            uniqueData,
            indexes,
            incomingNameCounts,
            matches,
            claimedCandidates
        );
        List<ProfessorCrawlReconciliation.Item> items = new ArrayList<>(uniqueData.size());

        for (ProfessorCrawlData data : uniqueData) {
            ProfessorCrawlCandidate candidate = matches.get(data.identityKey());
            items.add(new ProfessorCrawlReconciliation.Item(
                data,
                candidate,
                resolveIdentityKey(candidate, data)
            ));
        }

        List<ProfessorCrawlCandidate> missingCandidates = existing.stream()
            .filter(candidate -> !claimedCandidates.contains(candidate))
            .toList();
        return new ProfessorCrawlReconciliation(items, missingCandidates);
    }

    private Map<String, ProfessorCrawlCandidate> reserveExactMatches(
        List<ProfessorCrawlData> crawledData,
        CandidateIndexes indexes
    ) {
        Map<String, ProfessorCrawlCandidate> exactMatches = new HashMap<>();
        for (ProfessorCrawlData data : crawledData) {
            ProfessorCrawlCandidate candidate = indexes.byIdentity().get(data.identityKey());
            if (candidate != null) {
                exactMatches.put(data.identityKey(), candidate);
            }
        }
        return exactMatches;
    }

    private void reserveEmailMatches(
        List<ProfessorCrawlData> crawledData,
        CandidateIndexes indexes,
        Map<String, ProfessorCrawlCandidate> matches,
        Set<ProfessorCrawlCandidate> claimedCandidates
    ) {
        for (ProfessorCrawlData data : crawledData) {
            if (isMatched(data, matches) || data.email() == null) {
                continue;
            }
            ProfessorCrawlCandidate candidate = findUniqueAvailable(
                indexes.byEmail().get(data.email()),
                claimedCandidates,
                "email:" + data.email()
            );
            reserve(data, candidate, matches, claimedCandidates);
        }
    }

    private void reserveHomepageAndNameMatches(
        List<ProfessorCrawlData> crawledData,
        CandidateIndexes indexes,
        Map<String, ProfessorCrawlCandidate> matches,
        Set<ProfessorCrawlCandidate> claimedCandidates
    ) {
        Map<HomepageName, Integer> aliasCounts = new HashMap<>();
        for (ProfessorCrawlData data : crawledData) {
            if (!isMatched(data, matches) && data.homepageUrl() != null) {
                aliasCounts.merge(HomepageName.from(data), 1, Integer::sum);
            }
        }

        for (ProfessorCrawlData data : crawledData) {
            HomepageName alias = HomepageName.from(data);
            if (isMatched(data, matches)
                || data.homepageUrl() == null
                || aliasCounts.getOrDefault(alias, 0) != 1) {
                continue;
            }
            List<ProfessorCrawlCandidate> candidates = indexes.byHomepage()
                .getOrDefault(data.homepageUrl(), List.of())
                .stream()
                .filter(candidate -> candidate.getProfessorName().equals(data.professorName()))
                .toList();
            ProfessorCrawlCandidate candidate = findUniqueAvailable(
                candidates,
                claimedCandidates,
                "homepage-and-name:" + data.homepageUrl() + ":" + data.professorName()
            );
            reserve(data, candidate, matches, claimedCandidates);
        }
    }

    private void reserveUniqueNameMatches(
        List<ProfessorCrawlData> crawledData,
        CandidateIndexes indexes,
        Map<String, Integer> incomingNameCounts,
        Map<String, ProfessorCrawlCandidate> matches,
        Set<ProfessorCrawlCandidate> claimedCandidates
    ) {
        for (ProfessorCrawlData data : crawledData) {
            if (isMatched(data, matches)
                || incomingNameCounts.getOrDefault(data.professorName(), 0) != 1) {
                continue;
            }
            List<ProfessorCrawlCandidate> candidates = indexes.byName()
                .getOrDefault(data.professorName(), List.of())
                .stream()
                .filter(candidate -> eligibleForNameFallback(candidate, data))
                .toList();
            ProfessorCrawlCandidate candidate = findUniqueAvailable(
                candidates,
                claimedCandidates,
                "name:" + data.professorName()
            );
            reserve(data, candidate, matches, claimedCandidates);
        }
    }

    private boolean isMatched(
        ProfessorCrawlData data,
        Map<String, ProfessorCrawlCandidate> matches
    ) {
        return matches.containsKey(data.identityKey());
    }

    private void reserve(
        ProfessorCrawlData data,
        ProfessorCrawlCandidate candidate,
        Map<String, ProfessorCrawlCandidate> matches,
        Set<ProfessorCrawlCandidate> claimedCandidates
    ) {
        if (candidate == null) {
            return;
        }
        matches.put(data.identityKey(), candidate);
        claimedCandidates.add(candidate);
    }

    private boolean eligibleForNameFallback(
        ProfessorCrawlCandidate candidate,
        ProfessorCrawlData data
    ) {
        if (!data.hasStableIdentity()) {
            return true;
        }
        return candidate.getEmail() == null && candidate.getHomepageUrl() == null;
    }

    private ProfessorCrawlCandidate findUniqueAvailable(
        List<ProfessorCrawlCandidate> candidates,
        Set<ProfessorCrawlCandidate> claimedCandidates,
        String alias
    ) {
        if (candidates == null) {
            return null;
        }
        List<ProfessorCrawlCandidate> available = candidates.stream()
            .filter(candidate -> !claimedCandidates.contains(candidate))
            .toList();
        if (available.size() > 1) {
            throw new ProfessorCrawlException("AMBIGUOUS_PROFESSOR_IDENTITY: " + alias);
        }
        return available.isEmpty() ? null : available.getFirst();
    }

    private String resolveIdentityKey(
        ProfessorCrawlCandidate candidate,
        ProfessorCrawlData data
    ) {
        if (candidate == null || data.hasStableIdentity()) {
            return data.identityKey();
        }
        if (candidate.getSourceIdentityKey().startsWith("legacy:")) {
            return data.identityKey();
        }
        return candidate.getSourceIdentityKey();
    }

    private List<ProfessorCrawlData> deduplicate(List<ProfessorCrawlData> crawledData) {
        if (crawledData == null || crawledData.isEmpty()) {
            throw new ProfessorCrawlException("PROFESSOR_CRAWL_RESULT_EMPTY");
        }

        Map<String, ProfessorCrawlData> dataByIdentity = new LinkedHashMap<>();
        for (ProfessorCrawlData data : crawledData) {
            if (data == null) {
                throw new ProfessorCrawlException("PROFESSOR_CRAWL_DATA_REQUIRED");
            }
            String identityKey = data.identityKey();
            ProfessorCrawlData existing = dataByIdentity.putIfAbsent(identityKey, data);
            if (existing != null && !existing.equals(data)) {
                throw new ProfessorCrawlException(
                    "CONFLICTING_DUPLICATE_PROFESSOR_IDENTITY: " + identityKey
                );
            }
        }
        return List.copyOf(dataByIdentity.values());
    }

    private Map<String, Integer> countByProfessorName(List<ProfessorCrawlData> crawledData) {
        Map<String, Integer> counts = new HashMap<>();
        for (ProfessorCrawlData data : crawledData) {
            counts.merge(data.professorName(), 1, Integer::sum);
        }
        return counts;
    }

    private record CandidateIndexes(
        Map<String, ProfessorCrawlCandidate> byIdentity,
        Map<String, List<ProfessorCrawlCandidate>> byEmail,
        Map<String, List<ProfessorCrawlCandidate>> byHomepage,
        Map<String, List<ProfessorCrawlCandidate>> byName
    ) {
        private static CandidateIndexes from(List<ProfessorCrawlCandidate> candidates) {
            Map<String, ProfessorCrawlCandidate> byIdentity = new HashMap<>();
            for (ProfessorCrawlCandidate candidate : candidates) {
                ProfessorCrawlCandidate duplicate = byIdentity.put(
                    candidate.getSourceIdentityKey(),
                    candidate
                );
                if (duplicate != null) {
                    throw new ProfessorCrawlException(
                        "DUPLICATE_STORED_PROFESSOR_IDENTITY: "
                            + candidate.getSourceIdentityKey()
                    );
                }
            }
            return new CandidateIndexes(
                Map.copyOf(byIdentity),
                indexBy(candidates, ProfessorCrawlCandidate::getEmail),
                indexBy(candidates, ProfessorCrawlCandidate::getHomepageUrl),
                indexBy(candidates, ProfessorCrawlCandidate::getProfessorName)
            );
        }

        private static Map<String, List<ProfessorCrawlCandidate>> indexBy(
            List<ProfessorCrawlCandidate> candidates,
            Function<ProfessorCrawlCandidate, String> keyExtractor
        ) {
            Map<String, List<ProfessorCrawlCandidate>> index = new HashMap<>();
            for (ProfessorCrawlCandidate candidate : candidates) {
                String key = keyExtractor.apply(candidate);
                if (key != null) {
                    index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
                }
            }
            return index;
        }
    }

    private record HomepageName(String homepageUrl, String professorName) {
        private static HomepageName from(ProfessorCrawlData data) {
            return new HomepageName(data.homepageUrl(), data.professorName());
        }
    }
}
