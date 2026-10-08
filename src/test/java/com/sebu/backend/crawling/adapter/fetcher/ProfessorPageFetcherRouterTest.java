package com.sebu.backend.crawling.adapter.fetcher;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ProfessorPageFetcherRouterTest {
    private final CurlProfessorPageFetcher staticFetcher = mock(CurlProfessorPageFetcher.class);
    private final SejongMainProfessorPageFetcher mainFetcher = mock(SejongMainProfessorPageFetcher.class);
    private final ProfessorPageFetcherRouter router = new ProfessorPageFetcherRouter(staticFetcher, mainFetcher);

    @Test
    void usesMainPageSessionForCollegePages() {
        String url = "https://www.sejong.ac.kr/kor/college/example.do";
        router.fetch(url);
        verify(mainFetcher).fetch(url);
        verifyNoInteractions(staticFetcher);
    }

    @Test
    void keepsExistingDepartmentAndAerospaceRoutes() {
        String department = "https://dept.sejong.ac.kr/example/intro/professor.do";
        String aerospace = "https://ae.sejong.ac.kr/shop_contents/myboard_list.htm?myboard_code=professor";
        router.fetch(department);
        router.fetch(aerospace);
        verify(staticFetcher).fetch(department);
        verify(staticFetcher).fetch(aerospace);
        verifyNoInteractions(mainFetcher);
    }

    @Test
    void doesNotUseMainSessionForHostSuffixLookalikes() {
        String url = "https://www.sejong.ac.kr.example.com/kor/college/example.do";
        router.fetch(url);
        verify(staticFetcher).fetch(url);
        verifyNoInteractions(mainFetcher);
    }
}
