package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
