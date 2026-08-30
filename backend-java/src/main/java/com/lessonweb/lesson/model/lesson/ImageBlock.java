package com.lessonweb.lesson.model.lesson;

import javax.validation.constraints.NotNull;

public record ImageBlock(@NotNull String id, @NotNull String src, String alt) implements DocumentBlock {

    @Override
    public String type() {
        return "image";
    }
}
