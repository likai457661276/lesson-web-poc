package com.lessonweb.lesson.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "lesson")
public record LessonProperties(
        String frontendOrigins,
        String dataDir,
        DataSize maxFileSize
) {
}
