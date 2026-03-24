package com.safecircle.api.exception;

// ——— 401 Unauthorized ———
publicclass UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}