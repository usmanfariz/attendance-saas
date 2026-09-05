package com.attendance.saas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        boolean allowCredentials,
        long maxAge
) {

    public List<String> allowedOriginsOrDefault() {
        return allowedOrigins == null || allowedOrigins.isEmpty() ? List.of("*") : allowedOrigins;
    }

    public List<String> allowedMethodsOrDefault() {
        return allowedMethods == null || allowedMethods.isEmpty()
                ? List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                : allowedMethods;
    }

    public List<String> allowedHeadersOrDefault() {
        return allowedHeaders == null || allowedHeaders.isEmpty() ? List.of("*") : allowedHeaders;
    }
}
