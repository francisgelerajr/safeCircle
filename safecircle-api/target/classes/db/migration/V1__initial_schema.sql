-- FLYWAY MIGRATION V1: Initial schema
--
-- FLYWAY CONCEPT:
-- This file runs exactly ONCE on any database that hasn't seen it yet.
-- Flyway tracks which migrations have run in a table called flyway_schema_history.
-- If you need to change the schema later, you create V2__..., V3__..., etc.
-- You NEVER edit a migration file after it has been applied to any database.
-- Editing an applied migration causes Flyway to refuse to start (checksum mismatch).
--
-- FILE NAMING: V{version}__{description}.sql
-- The double underscore separates version from description.

-- ==========================================
-- EXTENSION: uuid-ossp
-- Enables gen_random_uuid() for UUID generation in Postgres.
-- ==========================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ==========================================
-- TABLE: users
-- ==========================================
CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    cognito_sub     VARCHAR(128) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(255) NOT NULL,
    phone           VARCHAR(20)  NOT NULL UNIQUE,  -- E.164 format: +639171234567
    device_token    TEXT,                           -- FCM or APNs token, nullable
    device_platform VARCHAR(10),                    -- 'ANDROID' or 'IOS'
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- TIMESTAMPTZ vs TIMESTAMP CONCEPT:
-- TIMESTAMPTZ (timestamp with time zone) stores the moment in time as UTC internally.
-- When you insert "2026-03-24 08:00:00+08:00", Postgres stores it as UTC.
-- TIMESTAMP (without time zone) stores whatever you give it, with no timezone info.
-- Always use TIMESTAMPTZ. Never use TIMESTAMP for anything that represents a real moment.

-- ==========================================
-- TABLE: monitored_settings
-- ==========================================
CREATE TABLE monitored_settings (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    threshold_hours     INT         NOT NULL DEFAULT 12,
    grace_period_mins   INT         NOT NULL DEFAULT 30,
    quiet_start         TIME,                           -- local time HH:mm
    quiet_end           TIME,
    timezone            VARCHAR(64) NOT NULL DEFAULT 'Asia/Manila',
    is_paused           BOOLEAN     NOT NULL DEFAULT FALSE,
    paused_until        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ON DELETE CASCADE CONCEPT:
-- If a user is deleted, all their monitored_settings rows are automatically deleted too.
-- Without this, deleting a user would fail because the foreign key would have nothing to point to.

-- ==========================================
-- TABLE: care_circle_members
-- ==========================================
CREATE TABLE care_circle_members (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    monitored_user_id   UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    priority_order      INT         NOT NULL DEFAULT 1,
    notify_via          TEXT[]      NOT NULL DEFAULT ARRAY['PUSH','SMS'],
    -- TEXT[] is a Postgres array column — stores ['PUSH', 'SMS'] directly.
    -- Alternative would be a separate care_circle_channels table, but for a
    -- small fixed set of values, an array column is simpler and fast enough.
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, ACTIVE, DECLINED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (monitored_user_id, contact_user_id)
    -- A person can only be in a care circle once per monitored user.
);

-- ==========================================
-- TABLE: alert_events
-- ==========================================
CREATE TABLE alert_events (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    monitored_user_id       UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    trigger_reason          VARCHAR(20) NOT NULL,  -- INACTIVE, OFFLINE, BATTERY_LOW, MANUAL_SOS
    state                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    battery_level_at_trigger INT,
    triggered_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ,
    resolution_note         VARCHAR(500)
);

CREATE INDEX idx_alert_events_user_id ON alert_events(monitored_user_id);
CREATE INDEX idx_alert_events_state   ON alert_events(state);
-- Composite index for the most common query: "active alerts for user X"
CREATE INDEX idx_alert_events_user_state ON alert_events(monitored_user_id, state);

-- ==========================================
-- TABLE: alert_escalations
-- ==========================================
CREATE TABLE alert_escalations (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_event_id  UUID        NOT NULL REFERENCES alert_events(id) ON DELETE CASCADE,
    contact_user_id UUID        NOT NULL REFERENCES users(id),
    channel         VARCHAR(10) NOT NULL,  -- PUSH, SMS, EMAIL
    status          VARCHAR(20) NOT NULL DEFAULT 'DELIVERED',
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    acked_at        TIMESTAMPTZ,           -- NULL until contact acknowledges
    failure_reason  VARCHAR(500)
);

CREATE INDEX idx_escalations_alert_event_id ON alert_escalations(alert_event_id);
CREATE INDEX idx_escalations_contact_id     ON alert_escalations(contact_user_id);