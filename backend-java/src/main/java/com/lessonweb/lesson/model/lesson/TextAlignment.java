package com.lessonweb.lesson.model.lesson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TextAlignment {
    LEFT("left"),
    CENTER("center"),
    RIGHT("right");

    private final String value;

    TextAlignment(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TextAlignment fromValue(String value) {
        for (TextAlignment alignment : values()) {
            if (alignment.value.equals(value)) {
                return alignment;
            }
        }
        throw new IllegalArgumentException("Unknown text alignment: " + value);
    }
}
