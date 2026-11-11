-- V5: learning — quizzes, assignments, enrollments, progress, attempts, certificates,
-- and the xAPI learning record.
--
-- Same rules as before: tenant_id on every row, FORCEd RLS, composite (tenant_id, …) foreign keys.

ALTER TABLE lesson ADD CONSTRAINT lesson_tenant_id_unique UNIQUE (tenant_id, id);

-- ---------------------------------------------------------------------------
-- Quiz (authored with the course; at most one per course)
-- ---------------------------------------------------------------------------
CREATE TABLE quiz (
    id             uuid PRIMARY KEY,
    tenant_id      uuid        NOT NULL REFERENCES tenant (id),
    course_id      uuid        NOT NULL UNIQUE,
    pass_score     integer     NOT NULL CHECK (pass_score BETWEEN 1 AND 100),   -- percent
    max_attempts   integer     CHECK (max_attempts >= 1),                        -- NULL = unlimited
    time_limit_sec integer     CHECK (time_limit_sec >= 60),                     -- NULL = no limit
    revision       integer     NOT NULL DEFAULT 1,  -- bumped on every edit; attempts record it
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT quiz_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT quiz_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES course (tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE question (
    id        uuid PRIMARY KEY,
    tenant_id uuid        NOT NULL REFERENCES tenant (id),
    quiz_id   uuid        NOT NULL,
    type      varchar(20) NOT NULL CHECK (type IN ('SINGLE', 'MULTIPLE')),
    prompt    text        NOT NULL,
    options   jsonb       NOT NULL,   -- [{"id": "a", "text": "..."}]
    answer    jsonb       NOT NULL,   -- ["a"]: never leaves the server in a learner response
    points    integer     NOT NULL CHECK (points BETWEEN 1 AND 100),
    position  integer     NOT NULL CHECK (position >= 0),
    CONSTRAINT question_quiz_fk FOREIGN KEY (tenant_id, quiz_id) REFERENCES quiz (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT question_position_unique UNIQUE (quiz_id, position) DEFERRABLE INITIALLY DEFERRED
);

-- ---------------------------------------------------------------------------
-- Assignment = the admin's intent ("dev team takes this course by 3/31").
-- Enrollment = one person taking one course. A department assignment fans out into many.
-- ---------------------------------------------------------------------------
CREATE TABLE assignment (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    course_id     uuid        NOT NULL,
    target_type   varchar(20) NOT NULL CHECK (target_type IN ('DEPARTMENT', 'MEMBER')),
    department_id uuid,
    membership_id uuid,
    due_at        timestamptz,
    assigned_by   uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT assignment_target_check CHECK (
        (target_type = 'DEPARTMENT' AND department_id IS NOT NULL AND membership_id IS NULL)
        OR (target_type = 'MEMBER' AND membership_id IS NOT NULL AND department_id IS NULL)),
    CONSTRAINT assignment_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES course (tenant_id, id),
    CONSTRAINT assignment_department_fk FOREIGN KEY (tenant_id, department_id) REFERENCES department (tenant_id, id),
    CONSTRAINT assignment_member_fk FOREIGN KEY (tenant_id, membership_id) REFERENCES membership (tenant_id, id),
    CONSTRAINT assignment_assigner_fk FOREIGN KEY (tenant_id, assigned_by) REFERENCES membership (tenant_id, id)
);
-- New hires: "which active department assignments apply to this department?"
CREATE INDEX assignment_department_idx ON assignment (tenant_id, department_id) WHERE department_id IS NOT NULL;
CREATE INDEX assignment_course_idx ON assignment (tenant_id, course_id);

CREATE TABLE enrollment (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    membership_id uuid        NOT NULL,
    course_id     uuid        NOT NULL,
    status        varchar(20) NOT NULL CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    due_at        timestamptz,              -- the earliest due date of all assignments that apply
    started_at    timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    -- One enrollment per person and course, however many assignments overlap. The constraint,
    -- not a "select then insert", is what makes concurrent assignments safe (ON CONFLICT).
    CONSTRAINT enrollment_unique UNIQUE (membership_id, course_id),
    CONSTRAINT enrollment_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT enrollment_member_fk FOREIGN KEY (tenant_id, membership_id) REFERENCES membership (tenant_id, id),
    CONSTRAINT enrollment_course_fk FOREIGN KEY (tenant_id, course_id) REFERENCES course (tenant_id, id)
);
-- "Overdue" is computed (due_at < now() and not completed), never stored; this index serves it.
CREATE INDEX enrollment_status_due_idx ON enrollment (tenant_id, status, due_at);
CREATE INDEX enrollment_course_idx ON enrollment (tenant_id, course_id);

-- Current state per lesson (overwritten). The event history lives in learning_record.
CREATE TABLE lesson_progress (
    id                uuid PRIMARY KEY,
    tenant_id         uuid        NOT NULL REFERENCES tenant (id),
    enrollment_id     uuid        NOT NULL,
    lesson_id         uuid        NOT NULL,
    watched_sec       integer     NOT NULL DEFAULT 0 CHECK (watched_sec >= 0),
    last_position_sec integer     NOT NULL DEFAULT 0 CHECK (last_position_sec >= 0),
    last_heartbeat_at timestamptz,
    progressed_step   integer     NOT NULL DEFAULT 0,   -- highest 25% step already recorded as xAPI 'progressed'
    completed_at      timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT lesson_progress_unique UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT lesson_progress_enrollment_fk FOREIGN KEY (tenant_id, enrollment_id) REFERENCES enrollment (tenant_id, id),
    CONSTRAINT lesson_progress_lesson_fk FOREIGN KEY (tenant_id, lesson_id) REFERENCES lesson (tenant_id, id)
);

CREATE TABLE quiz_attempt (
    id            uuid PRIMARY KEY,
    tenant_id     uuid        NOT NULL REFERENCES tenant (id),
    enrollment_id uuid        NOT NULL,
    quiz_id       uuid        NOT NULL,
    quiz_revision integer     NOT NULL,
    status        varchar(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    answers       jsonb,      -- what was submitted, and how each question was graded
    score         integer,    -- percent
    passed        boolean,
    started_at    timestamptz NOT NULL,
    deadline_at   timestamptz,
    submitted_at  timestamptz,
    CONSTRAINT quiz_attempt_enrollment_fk FOREIGN KEY (tenant_id, enrollment_id) REFERENCES enrollment (tenant_id, id),
    CONSTRAINT quiz_attempt_quiz_fk FOREIGN KEY (tenant_id, quiz_id) REFERENCES quiz (tenant_id, id)
);
-- At most one open attempt per enrollment: two "start" clicks cannot open two attempts.
CREATE UNIQUE INDEX quiz_attempt_one_open ON quiz_attempt (enrollment_id) WHERE status = 'IN_PROGRESS';

-- Created in the same transaction as the completion. The PDF comes later (Day 4: outbox → worker).
CREATE TABLE certificate (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id),
    enrollment_id uuid         NOT NULL UNIQUE,
    serial        varchar(32)  NOT NULL UNIQUE,   -- printed on the certificate, used for public verification
    object_key    varchar(512),                   -- NULL until the PDF exists
    issued_at     timestamptz  NOT NULL,
    CONSTRAINT certificate_enrollment_fk FOREIGN KEY (tenant_id, enrollment_id) REFERENCES enrollment (tenant_id, id)
);

-- Idempotency-Key results for POST /v1/assignments, stored in the same transaction as the work
-- itself: a crash can never leave "assignment created, key not recorded" (or the reverse).
CREATE TABLE idempotency_record (
    tenant_id     uuid         NOT NULL REFERENCES tenant (id),
    scope         varchar(50)  NOT NULL,
    key           varchar(100) NOT NULL,
    request_hash  varchar(64)  NOT NULL,   -- same key + different body = client bug → 422
    response      jsonb        NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, scope, key)
);

-- ---------------------------------------------------------------------------
-- xAPI learning record: append-only history. The id is the xAPI statement id, so the same
-- statement stored twice collides on the primary key (idempotent).
-- ---------------------------------------------------------------------------
CREATE TABLE learning_record (
    id            uuid PRIMARY KEY,
    tenant_id     uuid         NOT NULL REFERENCES tenant (id),
    enrollment_id uuid         NOT NULL,            -- xAPI context.registration
    actor_user_id uuid         NOT NULL,
    verb_iri      varchar(200) NOT NULL,
    object_iri    varchar(300) NOT NULL,
    result        jsonb,
    statement     jsonb        NOT NULL,            -- the full xAPI statement
    "timestamp"   timestamptz  NOT NULL,
    stored        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT learning_record_enrollment_fk FOREIGN KEY (tenant_id, enrollment_id) REFERENCES enrollment (tenant_id, id)
);
CREATE INDEX learning_record_registration_idx ON learning_record (tenant_id, enrollment_id, "timestamp");
CREATE INDEX learning_record_verb_idx ON learning_record (tenant_id, verb_iri, "timestamp");

-- ---------------------------------------------------------------------------
-- Row-level security for every new table
-- ---------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['quiz', 'question', 'assignment', 'enrollment', 'lesson_progress',
                             'quiz_attempt', 'certificate', 'idempotency_record', 'learning_record']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format('CREATE POLICY tenant_isolation ON %I USING (tenant_id = app_current_tenant()) '
                       'WITH CHECK (tenant_id = app_current_tenant())', t);
    END LOOP;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON quiz, question TO lms_app;
GRANT SELECT, INSERT, UPDATE ON assignment, enrollment, lesson_progress, quiz_attempt, certificate TO lms_app;
GRANT SELECT, INSERT, DELETE ON idempotency_record TO lms_app;
-- History is append-only for the application: no UPDATE, no DELETE.
GRANT SELECT, INSERT ON learning_record TO lms_app;
