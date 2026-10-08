package com.sebu.backend.crawling.port;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.domain.CrawlParserType;
import com.sebu.backend.crawling.domain.ProfessorCrawlData;

import java.util.List;

public interface ProfessorPageParser {
    CrawlParserType supports();

    List<ProfessorCrawlData> parse(FetchedProfessorPage page);
}
