package com.sebu.backend.laboratory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(LaboratoryRetentionProperties.class)
public class LaboratoryRetentionConfiguration {
}
