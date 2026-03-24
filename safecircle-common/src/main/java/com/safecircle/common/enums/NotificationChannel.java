package com.safecircle.common.enums;

/**
 * The channels through which a care circle contact can be notified.
 * Each contact has a list of preferred channels stored in care_circle_members.notify_via.
 * The worker's NotificationRouter reads this list and sends via each channel in order.
 */
public enum NotificationChannel {
    PUSH,   // FCM (Android) or APNs (iOS) push notification
    SMS,    // Twilio SMS
    EMAIL   // Future — not in MVP
}