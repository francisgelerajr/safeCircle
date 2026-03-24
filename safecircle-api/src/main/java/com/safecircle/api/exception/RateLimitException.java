package com.safecircle.api.exception;

// ——— 429 Too Many Requests ———
public class RateLimitException extends RuntimeException {
    private final String code;

    public RateLimitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}