package com.sebu.backend.researchfield.extraction.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResearchFieldExtractionProperties.class)
public class ResearchFieldExtractionConfiguration {
}
