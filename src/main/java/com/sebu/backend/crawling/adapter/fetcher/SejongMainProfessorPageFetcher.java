package com.sebu.backend.crawling.adapter.fetcher;

import com.sebu.backend.crawling.config.ProfessorCrawlerProperties;
import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
public class SejongMainProfessorPageFetcher {
    private final ProfessorCrawlerProperties properties;
    private final SejongMainProfessorPageAdapter adapter;

    public FetchedProfessorPage fetch(String sourceUrl) {
        // The public list API requires the anonymous session issued by its college page.
        // Keep that cookie in this request's memory, never in files or shared application state.
        try (HttpClient client = HttpClient.newBuilder()
            .connectTimeout(properties.getTimeout())
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()) {
            byte[] html = get(client, sourceUrl, null);
            var page = Jsoup.parse(new ByteArrayInputStream(html), null, sourceUrl);
            if (page.select("#proShow > li .b-professor-name").isEmpty()) {
                Thread.sleep(properties.getRequestDelay().toMillis());
                String json = new String(get(client, adapter.apiUrl(page), sourceUrl), StandardCharsets.UTF_8);
                page = adapter.populate(page, json);
            }
            return new FetchedProfessorPage(page.outerHtml(), sourceUrl);
        } catch (IOException exception) {
            throw new ProfessorCrawlException("SEJONG_MAIN_PAGE_READ_FAILED", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProfessorCrawlException("SEJONG_MAIN_FETCH_INTERRUPTED", exception);
        }
    }

    private byte[] get(HttpClient client, String url, String referer) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"www.sejong.ac.kr".equals(uri.getHost())
            || uri.getUserInfo() != null || uri.getPort() != -1) {
            throw new ProfessorCrawlException("SEJONG_MAIN_INVALID_ORIGIN");
        }
        var request = HttpRequest.newBuilder(uri).timeout(properties.getTimeout())
            .header("User-Agent", properties.getUserAgent())
            .header("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");
        if (referer != null) {
            request.header("Referer", referer).header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json");
        }
        var response = client.send(request.GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new ProfessorCrawlException("SEJONG_MAIN_HTTP_STATUS: " + response.statusCode());
            }
            byte[] bytes = readBounded(input);
            if (bytes.length > properties.getMaxBodySizeBytes()) {
                throw new ProfessorCrawlException("SEJONG_MAIN_BODY_TOO_LARGE");
            }
            return bytes;
        }
    }

    private byte[] readBounded(InputStream input) throws IOException, InterruptedException {
        // HttpRequest.timeout covers headers, so bound the body read separately as well.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var read = executor.submit(() -> input.readNBytes(properties.getMaxBodySizeBytes() + 1));
            try {
                return read.get(properties.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                input.close();
                read.cancel(true);
                throw new ProfessorCrawlException("SEJONG_MAIN_BODY_TIMEOUT", exception);
            } catch (ExecutionException exception) {
                throw new IOException("SEJONG_MAIN_BODY_READ_FAILED", exception.getCause());
            } catch (InterruptedException exception) {
                input.close();
                read.cancel(true);
                throw exception;
            }
        }
    }
}
