package com.safecircle.api.service;

import com.safecircle.api.exception.NotFoundException;
import com.safecircle.api.repository.AlertEventRepository;
import com.safecircle.api.repository.MonitoringSettingsRepository;
import com.safecircle.common.dto.HeartbeatDto;
import com.safecircle.common.enums.AlertState;
import com.safecircle.common.model.AlertEvent;
import com.safecircle.common.model.MonitoringSettings;
import com.safecircle.common.model.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * SERVICE LAYER CONCEPT:
 *
 * The service layer is where business logic lives. It has no knowledge of HTTP
 * (no HttpServletRequest, no ResponseEntity) — that belongs in the controller.
 * It also doesn't write SQL directly — that belongs in repositories.
 *
 * The service orchestrates: "given this input, what needs to happen?"
 * For a heartbeat ping:
 *   1. Write last_seen to Redis
 *   2. Save battery/network metadata
 *   3. If there's an active alert AND the user is now active → auto-resolve it
 *   4. Return when the next ping is due
 *
 * @Transactional CONCEPT:
 * When a method is annotated @Transactional, Spring wraps it in a database transaction.
 * If anything throws an exception, ALL database changes in that method are rolled back.
 * Example: if saving the AlertEvent succeeds but something else fails,
 * the AlertEvent save is rolled back too — no half-saved state in the database.
 *
 * readOnly = true on read-only methods is an optimization hint to Hibernate
 * — it skips dirty checking (detecting changed entities) which saves CPU.
 *
 * MICROMETER / METRICS CONCEPT:
 * We inject a MeterRegistry to record custom metrics. Every time a ping is received,
 * we increment a counter. Micrometer publishes this to CloudWatch automatically.
 * You can then build a CloudWatch dashboard showing "pings per minute over time"
 * — useful for detecting if users have stopped using the app.
 */
@Service
@Slf4j
public class HeartbeatService {

    // Redis key pattern: "heartbeat:{userId}"
    // e.g. "heartbeat:550e8400-e29b-41d4-a716-446655440000"
    private static final String HEARTBEAT_KEY_PREFIX = "heartbeat:";

    // Redis key pattern: "heartbeat:battery:{userId}"
    private static final String BATTERY_KEY_PREFIX = "heartbeat:battery:";

    private final StringRedisTemplate redisTemplate;
    private final MonitoringSettingsRepository settingsRepository;
    private final AlertEventRepository alertEventRepository;
    private final Counter pingCounter;
    private final Counter checkInCounter;

    @Value("${safecircle.heartbeat.ping-interval-minutes}")
    private int pingIntervalMinutes;

    @Value("${safecircle.heartbeat.redis-ttl-hours}")
    private int redisTtlHours;

    public HeartbeatService(
            StringRedisTemplate redisTemplate,
            MonitoringSettingsRepository settingsRepository,
            AlertEventRepository alertEventRepository,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.settingsRepository = settingsRepository;
        this.alertEventRepository = alertEventRepository;

        // Register custom metrics — these appear in CloudWatch as
        // "safecircle.heartbeat.pings.total" and "safecircle.heartbeat.checkins.total"
        this.pingCounter = Counter.builder("safecircle.heartbeat.pings")
            .description("Total heartbeat pings received")
            .register(meterRegistry);
        this.checkInCounter = Counter.builder("safecircle.heartbeat.checkins")
            .description("Total manual check-ins received")
            .register(meterRegistry);
    }

    /**
     * Processes a heartbeat ping from the mobile app.
     *
     * Called every pingIntervalMinutes by the mobile app's background task.
     * This is the "I'm still alive" signal.
     */
    @Transactional
    public HeartbeatDto.Response processPing(User user, HeartbeatDto.Request request) {
        String userId = user.getId().toString();

        // 1. Write last_seen timestamp to Redis
        // We store the Unix epoch milliseconds as a string.
        // Redis strings are the simplest and fastest Redis data structure.
        String nowEpochMs = String.valueOf(Instant.now().toEpochMilli());
        String heartbeatKey = HEARTBEAT_KEY_PREFIX + userId;

        redisTemplate.opsForValue().set(
            heartbeatKey,
            nowEpochMs,
            Duration.ofHours(redisTtlHours)
            // TTL (Time To Live): Redis automatically deletes this key after redisTtlHours.
            // We set it longer than the maximum inactivity threshold (72h) so keys
            // never expire before the inactivity checker has a chance to evaluate them.
        );

        // 2. Store battery level separately — used to enrich alert messages
        redisTemplate.opsForValue().set(
            BATTERY_KEY_PREFIX + userId,
            request.getBatteryLevel().toString(),
            Duration.ofHours(redisTtlHours)
        );

        log.debug("Heartbeat ping received for user {} — battery {}%, network {}",
            userId, request.getBatteryLevel(), request.getNetworkStatus());

        // 3. If an active alert exists and user is pinging, auto-resolve it.
        // This handles the case where the user's phone came back online
        // mid-escalation without them manually tapping "I'm OK".
        resolveActiveAlertIfPresent(user, "Auto-resolved: device came back online");

        // 4. Increment custom metric for CloudWatch
        pingCounter.increment();

        // 5. Tell the app when to send its next ping
        Instant nextPingDue = Instant.now().plus(Duration.ofMinutes(pingIntervalMinutes));
        return HeartbeatDto.Response.builder()
            .received(true)
            .nextPingDue(nextPingDue)
            .build();
    }

