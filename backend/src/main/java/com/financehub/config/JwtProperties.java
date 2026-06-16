package com.financehub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "financehub.security.jwt")
public record JwtProperties(
        String secret,
        Duration expiration
) {
}
