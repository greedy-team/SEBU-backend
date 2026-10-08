package com.sebu.backend.researchfield.manualsplit.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("research-field-manual-split")
@EnableConfigurationProperties(ManualSplitImportProperties.class)
public class ManualSplitImportConfiguration { }