    /**
     * Processes a manual "I'm OK" check-in from the user.
     * Resets the inactivity timer AND explicitly resolves any active alert.
     */
    @Transactional
    public HeartbeatDto.Response processCheckIn(User user) {
        String userId = user.getId().toString();

        // Reset the heartbeat timer — same as a ping
        redisTemplate.opsForValue().set(
            HEARTBEAT_KEY_PREFIX + userId,
            String.valueOf(Instant.now().toEpochMilli()),
            Duration.ofHours(redisTtlHours)
        );

        // Explicitly resolve any active alert with a human-readable reason
        resolveActiveAlertIfPresent(user, "Resolved: user checked in manually");

        log.info("Manual check-in received for user {}", userId);
        checkInCounter.increment();

        return HeartbeatDto.Response.builder()
            .received(true)
            .nextPingDue(Instant.now().plus(Duration.ofMinutes(pingIntervalMinutes)))
            .build();
    }

    /**
     * Returns the last seen timestamp for a user from Redis.
     * Returns empty if no heartbeat has ever been recorded.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> getLastSeen(String userId) {
        String value = redisTemplate.opsForValue().get(HEARTBEAT_KEY_PREFIX + userId);
        if (value == null) return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
    }

    /**
     * Returns the last known battery level from Redis.
     * Returns -1 if unknown (Redis key expired or never set).
     */
    public int getLastBatteryLevel(String userId) {
        String value = redisTemplate.opsForValue().get(BATTERY_KEY_PREFIX + userId);
        return value != null ? Integer.parseInt(value) : -1;
    }

    /**
     * Checks whether the current time falls within this user's quiet hours.
     *
     * TIMEZONE HANDLING CONCEPT:
     * The user's quiet hours are stored in their local time (e.g. "22:00" to "07:00"
     * in "Asia/Manila"). We must convert the current UTC time to the user's timezone
     * before comparing. Without this, a user in Manila with quietStart=22:00 would
     * have their alerts suppressed at 10pm UTC (6am Manila time) — exactly wrong.
     */
    public boolean isInQuietHours(MonitoringSettings settings) {
        if (settings.getQuietStart() == null || settings.getQuietEnd() == null) {
            return false; // No quiet hours configured
        }

        ZoneId userZone = ZoneId.of(settings.getTimezone());
        ZonedDateTime nowInUserZone = ZonedDateTime.now(userZone);
        var currentTime = nowInUserZone.toLocalTime();

        var start = settings.getQuietStart();
        var end = settings.getQuietEnd();

        // Handle overnight quiet hours (e.g. 22:00 to 07:00)
        if (start.isAfter(end)) {
            // Overnight: quiet if time >= start OR time < end
            return currentTime.isAfter(start) || currentTime.isBefore(end);
        } else {
            // Same-day: quiet if start <= time < end
            return !currentTime.isBefore(start) && currentTime.isBefore(end);
        }
    }

    /**
     * Finds and resolves the user's current active alert if one exists.
     * Private helper used by both processPing and processCheckIn.
     */
    private void resolveActiveAlertIfPresent(User user, String resolutionNote) {
        alertEventRepository
            .findByMonitoredUserIdAndStateNot(user.getId(), AlertState.RESOLVED)
            .ifPresent(alert -> {
                alert.setState(AlertState.RESOLVED);
                alert.setResolvedAt(Instant.now());
                alert.setResolutionNote(resolutionNote);
                alertEventRepository.save(alert);
                log.info("Alert {} resolved for user {} — {}",
                    alert.getId(), user.getId(), resolutionNote);
            });
    }
}