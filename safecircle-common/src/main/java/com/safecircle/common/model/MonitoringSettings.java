package com.safecircle.common.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * The monitored user's inactivity detection configuration.
 *
 * ONE-TO-ONE RELATIONSHIP CONCEPT:
 * Every User has exactly one MonitoringSettings row.
 * We store it in a separate table (not as columns on users) for two reasons:
 *  1. Separation of concerns — the users table stays focused on identity
 *  2. We can add new settings columns here without touching the users table
 *
 * @OneToOne with @JoinColumn creates a foreign key: monitored_settings.user_id → users.id
 * fetch = FetchType.LAZY means JPA won't load the User object from the database
 * unless you explicitly call .getUser() — this prevents unnecessary database queries.
 */
@Entity
@Table(name = "monitored_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * How many hours of inactivity before the escalation starts.
     * Default: 12 hours. Valid range: 1–72 hours.
     */
    @Column(name = "threshold_hours", nullable = false)
    @Builder.Default
    private Integer thresholdHours = 12;

    /**
     * How many minutes after the threshold before contacting the care circle.
     * During this window, the app nudges the user to check in.
     * Default: 30 minutes.
     */
    @Column(name = "grace_period_mins", nullable = false)
    @Builder.Default
    private Integer gracePeriodMins = 30;

    /**
     * LocalTime stores just the time-of-day (HH:mm:ss), no date, no timezone.
     * We store quiet hours in local time + timezone separately, so the scheduler
     * can correctly compute whether right now is within quiet hours for this user.
     *
     * Example: quietStart=22:00, quietEnd=07:00, timezone="Asia/Manila"
     * means "don't alert between 10pm and 7am Manila time."
     */
    @Column(name = "quiet_start")
    private LocalTime quietStart;

    @Column(name = "quiet_end")
    private LocalTime quietEnd;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "Asia/Manila";

    /**
     * Whether monitoring is currently paused (e.g. user is traveling).
     * The scheduler skips paused users entirely.
     */
    @Column(name = "is_paused", nullable = false)
    @Builder.Default
    private Boolean isPaused = false;

    /**
     * When a pause auto-expires. Null if not paused or paused indefinitely.
     * The scheduler checks: if isPaused AND pausedUntil < now, auto-resume.
     */
    @Column(name = "paused_until")
    private Instant pausedUntil;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}