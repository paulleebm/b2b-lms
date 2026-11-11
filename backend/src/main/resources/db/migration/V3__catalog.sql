-- V3: course catalog — course → section → lesson.
--
-- Same rules as V1/V2: every table carries tenant_id and is under FORCEd RLS, and every
-- reference is a composite (tenant_id, …) foreign key, because FK checks bypass RLS.

-- Courses are owned by a membership (the instructor in this company), not by a global user.
ALTER TABLE membership ADD CONSTRAINT membership_tenant_id_unique UNIQUE (tenant_id, id);

CREATE TABLE course (
    id           uuid PRIMARY KEY,
    tenant_id    uuid         NOT NULL REFERENCES tenant (id),
    title        varchar(200) NOT NULL,
    description  text         NOT NULL DEFAULT '',
    status       varchar(20)  NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    owner_id     uuid         NOT NULL,
    version      bigint       NOT NULL DEFAULT 0,       -- optimistic lock; exposed as the ETag
    published_at timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT course_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT course_owner_fk FOREIGN KEY (tenant_id, owner_id) REFERENCES membership (tenant_id, id)
);
CREATE INDEX course_list_idx ON course (tenant_id, status, updated_at DESC);
CREATE INDEX course_owner_idx ON course (tenant_id, owner_id);

CREATE TABLE course_section (
    id         uuid PRIMARY KEY,
    tenant_id  uuid         NOT NULL REFERENCES tenant (id),
    course_id  uuid         NOT NULL,
    title      varchar(200) NOT NULL,
    position   integer      NOT NULL CHECK (position >= 0),
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT section_course_fk FOREIGN KEY (tenant_id, course_id)
        REFERENCES course (tenant_id, id) ON DELETE CASCADE,
    -- Target of the lesson FK below: lets a lesson prove its section is in the same course.
    CONSTRAINT section_scope_unique UNIQUE (tenant_id, course_id, id),
    -- Deferred: a reorder rewrites many positions in one transaction; the check runs at commit.
    CONSTRAINT section_position_unique UNIQUE (course_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE lesson (
    id             uuid PRIMARY KEY,
    tenant_id      uuid         NOT NULL REFERENCES tenant (id),
    course_id      uuid         NOT NULL,                -- denormalised: publish checks and progress read by course
    section_id     uuid         NOT NULL,
    title          varchar(200) NOT NULL,
    type           varchar(20)  NOT NULL CHECK (type IN ('VIDEO', 'DOCUMENT', 'TEXT')),
    position       integer      NOT NULL CHECK (position >= 0),
    body           text,                                  -- TEXT lessons (Markdown)
    video_asset_id uuid,                                  -- VIDEO lessons; FK added with the files module (V4)
    file_id        uuid,                                  -- DOCUMENT lessons; FK added in V4
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    -- (tenant, course, section) must exist together: a lesson cannot claim course A while
    -- sitting in a section of course B.
    CONSTRAINT lesson_section_fk FOREIGN KEY (tenant_id, course_id, section_id)
        REFERENCES course_section (tenant_id, course_id, id) ON DELETE CASCADE,
    CONSTRAINT lesson_position_unique UNIQUE (section_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX lesson_course_idx ON lesson (tenant_id, course_id);

ALTER TABLE course ENABLE ROW LEVEL SECURITY;
ALTER TABLE course FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON course
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE course_section ENABLE ROW LEVEL SECURITY;
ALTER TABLE course_section FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON course_section
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE lesson ENABLE ROW LEVEL SECURITY;
ALTER TABLE lesson FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lesson
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

GRANT SELECT, INSERT, UPDATE, DELETE ON course, course_section, lesson TO lms_app;
