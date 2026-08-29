package com.lessonweb.lesson.model.lesson;

import jakarta.validation.constraints.NotNull;

public record ParagraphBlock(@NotNull String id, @NotNull String text) implements DocumentBlock {

    @Override
    public String type() {
        return "paragraph";
    }
}
