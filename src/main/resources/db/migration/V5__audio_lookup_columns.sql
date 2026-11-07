-- Các cột này được dùng để tra audio theo POI và ngôn ngữ.
ALTER TABLE audio
    ADD COLUMN poi_id BIGINT NULL,
    ADD COLUMN language_code VARCHAR(10) NULL;

UPDATE audio a
JOIN translation t ON t.translation_id = a.translation_id
SET a.poi_id = t.poi_id,
    a.language_code = t.language_code
WHERE a.poi_id IS NULL OR a.language_code IS NULL;

ALTER TABLE audio
    MODIFY COLUMN poi_id BIGINT NOT NULL,
    MODIFY COLUMN language_code VARCHAR(10) NOT NULL,
    ADD CONSTRAINT fk_audio_poi FOREIGN KEY (poi_id) REFERENCES poi (poi_id),
    ADD CONSTRAINT fk_audio_language FOREIGN KEY (language_code) REFERENCES language (code),
    ADD UNIQUE KEY uq_audio_poi_language (poi_id, language_code);
