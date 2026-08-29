package com.lessonweb.lesson.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mineru")
public record MineruProperties(
        String apiKey,
        String baseUrl,
        String modelVersion,
        String language,
        Duration pollInterval,
        Duration timeout
) {
}
