package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

public record FormulaBlock(@NotNull String id, @NotNull String latex) implements DocumentBlock {

    @Override
    public String type() {
        return "formula";
    }
}
