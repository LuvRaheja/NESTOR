package com.nestor.api.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Map<Integer, String> USER_FRIENDLY_MESSAGES = Map.of(
        401, "Your session has expired. Please sign in again.",
        403, "You don't have permission to access this resource.",
        404, "The requested resource was not found.",
        429, "Too many requests. Please slow down and try again later.",
        500, "An internal error occurred. Please try again later.",
        503, "The service is temporarily unavailable. Please try again later."
    );

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        int code = ex.getStatusCode().value();
        String message = USER_FRIENDLY_MESSAGES.getOrDefault(code, ex.getReason());
        return ResponseEntity.status(code).body(Map.of("detail", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("detail", "An unexpected error occurred. Our team has been notified."));
    }
}
