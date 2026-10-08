package com.sebu.backend.crawling.adapter.fetcher;

import com.sebu.backend.crawling.dto.FetchedProfessorPage;
import com.sebu.backend.crawling.exception.ProfessorCrawlException;
import com.sebu.backend.crawling.config.ProfessorCrawlerProperties;
import com.sebu.backend.crawling.port.ProfessorPageFetcher;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class CurlProfessorPageFetcher implements ProfessorPageFetcher {
    private static final int MAX_ERROR_OUTPUT_BYTES = 2000;
    private static final int MAX_CURL_METADATA_BYTES = 4096;
    private static final String EFFECTIVE_URL_WRITE_OUT =
        "\\n__SEBU_EFFECTIVE_URL__:%{url_effective}";
    private static final byte[] EFFECTIVE_URL_MARKER =
        "\n__SEBU_EFFECTIVE_URL__:".getBytes(StandardCharsets.UTF_8);

    private final ProfessorCrawlerProperties properties;

    @Override
    public FetchedProfessorPage fetch(String sourceUrl) {
        URI sourceUri = validateSourceUrl(sourceUrl);
        Process process = startCurl(sourceUri);
        try {
            byte[] output = readBounded(
                process.getInputStream(),
                properties.getMaxBodySizeBytes() + MAX_CURL_METADATA_BYTES
            );
            if (!process.waitFor(waitTimeoutMillis(), TimeUnit.MILLISECONDS)) {
                throw new ProfessorCrawlException("PROFESSOR_PAGE_FETCH_TIMEOUT: " + sourceUrl);
            }
            if (process.exitValue() != 0) {
                throw new ProfessorCrawlException(
                    "PROFESSOR_PAGE_FETCH_FAILED: exitCode=" + process.exitValue()
                        + ", detail=" + errorDetail(output)
                );
            }
            CurlOutput curlOutput = splitCurlOutput(output);
            URI effectiveUri = validateSourceUrl(curlOutput.effectiveUrl());
            try (InputStream bodyStream = new ByteArrayInputStream(curlOutput.body())) {
                Document document = Jsoup.parse(bodyStream, null, effectiveUri.toString());
                return new FetchedProfessorPage(document.outerHtml(), effectiveUri.toString());
            }
        } catch (IOException exception) {
            throw new ProfessorCrawlException("PROFESSOR_PAGE_READ_FAILED: " + sourceUrl, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProfessorCrawlException("PROFESSOR_PAGE_FETCH_INTERRUPTED", exception);
        } finally {
            terminate(process);
        }
    }

    private Process startCurl(URI sourceUri) {
        List<String> command = List.of(
            properties.getCurlExecutable(),
            "--silent",
            "--show-error",
            "--fail",
            "--location",
            "--proto",
            "=https",
            "--proto-redir",
            "=https",
            "--max-time",
            curlTimeoutSeconds(),
            "--max-filesize",
            Integer.toString(properties.getMaxBodySizeBytes()),
            "--write-out",
            EFFECTIVE_URL_WRITE_OUT,
            "--user-agent",
            properties.getUserAgent(),
            "--header",
            "Accept: text/html,application/xhtml+xml",
            "--header",
            "Accept-Language: ko-KR,ko;q=0.9,en;q=0.8",
            sourceUri.toString()
        );
        try {
            return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        } catch (IOException exception) {
            throw new ProfessorCrawlException(
                "CURL_PROCESS_START_FAILED: " + properties.getCurlExecutable(),
                exception
            );
        }
    }

    private URI validateSourceUrl(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            if (uri.getHost() == null || !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ProfessorCrawlException("INVALID_HTTPS_CRAWL_SOURCE_URL: " + sourceUrl, exception);
        }
    }

    private byte[] readBounded(InputStream input, int maxBodySizeBytes) throws IOException {
        try (InputStream responseStream = input) {
            byte[] output = responseStream.readNBytes(maxBodySizeBytes + 1);
            if (output.length > maxBodySizeBytes) {
                throw new ProfessorCrawlException(
                    "PROFESSOR_PAGE_TOO_LARGE: maxBytes=" + maxBodySizeBytes
                );
            }
            return output;
        }
    }

    private CurlOutput splitCurlOutput(byte[] output) {
        int markerIndex = lastIndexOf(output, EFFECTIVE_URL_MARKER);
        if (markerIndex < 0) {
            throw new ProfessorCrawlException("CURL_EFFECTIVE_URL_MISSING");
        }
        byte[] body = Arrays.copyOfRange(output, 0, markerIndex);
        if (body.length > properties.getMaxBodySizeBytes()) {
            throw new ProfessorCrawlException(
                "PROFESSOR_PAGE_TOO_LARGE: maxBytes=" + properties.getMaxBodySizeBytes()
            );
        }
        String effectiveUrl = new String(
            output,
            markerIndex + EFFECTIVE_URL_MARKER.length,
            output.length - markerIndex - EFFECTIVE_URL_MARKER.length,
            StandardCharsets.UTF_8
        ).trim();
        return new CurlOutput(body, effectiveUrl);
    }

    private int lastIndexOf(byte[] source, byte[] target) {
        for (int start = source.length - target.length; start >= 0; start--) {
            boolean matches = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[start + offset] != target[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return start;
            }
        }
        return -1;
    }

    private String curlTimeoutSeconds() {
        Duration timeout = properties.getTimeout();
        return Double.toString(timeout.toMillis() / 1000.0);
    }

    private long waitTimeoutMillis() {
        return properties.getTimeout().plusSeconds(2).toMillis();
    }

    private String errorDetail(byte[] output) {
        String detail = new String(output, StandardCharsets.UTF_8)
            .replaceAll("\\s+", " ")
            .trim();
        if (detail.length() <= MAX_ERROR_OUTPUT_BYTES) {
            return detail;
        }
        return detail.substring(0, MAX_ERROR_OUTPUT_BYTES);
    }

    private void terminate(Process process) {
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private record CurlOutput(byte[] body, String effectiveUrl) {
    }
}
