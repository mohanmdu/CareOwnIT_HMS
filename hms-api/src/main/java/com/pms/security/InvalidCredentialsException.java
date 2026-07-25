package com.pms.security;

/** Thrown by LoginService for a bad username/password, or by ModuleAuthorizationManager's callers for a missing/invalid token - maps to 401, see GlobalExceptionHandler. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
