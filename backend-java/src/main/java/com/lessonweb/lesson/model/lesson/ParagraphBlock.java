package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

/** reviewNote describes uncertain structure or ordering without changing the source text. */
public record ParagraphBlock(@NotNull String id, @NotNull String text, String reviewNote) implements DocumentBlock {

    @Override
    public String type() {
        return "paragraph";
    }
}
