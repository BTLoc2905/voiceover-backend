package com.voiceover.backend.services;

public class ContentReviewConflictException extends RuntimeException {
    public ContentReviewConflictException(String message) { super(message); }
}
