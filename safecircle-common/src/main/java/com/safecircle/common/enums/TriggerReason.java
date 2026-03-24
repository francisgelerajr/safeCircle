package com.safecircle.common.enums;

/**
 * Why an alert was triggered. This is what appears in the notification
 * sent to care circle contacts — it lets them understand what happened
 * before they act, instead of just "something went wrong".
 *
 * It also determines the wording of the alert message itself:
 *  - INACTIVE → "Maria has not checked in since 8:00 AM"
 *  - BATTERY_LOW → "Maria's phone battery may have died (was at 5%)"
 *  - OFFLINE → "Maria's phone has been unreachable since 8:00 AM"
 *  - MANUAL_SOS → "Maria has pressed the SOS button and may need help"
 */
public enum TriggerReason {
    INACTIVE,
    OFFLINE,
    BATTERY_LOW,
    MANUAL_SOS
}