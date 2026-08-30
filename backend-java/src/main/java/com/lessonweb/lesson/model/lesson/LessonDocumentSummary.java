package com.lessonweb.lesson.model.lesson;

import java.time.Instant;

public record LessonDocumentSummary(
        String id,
        String title,
        String sourceFileName,
        String sourceType,
        int blockCount,
        Instant createdAt,
        Instant updatedAt
) {
}
