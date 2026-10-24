CREATE TABLE ai_description_draft (
    draft_id BIGINT NOT NULL AUTO_INCREMENT,
    poi_id BIGINT NOT NULL,
    source_snapshot TEXT NOT NULL,
    draft_content TEXT NULL,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(40) NOT NULL DEFAULT 'gemini',
    model VARCHAR(80) NOT NULL,
    error_message TEXT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (draft_id),
    KEY idx_ai_description_draft_poi_created (poi_id, created_at),
    CONSTRAINT fk_ai_description_draft_poi FOREIGN KEY (poi_id) REFERENCES poi (poi_id) ON DELETE CASCADE,
    CONSTRAINT chk_ai_description_draft_status CHECK (status IN ('DRAFT', 'ACCEPTED', 'DISCARDED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
