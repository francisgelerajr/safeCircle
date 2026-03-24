package com.safecircle.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/**
 * DTO CONCEPT:
 * DTO = Data Transfer Object. This is what the mobile app sends in the request body,
 * and what we send back in the response. It is NOT the JPA entity.
 *
 * WHY separate DTOs from entities?
 * The User entity has fields like cognitoSub, deviceToken, createdAt — internal
 * implementation details. We never want to expose these in an API response.
 * DTOs let us control exactly what goes in and out of the API surface.
 *
 * VALIDATION CONCEPT:
 * @NotNull, @Min, @Max are Bean Validation annotations. When Spring Boot sees
 * @Valid on a controller method parameter, it runs these checks before your code
 * even runs. If validation fails, Spring automatically returns a 400 Bad Request
 * with details about which field failed — you write zero validation code yourself.
 */
public class HeartbeatDto {

    @Data
    public static class Request {

        @NotNull(message = "batteryLevel is required")
        @Min(value = 0, message = "batteryLevel must be between 0 and 100")
        @Max(value = 100, message = "batteryLevel must be between 0 and 100")
        private Integer batteryLevel;

        @NotNull(message = "isCharging is required")
        private Boolean isCharging;

        @NotNull(message = "networkStatus is required")
        private NetworkStatus networkStatus;

        public enum NetworkStatus {
            WIFI, CELLULAR, OFFLINE
        }
    }

    @Data
    @lombok.Builder
    public static class Response {
        private boolean received;
        /**
         * Tells the mobile app when it should send its next ping.
         * The app uses this to schedule a background task rather than
         * hardcoding a fixed interval — the server controls the cadence.
         */
        private Instant nextPingDue;
    }
}