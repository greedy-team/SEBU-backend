package com.sebu.backend.crawling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProfessorCrawlerProperties.class)
public class ProfessorCrawlerConfiguration {
    @Bean
    Clock professorCrawlerClock() {
        return Clock.systemDefaultZone();
    }
}
