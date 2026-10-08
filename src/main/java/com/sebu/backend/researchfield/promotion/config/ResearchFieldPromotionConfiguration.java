package com.sebu.backend.researchfield.promotion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("research-field-promotion")
@EnableConfigurationProperties(ResearchFieldPromotionProperties.class)
public class ResearchFieldPromotionConfiguration {
}
