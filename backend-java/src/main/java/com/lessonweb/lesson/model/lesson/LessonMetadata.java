package com.lessonweb.lesson.model.lesson;

import jakarta.validation.constraints.NotNull;

public record LessonMetadata(@NotNull String sourceType, @NotNull String sourceFileName) {
}
