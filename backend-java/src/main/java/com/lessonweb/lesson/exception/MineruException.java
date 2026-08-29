package com.lessonweb.lesson.exception;

import org.springframework.http.HttpStatus;

public class MineruException extends AppException {

    public MineruException(String message) {
        super("MINERU_PARSE_FAILED", message, HttpStatus.BAD_GATEWAY);
    }

    public MineruException(String message, Throwable cause) {
        this(message);
        initCause(cause);
    }
}
