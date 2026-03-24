package com.safecircle.api.exception;

import com.safecircle.common.dto.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * GLOBAL EXCEPTION HANDLER CONCEPT:
 *
 * @RestControllerAdvice is a Spring AOP mechanism — it intercepts exceptions thrown
 * by ANY @RestController in the application BEFORE they propagate to the servlet
 * container (which would return a default HTML error page).
 *
 * Each @ExceptionHandler method says: "if an exception of type X is thrown anywhere
 * in any controller, call this method to produce the response."
 *
 * WITHOUT this, every controller method would need try/catch blocks and would
 * manually build error responses. Instead, controllers let exceptions propagate
 * and this handler converts them to the standard error format automatically.
 *
 * The response always wraps ApiError in a "error" key:
 * {
 *   "error": { "code": "...", "message": "...", "field": "..." }
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles @Valid validation failures — e.g. batteryLevel is null, or > 100.
     * Spring throws MethodArgumentNotValidException automatically when @Valid fails.
     * We extract the first field error and return a 400 with the field name.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, ApiError>> handleValidationException(
            MethodArgumentNotValidException ex) {

        FieldError firstError = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .orElse(null);

        ApiError error = firstError != null
            ? ApiError.ofField(
                "VALIDATION_ERROR",
                firstError.getDefaultMessage(),
                firstError.getField()
              )
            : ApiError.of("VALIDATION_ERROR", "Request validation failed");

        return ResponseEntity.badRequest().body(Map.of("error", error));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, ApiError>> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, ApiError>> handleUnauthorizedException(
            UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", ApiError.of("UNAUTHORIZED", ex.getMessage())));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, ApiError>> handleForbiddenException(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("error", ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, ApiError>> handleConflictException(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("error", ApiError.of(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, ApiError>> handleRateLimitException(RateLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(Map.of("error", ApiError.of(ex.getCode(), ex.getMessage())));
    }

    /**
     * Catch-all for any unexpected exception.
     * Logs the full stack trace (for CloudWatch) but returns a generic message
     * to the client — never expose internal details in error messages.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, ApiError>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error",
                ApiError.of("INTERNAL_ERROR", "An unexpected error occurred.")));
    }
}