package com.safecircle.api.scheduler;

import com.safecircle.api.repository.AlertEventRepository;
import com.safecircle.api.repository.MonitoringSettingsRepository;
import com.safecircle.api.service.EscalationService;
import com.safecircle.api.service.HeartbeatService;
import com.safecircle.common.enums.AlertState;
import com.safecircle.common.enums.TriggerReason;
import com.safecircle.common.model.MonitoringSettings;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * THE HEART OF THE SAFETY SYSTEM.
 *
 * This job runs every 5 minutes (triggered by EventBridge Scheduler in production,
 * or by Spring's @Scheduled in local dev). It scans every active user, checks
 * their last heartbeat timestamp, and triggers escalation if they've been
 * inactive too long.
 *
 * @Scheduled CONCEPT:
 * fixedDelay = 5 minutes means "wait 5 minutes AFTER the previous run completes
 * before starting the next one." This prevents overlapping runs if processing
 * takes longer than expected.
 *
 * cron = "0 * /5 * * * *" would run every 5 minutes on the clock (00, 05, 10...).
 * fixedDelay is safer for this use case because it prevents concurrent runs.
 *
 * IMPORTANT — DISTRIBUTED LOCKING NOTE:
 * In production, multiple ECS tasks run simultaneously (one per AZ).
 * Without a distributed lock, BOTH tasks would run this job and send
 * duplicate alerts. The solution (to implement in v2) is a Redis-based
 * distributed lock — one task acquires the lock, runs the job, releases it.
 * The other task sees the lock is held and skips its run.
 * For MVP, run only ONE instance of the API container (tasks=1 in ECS).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InactivityCheckerJob {

    private final MonitoringSettingsRepository settingsRepository;
    private final AlertEventRepository alertEventRepository;
    private final HeartbeatService heartbeatService;
    private final EscalationService escalationService;
    private final MeterRegistry meterRegistry;

    /**
     * Runs every 5 minutes in local dev via Spring @Scheduled.
     * In production, EventBridge Scheduler triggers a dedicated HTTP endpoint
     * instead (more reliable than in-process scheduling in a containerized env).
     *
     * initialDelay: wait 30 seconds after startup before the first run —
     * gives the app time to fully initialize before doing database work.
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 30_000)
    public void checkAllUsers() {
        // Time the full job run — visible in CloudWatch metrics
        Timer.Sample sample = Timer.start(meterRegistry);

        log.info("InactivityCheckerJob starting at {}", Instant.now());
        int checked = 0, triggered = 0;

        try {
            // Load all non-paused monitoring settings in one query
            List<MonitoringSettings> activeSettings =
                settingsRepository.findAllActiveSettings(Instant.now());

            for (MonitoringSettings settings : activeSettings) {
                checked++;
                try {
                    processUser(settings);
                    triggered++;
                } catch (Exception e) {
                    // Catch per-user exceptions so one bad user doesn't abort the whole job
                    log.error("Error processing user {}: {}",
                        settings.getUser().getId(), e.getMessage(), e);
                }
            }

        } finally {
            sample.stop(Timer.builder("safecircle.inactivity.check.duration")
                .description("Time taken by InactivityCheckerJob")
                .register(meterRegistry));
            log.info("InactivityCheckerJob complete — checked: {}, escalations triggered: {}",
                checked, triggered);
        }
    }

    /**
     * Evaluates a single user's inactivity state and triggers escalation if needed.
     */
    private void processUser(MonitoringSettings settings) {
        String userId = settings.getUser().getId().toString();

        // Skip users in quiet hours — no alerts between e.g. 10pm and 7am
        if (heartbeatService.isInQuietHours(settings)) {
            log.debug("User {} is in quiet hours — skipping", userId);
            return;
        }

        // Skip if user already has an active alert (avoid duplicates)
        if (alertEventRepository.existsByMonitoredUserIdAndStateNot(
                settings.getUser().getId(), AlertState.RESOLVED)) {
            log.debug("User {} already has an active alert — skipping", userId);
            return;
        }

        // Get last seen timestamp from Redis
        Optional<Instant> lastSeen = heartbeatService.getLastSeen(userId);

        if (lastSeen.isEmpty()) {
            // No heartbeat ever recorded — user just registered or never opened the app.
            // Don't alert — they haven't set up monitoring yet.
            log.debug("No heartbeat recorded for user {} — skipping", userId);
            return;
        }

        Instant threshold = Instant.now()
            .minus(Duration.ofHours(settings.getThresholdHours()));

        if (lastSeen.get().isBefore(threshold)) {
            // Last seen was before the threshold — this user is inactive
            int batteryLevel = heartbeatService.getLastBatteryLevel(userId);

            // TRIGGER REASON LOGIC:
            // If battery was critically low (< 10%) at last contact, we label
            // the alert BATTERY_LOW — gives contacts a better explanation.
            // Otherwise it's just INACTIVE.
            TriggerReason reason = (batteryLevel >= 0 && batteryLevel < 10)
                ? TriggerReason.BATTERY_LOW
                : TriggerReason.INACTIVE;

            log.info("User {} inactive since {} — triggering {} alert (battery: {}%)",
                userId, lastSeen.get(), reason, batteryLevel);

            escalationService.triggerEscalation(settings.getUser(), reason, batteryLevel);
        }
    }
}