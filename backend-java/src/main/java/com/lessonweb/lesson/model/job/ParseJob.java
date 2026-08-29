package com.lessonweb.lesson.model.job;

import com.lessonweb.lesson.model.lesson.LessonDocument;

import java.time.Instant;

public record ParseJob(
        String jobId,
        JobStatus status,
        String sourceFileName,
        Instant createdAt,
        LessonDocument document,
        ErrorDetail error
) {
}
