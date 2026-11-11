-- V4: uploaded files and video assets.
--
-- Bytes never pass through the API: the browser uploads straight to S3 with a presigned URL,
-- and this table is the API's record of what is supposed to be at each object key.
--   PENDING  : URL handed out, upload not confirmed
--   UPLOADED : the API checked the object (HeadObject: size and type match what was declared)
--   ABORTED  : cancelled, or the object did not match; a lifecycle rule removes stragglers

CREATE TABLE stored_file (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid          NOT NULL REFERENCES tenant (id),
    purpose             varchar(20)   NOT NULL CHECK (purpose IN ('VIDEO_SOURCE', 'DOCUMENT')),
    object_key          varchar(512)  NOT NULL UNIQUE,  -- tenants/{tenant}/files/{id}/source — never the client's file name
    file_name           varchar(255)  NOT NULL,         -- shown to people, used in Content-Disposition
    content_type        varchar(100)  NOT NULL,
    size_bytes          bigint        NOT NULL CHECK (size_bytes > 0),
    status              varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'UPLOADED', 'ABORTED')),
    multipart_upload_id varchar(1024),                  -- S3 UploadId while a multipart upload is open
    uploaded_by         uuid          NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    uploaded_at         timestamptz,
    CONSTRAINT stored_file_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT stored_file_uploader_fk FOREIGN KEY (tenant_id, uploaded_by) REFERENCES membership (tenant_id, id)
);
-- For the cleanup job: abandoned uploads older than a day.
CREATE INDEX stored_file_pending_idx ON stored_file (created_at) WHERE status = 'PENDING';

-- A video is its source file plus the transcoded HLS rendition (MediaConvert in AWS).
CREATE TABLE video_asset (
    id               uuid PRIMARY KEY,
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    source_file_id   uuid         NOT NULL UNIQUE,
    status           varchar(20)  NOT NULL CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    hls_manifest_key varchar(512),
    duration_seconds integer CHECK (duration_seconds >= 0),
    transcode_job_id varchar(100),
    failure_reason   varchar(500),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    ready_at         timestamptz,
    CONSTRAINT video_asset_tenant_id_unique UNIQUE (tenant_id, id),
    CONSTRAINT video_asset_source_fk FOREIGN KEY (tenant_id, source_file_id) REFERENCES stored_file (tenant_id, id)
);

-- The lesson columns created in V3 now get their tenant-scoped references.
-- (MATCH SIMPLE: a NULL video_asset_id / file_id is simply not checked.)
ALTER TABLE lesson ADD CONSTRAINT lesson_video_fk
    FOREIGN KEY (tenant_id, video_asset_id) REFERENCES video_asset (tenant_id, id);
ALTER TABLE lesson ADD CONSTRAINT lesson_file_fk
    FOREIGN KEY (tenant_id, file_id) REFERENCES stored_file (tenant_id, id);

ALTER TABLE stored_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE stored_file FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON stored_file
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

ALTER TABLE video_asset ENABLE ROW LEVEL SECURITY;
ALTER TABLE video_asset FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON video_asset
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

-- No DELETE: files are aborted, not removed, so audit and cleanup can see what happened.
GRANT SELECT, INSERT, UPDATE ON stored_file, video_asset TO lms_app;
