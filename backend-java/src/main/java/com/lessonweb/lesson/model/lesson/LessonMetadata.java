package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

public record LessonMetadata(@NotNull String sourceType, @NotNull String sourceFileName) {
}
