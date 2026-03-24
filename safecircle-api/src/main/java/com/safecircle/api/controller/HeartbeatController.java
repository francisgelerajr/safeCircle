package com.safecircle.api.controller;

import com.safecircle.api.security.CurrentUserResolver;
import com.safecircle.api.service.HeartbeatService;
import com.safecircle.common.dto.HeartbeatDto;
import com.safecircle.common.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CONTROLLER LAYER CONCEPT:
 *
 * The controller's only job is to:
 *   1. Receive the HTTP request
 *   2. Validate the input (via @Valid)
 *   3. Identify who is calling (via CurrentUserResolver)
 *   4. Call the service with clean Java objects
 *   5. Return the response
 *
 * No business logic lives here. No database access. No Redis.
 * If you read a controller and it has if/else business rules, those
 * rules belong in the service layer instead.
 *
 * @RestController = @Controller + @ResponseBody
 * Every method return value is automatically serialized to JSON.
 *
 * @RequestMapping sets the base URL prefix for all methods in this class.
 *
 * @RequiredArgsConstructor (Lombok) generates a constructor that takes all
 * final fields as parameters — this is how Spring injects the dependencies.
 * It's cleaner than @Autowired on fields (which hides dependencies and makes
 * testing harder).
 */
@RestController
@RequestMapping("/api/v1/heartbeat")
@RequiredArgsConstructor
@Slf4j
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * POST /api/v1/heartbeat/ping
     *
     * Called automatically by the mobile app on a schedule.
     * Proves the user's device is reachable and records battery/network state.
     *
     * @Valid triggers Bean Validation on HeartbeatDto.Request before this method runs.
     * If any validation fails (@NotNull, @Min, @Max), Spring returns 400 automatically
     * via GlobalExceptionHandler — this method is never called.
     */
    @PostMapping("/ping")
    public ResponseEntity<HeartbeatDto.Response> ping(
            @Valid @RequestBody HeartbeatDto.Request request) {

        User currentUser = currentUserResolver.getCurrentUser();
        HeartbeatDto.Response response = heartbeatService.processPing(currentUser, request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/heartbeat/check-in
     *
     * Called when the user taps "I'm OK" in the app.
     * Resets the timer AND explicitly resolves any active alert.
     * Empty body — the user identity comes entirely from the JWT.
     */
    @PostMapping("/check-in")
    public ResponseEntity<HeartbeatDto.Response> checkIn() {
        User currentUser = currentUserResolver.getCurrentUser();
        HeartbeatDto.Response response = heartbeatService.processCheckIn(currentUser);
        return ResponseEntity.ok(response);
    }
}