package com.safecircle.worker.sender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends push notifications to Android devices via Firebase Cloud Messaging (FCM).
 *
 * FCM HTTP v1 API CONCEPT:
 * FCM is Google's push notification service. When you want to send a notification
 * to an Android device, you send an HTTPS POST to Google's FCM endpoint with:
 *   - The device's FCM registration token (stored in users.device_token)
 *   - Your notification payload (title, body, data)
 *   - An Authorization header with your Firebase Server Key
 *
 * FCM then delivers the notification to the device, even if the app is in
 * the background or closed. The device's OS shows the notification.
 *
 * HIGH PRIORITY CONCEPT:
 * Normal FCM messages may be delayed or batched by the OS to save battery.
 * "priority": "high" bypasses Android's Doze mode and delivers immediately —
 * critical for a safety app where a 3am alert must wake the device.
 * On iOS this is handled by APNs "apns-priority: 10" in the APNs sender.
 *
 * RestClient CONCEPT:
 * Spring Boot 3.2's RestClient is the modern replacement for RestTemplate.
 * It's fluent, readable, and handles request/response serialization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmSender {

    private final RestClient restClient;

    @Value("${safecircle.fcm.server-key}")
    private String fcmServerKey;

    private static final String FCM_ENDPOINT =
        "https://fcm.googleapis.com/v1/projects/{projectId}/messages:send";

    /**
     * Sends a push notification to an Android device.
     *
     * @param deviceToken the FCM registration token from users.device_token
     * @param title       notification title — shown in the status bar
     * @param body        notification body — the full message text
     * @param data        key-value pairs passed to the app (for deep linking etc.)
     */
    public void send(String deviceToken, String title, String body, Map<String, String> data) {
        if (deviceToken == null || deviceToken.isBlank()) {
            log.warn("Skipping FCM send — device token is null or empty");
            return;
        }

        /*
         * FCM v1 API message structure.
         * "android.priority": "high" — bypasses battery optimization / Doze mode.
         * This is what makes the notification arrive immediately even at 3am.
         */
        Map<String, Object> message = Map.of(
            "message", Map.of(
                "token", deviceToken,
                "notification", Map.of(
                    "title", title,
                    "body", body
                ),
                "android", Map.of(
                    "priority", "high",
                    "notification", Map.of(
                        "sound", "default",
                        "channel_id", "safecircle_alerts"  // Android notification channel
                    )
                ),
                "data", data
            )
        );

        try {
            restClient.post()
                .uri(FCM_ENDPOINT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + fcmServerKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(message)
                .retrieve()
                .toBodilessEntity();

            log.info("FCM notification sent to token ending ...{}",
                deviceToken.substring(Math.max(0, deviceToken.length() - 8)));

        } catch (Exception e) {
            // Throw so the SQS listener can update status to FAILED and log the reason
            throw new RuntimeException("FCM send failed: " + e.getMessage(), e);
        }
    }
}