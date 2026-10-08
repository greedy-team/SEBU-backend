package com.sebu.backend.crawling.port;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;

public interface ProfessorPageFetcher {
    FetchedProfessorPage fetch(String sourceUrl);
}
