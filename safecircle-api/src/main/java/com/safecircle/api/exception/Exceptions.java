package com.safecircle.api.exception;

/**
 * CUSTOM EXCEPTION CONCEPT:
 *
 * Rather than throwing generic RuntimeException("user not found") everywhere,
 * we define specific exception classes that carry semantic meaning.
 *
 * The GlobalExceptionHandler then maps each exception type to the correct
 * HTTP status code and error response shape — in one place, not scattered
 * across every service method.
 *
 * This means service methods stay clean:
 *   throw new NotFoundException("USER_NOT_FOUND", "No user with that ID");
 *
 * Instead of:
 *   ResponseEntity.status(404).body(Map.of("error", "not found"))
 * scattered everywhere.
 */

// ——— 404 Not Found ———
class NotFoundException extends RuntimeException {
    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}

// ——— 401 Unauthorized ———
class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

// ——— 403 Forbidden ———
class ForbiddenException extends RuntimeException {
    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}

// ——— 409 Conflict ———
class ConflictException extends RuntimeException {
    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}

// ——— 429 Too Many Requests ———
class RateLimitException extends RuntimeException {
    private final String code;

    public RateLimitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}