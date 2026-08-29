package com.lessonweb.lesson.model.job;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JobStatus {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String value;

    JobStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
