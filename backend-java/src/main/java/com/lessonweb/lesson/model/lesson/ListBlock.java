package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

import java.util.List;

public record ListBlock(
        @NotNull String id,
        @NotNull List<@NotNull String> items,
        boolean ordered
) implements DocumentBlock {

    public ListBlock {
        items = List.copyOf(items);
    }

    @Override
    public String type() {
        return "list";
    }
}
