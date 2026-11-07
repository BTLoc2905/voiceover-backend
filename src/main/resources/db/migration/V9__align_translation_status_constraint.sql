UPDATE translation
SET status = CASE status
    WHEN 'Pending' THEN 'PENDING'
    WHEN 'Completed' THEN 'COMPLETED'
    ELSE status
END;

ALTER TABLE translation DROP CONSTRAINT chk_translation_status;

ALTER TABLE translation
    ADD CONSTRAINT chk_translation_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'));
