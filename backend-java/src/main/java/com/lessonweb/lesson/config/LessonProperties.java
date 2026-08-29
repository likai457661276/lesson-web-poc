package com.lessonweb.lesson.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "lesson")
public record LessonProperties(
        String frontendOrigin,
        Path dataDir,
        DataSize maxFileSize,
        String allowedExtensions
) {
    public Set<String> allowedExtensionSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
