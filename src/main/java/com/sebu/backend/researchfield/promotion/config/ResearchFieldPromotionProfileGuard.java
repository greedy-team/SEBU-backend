package com.sebu.backend.researchfield.promotion.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({
    "research-field-promotion & crawler",
    "research-field-promotion & promotion",
    "research-field-promotion & research-field-extraction",
    "research-field-promotion & research-field-manual-split"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResearchFieldPromotionProfileGuard implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        throw new IllegalStateException(
            "RESEARCH_FIELD_PROMOTION_PROFILE_CONFLICT"
        );
    }
}
