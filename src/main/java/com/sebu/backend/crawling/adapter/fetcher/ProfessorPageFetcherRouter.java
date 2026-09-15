package com.sebu.backend.crawling.adapter.fetcher;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.net.URI;

@Primary
@Component
@RequiredArgsConstructor
public class ProfessorPageFetcherRouter implements ProfessorPageFetcher {
    private final CurlProfessorPageFetcher staticFetcher;
    private final SejongMainProfessorPageFetcher mainFetcher;

    @Override
    public FetchedProfessorPage fetch(String sourceUrl) {
        URI uri = URI.create(sourceUrl);
        if ("https".equalsIgnoreCase(uri.getScheme())
            && "www.sejong.ac.kr".equalsIgnoreCase(uri.getHost())
            && uri.getPath().startsWith("/kor/college/")) {
            return mainFetcher.fetch(sourceUrl);
        }
        return staticFetcher.fetch(sourceUrl);
    }
}
