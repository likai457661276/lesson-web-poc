package com.lessonweb.lesson.model.lesson;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record HeadingBlock(
        @NotNull String id,
        @Min(1) @Max(6) int level,
        @NotNull String text,
        @NotNull TextAlignment alignment
) implements DocumentBlock {

    @Override
    public String type() {
        return "heading";
    }
}
