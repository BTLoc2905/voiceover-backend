package com.voiceover.backend.controllers;

import com.voiceover.backend.services.RouteConflictException;
import com.voiceover.backend.services.ContentReviewConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class RouteErrorHandler {
    @ExceptionHandler(ContentReviewConflictException.class)
    public ResponseEntity<?> reviewConflict(ContentReviewConflictException error) {
        return ResponseEntity.status(409).body(Map.of("code", "CONTENT_CONFLICT", "message", error.getMessage()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT", "message", error.getMessage()));
    }

    @ExceptionHandler(RouteConflictException.class)
    public ResponseEntity<?> conflict(RouteConflictException error) {
        return ResponseEntity.status(409).body(Map.of("code", "ROUTE_CONFLICT", "message", error.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> constraint(DataIntegrityViolationException error) {
        return ResponseEntity.status(409).body(Map.of("code", "DATA_CONFLICT", "message",
                "Không thể lưu hoặc xóa vì dữ liệu còn được tham chiếu. Hãy tải lại và kiểm tra các Route liên quan."));
    }
}
