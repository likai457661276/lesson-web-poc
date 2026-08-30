package com.lessonweb.lesson.exception;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException exception) {
        var response = new ApiErrorResponse(
                new ApiErrorResponse.ApiError(exception.code(), exception.getMessage()));
        return ResponseEntity.status(exception.status()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ValidationErrorResponse.ValidationDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ValidationErrorResponse.ValidationDetail(
                        "value_error",
                        List.of("body", error.getField()),
                        error.getDefaultMessage(),
                        error.getRejectedValue()))
                .toList();
        return ResponseEntity.unprocessableEntity().body(new ValidationErrorResponse(details));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ValidationErrorResponse> handleMissingPart(MissingServletRequestPartException exception) {
        var detail = new ValidationErrorResponse.ValidationDetail(
                "missing", List.of("body", exception.getRequestPartName()), "Field required", null);
        return ResponseEntity.unprocessableEntity().body(new ValidationErrorResponse(List.of(detail)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        var detail = new ValidationErrorResponse.ValidationDetail(
                "json_invalid", List.of("body"), "JSON body could not be parsed", null);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidationErrorResponse(List.of(detail)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        var response = new ApiErrorResponse(
                new ApiErrorResponse.ApiError("FILE_TOO_LARGE", "文件大小超过限制"));
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
}
