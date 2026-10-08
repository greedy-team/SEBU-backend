package com.sebu.backend.promotion.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("crawler & promotion")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OneTimeJobProfileGuard implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        throw new IllegalStateException("CRAWLER_AND_PROMOTION_PROFILES_ARE_MUTUALLY_EXCLUSIVE");
    }
}
