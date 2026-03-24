package com.safecircle.common.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA ENTITY CONCEPT:
 * @Entity tells Spring Data JPA that this class maps to a database table.
 * Each instance of this class corresponds to one row in the users table.
 *
 * LOMBOK CONCEPT:
 * @Data generates getters, setters, equals(), hashCode(), toString() at compile time.
 * @Builder generates a fluent builder: User.builder().email("...").build()
 * @NoArgsConstructor is required by JPA — it needs to instantiate entities via reflection.
 * @AllArgsConstructor is needed by @Builder.
 *
 * Without Lombok, you would write ~80 lines of boilerplate for this class.
 * With Lombok, it's clean and readable.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * UUID CONCEPT:
     * We use UUIDs (Universally Unique Identifiers) instead of auto-increment integers.
     * Why? Because:
     *  1. Integer IDs are guessable — a bad actor can enumerate user IDs
     *  2. UUIDs can be generated on the client before the server confirms creation
     *  3. They're safe to include in URLs: /api/v1/users/550e8400-e29b... reveals nothing
     *
     * @GeneratedValue(strategy = GenerationType.UUID) tells JPA to generate the UUID
     * automatically when a new User is persisted.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * The Cognito "sub" (subject) — the unique identifier Cognito assigns to every user.
     * We store this so we can look up our User row from the JWT token's "sub" claim.
     * unique = true creates a UNIQUE constraint in Postgres.
     */
    @Column(name = "cognito_sub", unique = true, nullable = false)
    private String cognitoSub;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    /**
     * Phone stored in E.164 format: +639171234567
     * E.164 is the international standard — it includes the country code,
     * has no spaces or dashes, and always starts with +.
     * This makes it directly usable with Twilio without any transformation.
     */
    @Column(unique = true, nullable = false)
    private String phone;

    /**
     * The FCM or APNs device token for push notifications.
     * Nullable — a user might not have a device token yet if they just registered.
     * Updated every time the app opens (see PATCH /users/me/device-token).
     */
    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "device_platform")
    @Enumerated(EnumType.STRING)
    private DevicePlatform devicePlatform;

    /**
     * @CreationTimestamp — Hibernate automatically sets this to the current UTC time
     * when the entity is first persisted. We never set it manually.
     * updatable = false ensures it can never be changed after creation.
     */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum DevicePlatform {
        ANDROID, IOS
    }
}