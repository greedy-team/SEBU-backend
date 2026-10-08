package com.sebu.backend.global.monitoring;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("monitoring")
public class MonitoringMetricsConfiguration {
    private static final String HTTP_REQUESTS = "http.server.requests";
    private static final Set<String> LATENCY_PATHS = Set.of("/api/v1/laboratories", "/api/v1/posts");

    @Bean
    ObservationPredicate monitoringApiObservations() {
        // Decide before handler mapping, so rejected scrape requests do not become UNKNOWN API traffic.
        return (name, context) -> !HTTP_REQUESTS.equals(name)
            || !(context instanceof ServerRequestObservationContext requestContext)
            || requestContext.getCarrier().getRequestURI()
                .startsWith(requestContext.getCarrier().getContextPath() + "/api/");
    }

    @Bean
    MeterFilter monitoringHttpMetrics() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (!HTTP_REQUESTS.equals(id.getName())) {
                    return id;
                }
                // Method and status already describe the outcome. Exception class names add no panel value.
                return id.replaceTags(id.getTags().stream()
                    .filter(tag -> !Set.of("exception", "error", "outcome").contains(tag.getKey()))
                    .toList());
            }

            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (!HTTP_REQUESTS.equals(id.getName())) {
                    return config;
                }
                if ("GET".equals(id.getTag("method"))
                    && LATENCY_PATHS.contains(id.getTag("uri"))) {
                    // A small fixed set of buckets supports an approximate p95 without per-request percentiles.
                    return DistributionStatisticConfig.builder()
                        .serviceLevelObjectives(
                            nanos(50), nanos(100), nanos(250), nanos(500),
                            nanos(1000), nanos(2000), nanos(5000))
                        .build().merge(config);
                }
                // Prometheus cannot mix summary and histogram types under the same metric name.
                // Keep other routes at a single boundary instead of enabling a full histogram for all APIs.
                return DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(nanos(5000))
                    .build().merge(config);
            }
        };
    }

    @Bean
    MeterFilter monitoringRequestExclusions() {
        return MeterFilter.deny(id -> HTTP_REQUESTS.equals(id.getName())
            && id.getTag("uri") != null && id.getTag("uri").startsWith("/")
            && !id.getTag("uri").startsWith("/api/"));
    }

    @Bean
    MeterFilter monitoringUriLimit() {
        return MeterFilter.maximumAllowableTags(HTTP_REQUESTS, "uri", 100, MeterFilter.deny());
    }

    private static double nanos(long millis) {
        return Duration.ofMillis(millis).toNanos();
    }
}
