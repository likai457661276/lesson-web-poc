package com.lessonweb.lesson.exception;

import java.util.List;

public record ValidationErrorResponse(List<ValidationDetail> detail) {

    public record ValidationDetail(String type, List<String> loc, String msg, Object input) {
    }
}
