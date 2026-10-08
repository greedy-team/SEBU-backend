package com.sebu.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.auth.transport")
public record AuthTransportProperties(@DefaultValue("false") boolean requireHttps) {
}
