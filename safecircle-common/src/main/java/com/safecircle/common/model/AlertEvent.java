package com.safecircle.common.model;

import com.safecircle.common.enums.AlertState;
import com.safecircle.common.enums.TriggerReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One row per inactivity incident.
 *
 * RELATIONSHIP CONCEPT — @OneToMany:
 * One AlertEvent generates many AlertEscalations (one per contact notified).
 * cascade = CascadeType.ALL means if you save an AlertEvent, its escalations
 * are saved automatically. If you delete it, escalations are deleted too.
 * orphanRemoval = true means if you remove an escalation from the list and save,
 * it gets deleted from the database — no orphaned rows.
 *
 * mappedBy = "alertEvent" tells JPA that the foreign key lives on the
 * AlertEscalation side (alert_escalations.alert_event_id), not here.
 */
@Entity
@Table(name = "alert_events", indexes = {
    // INDEX CONCEPT:
    // Without an index, "find all alerts for user X" requires Postgres to scan
    // every row in the table. With an index on monitored_user_id, it jumps
    // directly to matching rows. Essential for any column you filter or sort by.
    @Index(name = "idx_alert_events_user_id", columnList = "monitored_user_id"),
    @Index(name = "idx_alert_events_state", columnList = "state")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_user_id", nullable = false)
    private User monitoredUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false)
    private TriggerReason triggerReason;

    /**
     * The current state in the escalation state machine.
     * Updated as the alert progresses through the ladder.
     * Indexed so the scheduler can efficiently query active alerts.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AlertState state = AlertState.ACTIVE;

    /**
     * Battery level at the time the alert triggered (0–100).
     * Stored so we can tell contacts "her battery was at 5% when we last heard from her."
     */
    @Column(name = "battery_level_at_trigger")
    private Integer batteryLevelAtTrigger;

    @CreationTimestamp
    @Column(name = "triggered_at", updatable = false, nullable = false)
    private Instant triggeredAt;

    /**
     * Set when state transitions to RESOLVED.
     * Null while the alert is still active.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Optional note added when a contact acknowledges.
     * e.g. "I called her — she is fine, phone was on silent."
     */
    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @OneToMany(mappedBy = "alertEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AlertEscalation> escalations = new ArrayList<>();
}