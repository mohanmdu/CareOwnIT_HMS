package com.pms.common;

import com.pms.security.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(error);
    }
}
