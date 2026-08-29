package com.lessonweb.lesson.model.lesson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record LessonDocument(
        @Pattern(regexp = "1\\.0") String version,
        @NotNull String documentId,
        @NotNull String title,
        @Valid @NotNull LessonMetadata metadata,
        @Valid @NotNull List<DocumentBlock> blocks
) {
    public LessonDocument {
        version = version == null ? "1.0" : version;
        blocks = List.copyOf(blocks);
    }
}
