package com.sebu.backend.promotion.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PromotionProperties.class)
public class PromotionConfiguration {
}
