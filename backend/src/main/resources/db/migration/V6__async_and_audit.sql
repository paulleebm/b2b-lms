-- V6: outbox, scheduled-job locks, message de-duplication, audit log, reminders, and the public
-- certificate check.

-- ---------------------------------------------------------------------------
-- Outbox: Spring Modulith's event publication registry (schema v2 of spring-modulith-events-jdbc).
-- An event is written here in the same transaction as the business change; a relay then sends it
-- to SQS and marks it complete. A crash between commit and send leaves the row incomplete, and it
-- is re-sent (at-least-once). Global table: events carry their own tenant id.
-- ---------------------------------------------------------------------------
CREATE TABLE event_publication (
    id                     uuid                     NOT NULL PRIMARY KEY,
    listener_id            text                     NOT NULL,
    event_type             text                     NOT NULL,
    serialized_event       text                     NOT NULL,
    publication_date       timestamp with time zone NOT NULL,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamp with time zone
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- ShedLock: a scheduled job runs on one instance only, even with several tasks deployed.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamp     NOT NULL,
    locked_at  timestamp     NOT NULL,
    locked_by  varchar(255) NOT NULL
);

-- At-least-once delivery means a message can arrive twice: handlers record what they processed.
CREATE TABLE processed_message (
    message_id   uuid        NOT NULL,
    handler      varchar(64) NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, handler)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON event_publication, shedlock TO lms_app;
GRANT SELECT, INSERT, DELETE ON processed_message TO lms_app;

-- ---------------------------------------------------------------------------
-- Reminders: at most one per enrollment per day (the button can be pressed many times).
-- ---------------------------------------------------------------------------
ALTER TABLE enrollment ADD COLUMN reminded_at timestamptz;

-- ---------------------------------------------------------------------------
-- Audit log: who did what, append-only for the application (no UPDATE, no DELETE).
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id),
    actor_user_id uuid,                      -- null for system jobs
    action        varchar(64)  NOT NULL,     -- e.g. MEMBER_ROLE_CHANGED
    target_type   varchar(32)  NOT NULL,
    target_id     uuid,
    metadata      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    trace_id      varchar(64),
    created_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX audit_log_recent_idx ON audit_log (tenant_id, created_at DESC);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_log
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());
GRANT SELECT, INSERT ON audit_log TO lms_app;

-- ---------------------------------------------------------------------------
-- Public certificate check: anyone holding a serial may confirm it is genuine, without signing
-- in. RLS (rightly) hides every tenant's rows from an anonymous request, so — as with
-- user_memberships() in V1 — a SECURITY DEFINER function answers exactly this one question and
-- returns only what a verifier needs, with the holder's name masked.
-- ---------------------------------------------------------------------------
CREATE FUNCTION verify_certificate(p_serial text)
    RETURNS TABLE (serial text, holder_name text, course_title text, company_name text, issued_at timestamptz)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public
AS $$
    SELECT c.serial,
           CASE
               WHEN u.name IS NULL OR char_length(u.name) = 0 THEN '***'
               WHEN char_length(u.name) <= 2 THEN left(u.name, 1) || '*'
               ELSE left(u.name, 1) || repeat('*', char_length(u.name) - 2) || right(u.name, 1)
           END,
           co.title, t.name, c.issued_at
    FROM certificate c
    JOIN enrollment e ON e.id = c.enrollment_id
    JOIN membership m ON m.id = e.membership_id
    JOIN app_user u ON u.id = m.user_id
    JOIN course co ON co.id = e.course_id
    JOIN tenant t ON t.id = c.tenant_id
    WHERE c.serial = p_serial
$$;

-- FORCE RLS applies to the owner as well: give the owner read-only policies on what the function joins.
CREATE POLICY owner_read ON certificate FOR SELECT TO lms_owner USING (true);
CREATE POLICY owner_read ON enrollment FOR SELECT TO lms_owner USING (true);
CREATE POLICY owner_read ON course FOR SELECT TO lms_owner USING (true);

REVOKE ALL ON FUNCTION verify_certificate(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION verify_certificate(text) TO lms_app;
