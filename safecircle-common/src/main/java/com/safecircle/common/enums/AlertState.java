package com.safecircle.common.enums;

/**
 * The possible states of an alert event.
 *
 * STATE MACHINE CONCEPT:
 * At any moment, every alert is in exactly ONE of these states.
 * States only move forward (ACTIVE → NUDGE_SENT → CONTACT_ALERTED → RESOLVED).
 * The only exception is RESOLVED, which can be reached from any state
 * when the user checks in or a contact acknowledges.
 *
 * Storing this as a string enum in Postgres (via @Enumerated(EnumType.STRING))
 * rather than an integer means your database is human-readable — you can query
 * "SELECT * FROM alert_events WHERE state = 'CONTACT_ALERTED'" and understand it
 * immediately. Integer enums (0, 1, 2, 3) require you to look up the mapping.
 */
public enum AlertState {

    /**
     * Threshold exceeded. The inactivity checker has flagged this user.
     * A nudge push notification has been queued but not yet sent.
     */
    ACTIVE,

    /**
     * A gentle push notification has been sent to the monitored user.
     * Waiting for them to check in within the grace period.
     */
    NUDGE_SENT,

    /**
     * Grace period elapsed with no check-in.
     * One or more care circle contacts have been notified.
     * Waiting for a contact to acknowledge.
     */
    CONTACT_ALERTED,

    /**
     * Terminal state. Reached when:
     *  - The monitored user taps "I'm OK" (check-in)
     *  - A contact taps "Acknowledge / I'm handling this"
     *  - The monitored user taps "I'm fine" in response to a nudge
     */
    RESOLVED
}