package com.lessonweb.lesson.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "mineru")
@ConstructorBinding
public record MineruProperties(
        String apiKey,
        String baseUrl,
        String modelVersion,
        String language,
        Duration pollInterval,
        Duration timeout
) {
}
