package com.pms.common;

import com.pms.contact.RateLimitExceededException;
import com.pms.security.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Without this, a @Valid failure (e.g. a @Pattern-annotated field like
    // mobileNumber) was never actually handled anywhere in this app -
    // Spring's default handling forwards it to /error, which re-enters the
    // security filter chain; JwtAuthenticationFilter (a OncePerRequestFilter)
    // correctly skips re-running on that forward, but AuthorizationFilter
    // does not, so ModuleAuthorizationManager evaluates the forwarded
    // request against an anonymous authentication and the client sees a
    // misleading 401 "Authentication required" instead of the actual
    // validation error. Never noticed before because every existing
    // @Valid-validated field already has matching frontend validation that
    // blocks the request before it reaches the API - this endpoint's new
    // mobile-number check was the first path to actually exercise it.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed.");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    // Thrown by ContactRateLimiter (see com.pms.contact) for a spammy IP -
    // a distinct status from 400 so a well-behaved client can tell "your
    // input was wrong" apart from "slow down and retry later".
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiError> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    // Fires when two concurrent requests modify the same @Version'd row (e.g.
    // DrugBatch stock) - see migration doc risk R10. The client should retry
    // rather than assume the write silently succeeded or failed.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleConflict(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "This record was modified concurrently - please retry.", request);
    }

    // Fires on a same-row double-submit race (e.g. two concurrent Doctor
    // Queue check-ins for the same appointment) caught by a DB unique
    // constraint rather than an application-level guard.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "This record conflicts with an existing one - please refresh and retry.", request);
    }

    // A missing file under /uploads/** (e.g. a stale/old-format link) throws
    // this rather than going through Spring's older /error-forward behavior -
    // confirmed live against this app's actual Spring Boot 4.1/Spring 7
    // version. A real 404, safe to expose as-is (it reveals nothing beyond
    // "this path doesn't exist", the same information the request itself
    // already carried).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingStaticResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not found.", request);
    }

    // The catch-all - every @ExceptionHandler above targets a specific,
    // anticipated failure mode; anything else (IllegalStateException,
    // NullPointerException, ClassCastException, DateTimeParseException,
    // FlywayException, ...) used to fall through to Spring's default
    // handling, which forwards to /error and produces the exact same
    // misleading 401 the MethodArgumentNotValidException handler above was
    // added to prevent - just for every OTHER exception type instead of
    // just that one. Logged server-side at ERROR (the client only ever
    // sees a generic message - this exception's real content might be
    // exactly the kind of internal detail that shouldn't leak in a response
    // body) so it's still diagnosable from the logs, unlike the misleading
    // 401 it replaces.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again or contact support.", request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
