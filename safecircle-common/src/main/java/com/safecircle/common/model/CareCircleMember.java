package com.safecircle.common.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents one member of a user's care circle.
 *
 * MANY-TO-ONE RELATIONSHIP CONCEPT:
 * Many CareCircleMembers can belong to one monitored user.
 * Many CareCircleMembers can reference one contact user.
 * Both sides use @ManyToOne with FetchType.LAZY — we don't load the full
 * User object from the database unless we explicitly call .getMonitoredUser()
 * or .getContactUser(). This prevents unnecessary queries.
 *
 * ARRAY COLUMN CONCEPT:
 * notifyVia is a String[] mapped to a Postgres TEXT[] array column.
 * We store ["PUSH", "SMS"] directly in the row rather than creating a
 * separate join table. This works well for a small, fixed set of values
 * that are always read together with the member row.
 * The @Column definition uses columnDefinition to tell Postgres the type.
 */
@Entity
@Table(
    name = "care_circle_members",
    indexes = {
        @Index(name = "idx_ccm_monitored_user", columnList = "monitored_user_id"),
        @Index(name = "idx_ccm_contact_user",   columnList = "contact_user_id")
    },
    uniqueConstraints = {
        // A person can only appear once in a given care circle
        @UniqueConstraint(
            name = "uq_care_circle_pair",
            columnNames = {"monitored_user_id", "contact_user_id"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareCircleMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /** The person being monitored — the one who sends heartbeats. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitored_user_id", nullable = false)
    private User monitoredUser;

    /** The contact who receives alerts on behalf of the monitored user. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_user_id", nullable = false)
    private User contactUser;

    /**
     * Escalation order — contact with priority 1 is alerted first.
     * If they don't acknowledge within the window, priority 2 is alerted, etc.
     */
    @Column(name = "priority_order", nullable = false)
    @Builder.Default
    private Integer priorityOrder = 1;

    /**
     * Which channels to use when alerting this contact.
     * Stored as a Postgres array: TEXT[] → ["PUSH", "SMS"]
     */
    @Column(name = "notify_via", nullable = false, columnDefinition = "TEXT[]")
    @Builder.Default
    private String[] notifyVia = new String[]{"PUSH", "SMS"};

    /**
     * PENDING  — invitation sent, contact has not yet accepted
     * ACTIVE   — contact accepted, will receive alerts
     * DECLINED — contact declined the invitation
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}