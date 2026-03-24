package com.safecircle.common.model;

import com.safecircle.common.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per notification sent to one contact during one alert incident.
 *
 * This is what powers the audit trail:
 *   "Nudge sent to Maria at 08:00 via PUSH"
 *   "Alert sent to Pedro at 08:30 via SMS"
 *   "Pedro acknowledged at 08:33"
 *
 * Having this granularity means families can see exactly what happened
 * and when — reducing confusion and disputes after a false alarm.
 */
@Entity
@Table(name = "alert_escalations", indexes = {
    @Index(name = "idx_escalations_alert_event_id", columnList = "alert_event_id"),
    @Index(name = "idx_escalations_contact_id", columnList = "contact_user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_event_id", nullable = false)
    private AlertEvent alertEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id", nullable = false)
    private User contactUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    /**
     * DELIVERED: We successfully sent it to FCM/APNs/Twilio.
     * FAILED: The delivery attempt threw an exception (stored for debugging).
     * ACKNOWLEDGED: The contact tapped "I'm handling this."
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "DELIVERED";

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private Instant sentAt;

    /**
     * Set when the contact taps "Acknowledge". Null until then.
     * The presence of a non-null ackedAt is what stops further escalation.
     */
    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}