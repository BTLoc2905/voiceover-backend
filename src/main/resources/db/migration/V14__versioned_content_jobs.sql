-- Old in-flight jobs have no immutable input. Do not guess their source on restart.
UPDATE pipeline_task SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP(3),
    error_message = 'Tác vụ cũ chưa có phiên bản nguồn. Lưu lại POI hoặc yêu cầu tạo lại audio.'
WHERE status IN ('PENDING', 'PROCESSING');

ALTER TABLE poi
    ADD COLUMN content_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN source_fingerprint CHAR(64) NULL;

ALTER TABLE pipeline_task
    ADD COLUMN source_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN source_hash CHAR(64) NULL,
    ADD COLUMN source_text MEDIUMTEXT NULL,
    ADD COLUMN source_language_code VARCHAR(10) NULL,
    ADD COLUMN protected_terms_json MEDIUMTEXT NULL,
    ADD COLUMN translation_revision CHAR(64) NULL,
    ADD COLUMN claim_token VARCHAR(36) NULL,
    ADD COLUMN lease_until DATETIME(3) NULL,
    ADD COLUMN translation_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN audio_attempts INT NOT NULL DEFAULT 0,
    ADD KEY idx_pipeline_dispatch (status, lease_until, pipeline_task_id);

ALTER TABLE translation DROP CHECK chk_translation_status;
ALTER TABLE translation ADD CONSTRAINT chk_translation_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'STALE'));
