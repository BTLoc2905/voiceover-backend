-- Preserve coordinates entered with up to 17 decimal places instead of silently rounding to 6.
ALTER TABLE poi
    MODIFY COLUMN latitude DECIMAL(20,17) NOT NULL,
    MODIFY COLUMN longitude DECIMAL(20,17) NOT NULL;
