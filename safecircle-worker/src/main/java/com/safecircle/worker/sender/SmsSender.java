package com.safecircle.worker.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Sends SMS messages via the Twilio REST API.
 *
 * TWILIO CONCEPT:
 * Twilio is a cloud communications platform. To send an SMS:
 *   1. POST to https://api.twilio.com/2010-04-01/Accounts/{accountSid}/Messages.json
 *   2. Authenticate with HTTP Basic Auth (accountSid as username, authToken as password)
 *   3. Send form-encoded body: From, To, Body
 *
 * Twilio handles carrier routing, delivery receipts, and international numbers.
 * It supports Philippine numbers (+63) and has competitive rates for the region.
 *
 * WHY form-encoding instead of JSON?
 * Twilio's REST API is one of the older, battle-tested APIs — it predates JSON
 * becoming the universal standard. It accepts application/x-www-form-urlencoded.
 * This is a quirk of Twilio specifically, not a general pattern.
 *
 * SMS FALLBACK RATIONALE:
 * Push notifications require the app to be installed AND the device to have internet.
 * SMS works on any phone with a SIM card and any cellular signal.
 * For a safety app, SMS is the fallback that reaches people when push fails.
 */
@Component
@Slf4j
public class SmsSender {

    private final RestClient restClient;

    @Value("${safecircle.twilio.account-sid}")
    private String accountSid;

    @Value("${safecircle.twilio.auth-token}")
    private String authToken;

    @Value("${safecircle.twilio.from-number}")
    private String fromNumber;

    public SmsSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * Sends an SMS to the given phone number.
     *
     * @param toNumber E.164 format: +639171234567
     * @param body     The message text — keep under 160 chars to avoid multi-part SMS billing
     */
    public void send(String toNumber, String body) {
        if (toNumber == null || toNumber.isBlank()) {
            log.warn("Skipping SMS send — phone number is null or empty");
            return;
        }

        // Twilio uses form-encoded body, not JSON
        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        formBody.add("From", fromNumber);
        formBody.add("To", toNumber);
        formBody.add("Body", body);

        String url = String.format(
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", accountSid);

        try {
            restClient.post()
                .uri(url)
                // HTTP Basic Auth — Twilio uses accountSid:authToken
                .headers(h -> h.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formBody)
                .retrieve()
                .toBodilessEntity();

            log.info("SMS sent to {}", maskPhone(toNumber));

        } catch (Exception e) {
            throw new RuntimeException("Twilio SMS send failed: " + e.getMessage(), e);
        }
    }

    /** Masks phone number for logs: +639171234567 → +639*****4567 */
    private String maskPhone(String phone) {
        if (phone.length() < 8) return "****";
        return phone.substring(0, 4) + "*****" + phone.substring(phone.length() - 4);
    }
}