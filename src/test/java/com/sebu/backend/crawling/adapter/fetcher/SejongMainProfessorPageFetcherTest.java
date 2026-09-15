package com.sebu.backend.crawling.adapter.fetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sebu.backend.crawling.config.ProfessorCrawlerProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SejongMainProfessorPageFetcherTest {
    private static final String SOURCE = "https://www.sejong.ac.kr/kor/college/example.do";
    private static final String PAGE = """
        <script>var CMS={"menuCd":"3204"};</script><input id="paramsDeptNo" value="2135">
        <input id="orderOption" value="D"><input id="selectOption" value=""><ul id="proShow"></ul>
        """;

    @Test
    void usesOneAnonymousSessionAndAjaxHeadersToLoadTheDynamicList() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var pageResponse = response(200, PAGE);
        var apiResponse = response(200, "{\"items\":[{\"korNm\":\"홍길동\",\"resFld\":\"3D 영상\"}]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(pageResponse, apiResponse);
        HttpClient.Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            assertThat(fetcher().fetch(SOURCE).html()).contains("홍길동", "3D 영상");
        }
        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        var api = requests.getAllValues().getLast();
        assertThat(api.uri().getHost()).isEqualTo("www.sejong.ac.kr");
        assertThat(api.uri().getPath()).isEqualTo("/professor/getProfessorListKor.do");
        assertThat(api.headers().firstValue("Referer")).contains(SOURCE);
        assertThat(api.headers().firstValue("X-Requested-With")).contains("XMLHttpRequest");
        verify(builder).followRedirects(HttpClient.Redirect.NEVER);
        verify(builder).cookieHandler(any(java.net.CookieManager.class));
        verify(client).close();
    }

    @Test
    void doesNotRefetchAnAlreadyRenderedList() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var pageResponse = response(200, "<ul id='proShow'><li><div class='b-professor-name'>홍길동</div></li></ul>");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(pageResponse);
        runWithClient(client, () -> assertThat(fetcher().fetch(SOURCE).html()).contains("홍길동"));
        verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsHttpErrorsAndDoesNotTryToParseThemAsEmptyFacultyLists() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var errorResponse = response(403, "Forbidden");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(errorResponse);
        runWithClient(client, () -> assertThatThrownBy(() -> fetcher().fetch(SOURCE)).hasMessage("SEJONG_MAIN_HTTP_STATUS: 403"));
        verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void rejectsRedirectsFromThePublicApi() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var pageResponse = response(200, PAGE);
        var redirectResponse = response(302, "Redirect");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(pageResponse, redirectResponse);
        runWithClient(client, () -> assertThatThrownBy(() -> fetcher().fetch(SOURCE)).hasMessage("SEJONG_MAIN_HTTP_STATUS: 302"));
    }

    @Test
    void rejectsOversizedBodies() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var hugeResponse = response(200, "x".repeat(1025));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(hugeResponse);
        runWithClient(client, () -> assertThatThrownBy(() -> fetcher().fetch(SOURCE)).hasMessage("SEJONG_MAIN_BODY_TOO_LARGE"));
    }

    @Test
    void refusesOtherOriginsBeforeMakingRequests() throws Exception {
        HttpClient client = mock(HttpClient.class);
        runWithClient(client, () -> assertThatThrownBy(() -> fetcher().fetch("https://www.sejong.ac.kr.attacker.example/kor/college/test.do"))
            .hasMessage("SEJONG_MAIN_INVALID_ORIGIN"));
        verify(client, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private SejongMainProfessorPageFetcher fetcher() {
        var properties = new ProfessorCrawlerProperties();
        properties.setRequestDelay(Duration.ZERO);
        properties.setMaxBodySizeBytes(1024);
        return new SejongMainProfessorPageFetcher(properties, new SejongMainProfessorPageAdapter(new ObjectMapper()));
    }

    private void runWithClient(HttpClient client, Runnable assertion) {
        HttpClient.Builder builder = mock(HttpClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (var factory = mockStatic(HttpClient.class)) {
            factory.when(HttpClient::newBuilder).thenReturn(builder);
            assertion.run();
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<InputStream> response(int status, String body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        return response;
    }
}
