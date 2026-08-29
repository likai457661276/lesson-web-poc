package com.lessonweb.lesson.model.lesson;

import jakarta.validation.constraints.NotNull;

public record TableBlock(@NotNull String id, @NotNull String html) implements DocumentBlock {

    @Override
    public String type() {
        return "table";
    }
}
