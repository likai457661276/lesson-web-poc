package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

public record ParagraphBlock(@NotNull String id, @NotNull String text) implements DocumentBlock {

    @Override
    public String type() {
        return "paragraph";
    }
}
