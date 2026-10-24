UPDATE audio
SET status = CASE status
    WHEN 'Pending' THEN 'PENDING'
    WHEN 'Completed' THEN 'COMPLETED'
    ELSE status
END;

ALTER TABLE audio DROP CONSTRAINT chk_audio_status;

ALTER TABLE audio
    ADD CONSTRAINT chk_audio_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'TEXT_ONLY', 'FAILED'));
