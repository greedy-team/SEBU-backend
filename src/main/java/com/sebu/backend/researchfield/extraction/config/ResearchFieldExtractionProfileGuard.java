package com.sebu.backend.researchfield.extraction.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({
    "research-field-extraction & crawler",
    "research-field-extraction & promotion"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResearchFieldExtractionProfileGuard implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        throw new IllegalStateException(
            "RESEARCH_FIELD_EXTRACTION_PROFILE_CONFLICT"
        );
    }
}
