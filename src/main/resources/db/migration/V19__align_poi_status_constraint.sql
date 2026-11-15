-- Keep a CHECK throughout the upgrade, including databases whose CHECK was
-- previously expanded manually to accept HIDDEN alongside legacy ARCHIVED.
ALTER TABLE poi
    DROP CHECK chk_poi_status,
    ADD CONSTRAINT chk_poi_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN', 'ARCHIVED'));

-- Preserve every POI and keep legacy archived content non-public.
UPDATE poi
SET status = CASE
    WHEN UPPER(status) = 'ARCHIVED' THEN 'HIDDEN'
    ELSE UPPER(status)
END;

ALTER TABLE poi
    DROP CHECK chk_poi_status,
    ADD CONSTRAINT chk_poi_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'HIDDEN'));
