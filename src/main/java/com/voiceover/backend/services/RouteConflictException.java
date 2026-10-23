package com.voiceover.backend.services;

/** Business conflict; safe to display to the administrator. */
public class RouteConflictException extends RuntimeException {
    public RouteConflictException(String message) { super(message); }
}
