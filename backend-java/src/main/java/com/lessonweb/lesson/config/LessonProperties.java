package com.lessonweb.lesson.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "lesson")
@ConstructorBinding
public record LessonProperties(
        String frontendOrigins,
        String dataDir,
        DataSize maxFileSize
) {
}
