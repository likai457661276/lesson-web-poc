package com.lessonweb.lesson.exception;

public record ApiErrorResponse(ApiError error) {

    public record ApiError(String code, String message) {
    }
}
