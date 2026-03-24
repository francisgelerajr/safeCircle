package com.safecircle.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * The standard error response shape returned by GlobalExceptionHandler.
 *
 * Every error from the API looks like:
 * {
 *   "error": {
 *     "code": "HEARTBEAT_USER_NOT_FOUND",
 *     "message": "No user found for the provided token.",
 *     "field": null
 *   }
 * }
 *
 * WHY a machine-readable "code"?
 * The mobile app can switch on the code string to show the right UI message
 * in the right language. If we only returned an HTTP status and a human message,
 * the app couldn't distinguish two different 404 errors from each other.
 *
 * @JsonInclude(NON_NULL) means "field" is omitted from the JSON when it's null
 * (i.e. for non-validation errors where there's no specific field to blame).
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private String code;
    private String message;
    private String field; // only present for validation errors

    public static ApiError of(String code, String message) {
        return ApiError.builder().code(code).message(message).build();
    }

    public static ApiError ofField(String code, String message, String field) {
        return ApiError.builder().code(code).message(message).field(field).build();
    }
}