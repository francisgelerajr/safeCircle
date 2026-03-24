package com.safecircle.worker.model;

import lombok.Data;

/**
 * The payload of every SQS message this worker processes.
 *
 * The API's EscalationService serializes a Map<String, String> to JSON and
 * puts it on the queue. This class deserializes that JSON back into typed fields.
 *
 * MESSAGE TYPES:
 *
 * NUDGE_USER — sent to the monitored user asking them to check in.
 *   Required fields: userId, alertEventId, reason
 *
 * NOTIFY_CONTACT — sent to a care circle contact when escalation fires.
 *   Required fields: contactUserId, alertEventId, channel, triggerReason,
 *                    monitoredUserName, batteryLevel, triggeredAt
 *
 * Using a single class with a 'type' discriminator field is simpler than
 * separate classes for an MVP. As the system grows you might use sealed
 * classes or a proper command pattern.
 */
@Data
public class NotificationJob {

    /** Discriminates which processing path to take. */
    private String type;

    /** ID of the AlertEvent record — for audit trail updates. */
    private String alertEventId;

    /** For NUDGE_USER: the monitored user's ID. */
    private String userId;

    /** For NOTIFY_CONTACT: the contact user's ID. */
    private String contactUserId;

    /** For NOTIFY_CONTACT: "PUSH", "SMS", or "EMAIL" */
    private String channel;

    /** "INACTIVE", "BATTERY_LOW", "OFFLINE", "MANUAL_SOS" */
    private String triggerReason;
    private String reason;  // alias used in NUDGE_USER messages

    /** Display name of the monitored user — used in notification text. */
    private String monitoredUserName;

    /** Last known battery % — included in alert message to contacts. */
    private String batteryLevel;

    /** ISO 8601 timestamp when the alert was first triggered. */
    private String triggeredAt;
}