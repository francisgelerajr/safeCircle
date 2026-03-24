package com.safecircle.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safecircle.api.exception.NotFoundException;
import com.safecircle.api.repository.AlertEventRepository;
import com.safecircle.api.repository.CareCircleRepository;
import com.safecircle.common.enums.AlertState;
import com.safecircle.common.enums.NotificationChannel;
import com.safecircle.common.enums.TriggerReason;
import com.safecircle.common.model.AlertEscalation;
import com.safecircle.common.model.AlertEvent;
import com.safecircle.common.model.CareCircleMember;
import com.safecircle.common.model.User;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The escalation service manages the full alert lifecycle:
 *  1. Create an AlertEvent record in Postgres
 *  2. Enqueue a "nudge" job to SQS (sends push to the monitored user)
 *  3. After grace period, the scheduler calls escalateToContacts()
 *  4. For each contact, create AlertEscalation records and enqueue notification jobs
 *
 * SQS PRODUCER CONCEPT:
 * This service doesn't send push notifications or SMS directly.
 * Instead it puts a message on the SQS queue:
 *   { "type": "NUDGE_USER", "userId": "...", "alertEventId": "..." }
 *
 * The safecircle-worker container picks this up and does the actual sending.
 * This decouples the API from external services — if Twilio is slow,
 * it only slows down the worker, not the API. And if sending fails,
 * SQS retries it automatically.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService {

    private final AlertEventRepository alertEventRepository;
    private final CareCircleRepository careCircleRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${safecircle.sqs.notification-queue-name}")
    private String notificationQueueName;

    /**
     * Entry point — called by InactivityCheckerJob when a user is detected as inactive.
     * Creates the alert record and enqueues the first nudge to the user.
     */
    @Transactional
    public AlertEvent triggerEscalation(User user, TriggerReason reason, int batteryLevel) {
        // Create and persist the alert event
        AlertEvent alert = AlertEvent.builder()
            .monitoredUser(user)
            .triggerReason(reason)
            .state(AlertState.NUDGE_SENT)
            .batteryLevelAtTrigger(batteryLevel >= 0 ? batteryLevel : null)
            .build();

        alertEventRepository.save(alert);

        // Enqueue a nudge push notification to the monitored user
        // The worker will send: "Are you okay? Please check in within 30 minutes."
        enqueueJob(Map.of(
            "type", "NUDGE_USER",
            "alertEventId", alert.getId().toString(),
            "userId", user.getId().toString(),
            "reason", reason.name()
        ));

        log.info("Alert {} created for user {} — reason: {}", alert.getId(), user.getId(), reason);
        return alert;
    }

    /**
     * Called by the InactivityCheckerJob after the grace period has elapsed
     * with no check-in response. Transitions state → CONTACT_ALERTED and
     * enqueues notification jobs for each care circle contact in priority order.
     */
    @Transactional
    public void escalateToContacts(UUID alertEventId) {
        AlertEvent alert = alertEventRepository.findById(alertEventId)
            .orElseThrow(() -> new NotFoundException(
                "ALERT_NOT_FOUND", "Alert event not found: " + alertEventId));

        // Only escalate if still in NUDGE_SENT state
        // (user might have checked in between the scheduler runs)
        if (alert.getState() != AlertState.NUDGE_SENT) {
            log.info("Alert {} is no longer in NUDGE_SENT state — skipping escalation",
                alertEventId);
            return;
        }

        // Load care circle contacts in priority order (1 = first alerted)
        List<CareCircleMember> contacts = careCircleRepository
            .findActiveByMonitoredUserIdOrderByPriority(alert.getMonitoredUser().getId());

        if (contacts.isEmpty()) {
            log.warn("User {} has no active care circle contacts — cannot escalate",
                alert.getMonitoredUser().getId());
            return;
        }

        alert.setState(AlertState.CONTACT_ALERTED);
        alertEventRepository.save(alert);

        // Enqueue one notification job per contact per channel
        for (CareCircleMember contact : contacts) {
            List<NotificationChannel> channels = parseChannels(contact.getNotifyVia());

            for (NotificationChannel channel : channels) {
                // Record the escalation attempt in the audit trail
                AlertEscalation escalation = AlertEscalation.builder()
                    .alertEvent(alert)
                    .contactUser(contact.getContactUser())
                    .channel(channel)
                    .status("QUEUED")
                    .build();

                alert.getEscalations().add(escalation);

                // Enqueue the actual send job to the worker
                enqueueJob(Map.of(
                    "type", "NOTIFY_CONTACT",
                    "alertEventId", alert.getId().toString(),
                    "contactUserId", contact.getContactUser().getId().toString(),
                    "channel", channel.name(),
                    "triggerReason", alert.getTriggerReason().name(),
                    "monitoredUserName", alert.getMonitoredUser().getFullName(),
                    "batteryLevel", String.valueOf(
                        alert.getBatteryLevelAtTrigger() != null
                            ? alert.getBatteryLevelAtTrigger() : -1),
                    "triggeredAt", alert.getTriggeredAt().toString()
                ));
            }
        }

        alertEventRepository.save(alert);
        log.info("Alert {} escalated to {} contacts", alertEventId, contacts.size());
    }

    /**
     * Sends a message to the SQS notification queue.
     *
     * SqsTemplate.send() is the Spring Cloud AWS abstraction over the raw AWS SDK.
     * It serializes the payload Map to JSON and sends it to the named queue.
     * If SQS is temporarily unavailable, the @Transactional annotation ensures
     * the database changes are also rolled back — no alert record without a queued job.
     */
    private void enqueueJob(Map<String, String> payload) {
        sqsTemplate.send(to -> {
            try {
                to
                    .queue(notificationQueueName)
                    .payload(objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        );
    }

    private List<NotificationChannel> parseChannels(String[] notifyVia) {
        return Arrays.stream(notifyVia)
            .map(NotificationChannel::valueOf)
            .toList();
    }
}