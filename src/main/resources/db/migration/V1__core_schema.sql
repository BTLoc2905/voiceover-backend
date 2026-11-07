-- danh mục ngôn ngữ từ hệ thống
CREATE TABLE language
(
    code VARCHAR(10) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE, -- ngôn ngữ có đang mặc định hay không
    is_active BOOLEAN NOT NULL DEFAULT TRUE, -- ngôn ngữ có đang hoạt động hay không
    tts_supported BOOLEAN NOT NULL DEFAULT FALSE, -- ngôn ngữ có hỗ trợ tts hay không
    default_marker TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END) STORED, -- đảm bảo tối đa 1 ngôn ngữ làm mặc định
    UNIQUE KEY uq_language_default_marker (default_marker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- tài khoản admin_account
CREATE TABLE AdminAccount
(
    admin_account_id BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(150) NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (admin_account_id),
    UNIQUE KEY uq_admin_account_email (email),

    CONSTRAINT chk_admin_account_status
        CHECK (status IN ('ACTIVE', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- poi tham quan
CREATE TABLE poi
(
    poi_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của điểm tham quan
    name VARCHAR(200) NOT NULL,
    latitude DECIMAL(9,6) NOT NULL, -- lưu vĩ độ của điểm tham quan
    longitude DECIMAL(9,6) NOT NULL, -- lưu kinh độ của điểm tham quan
    radius_m INT NOT NULL DEFAULT 50, -- bán kính của điểm tham quan tính bằng mét
    thumbnail_url VARCHAR(500) NULL, -- đường dẫn đến hình ảnh thu nhỏ
    description_source VARCHAR(500) NULL, -- nguồn mô tả của điểm tham quan
    source_language_code VARCHAR(10) NOT NULL, -- mã ngôn ngữ của nguồn mô tả
    status VARCHAR(10) NOT NULL DEFAULT 'Draft', -- trạng thái của điểm tham quan
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (poi_id),
    KEY id_poi_status (status),
    KEY idx_poi_lat_lng (latitude, longitude),
    CONSTRAINT fk_poi_source_language FOREIGN KEY (source_language_code) REFERENCES language (code),
    CONSTRAINT chk_poi_status CHECK (status IN ('Draft', 'Published', 'Archived')),
    CONSTRAINT chk_poi_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT chk_poi_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_poi_radius    CHECK (radius_m > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- bản dịch theo poi và ngôn ngữ
CREATE TABLE translation
(
    translation_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của bản dịch
    poi_id BIGINT NOT NULL,
    language_code VARCHAR(10) NOT NULL,
    content TEXT NULL, -- nội dung thuyết minh sau khi dịch, có thể null khi đang Pending
    status VARCHAR(12) NOT NULL DEFAULT 'Pending', -- trạng thái của bản dịch
    source_hash CHAR(64) NULL, -- mã băm của nội dung thuyết minh gốc
    is_manual_edit BOOLEAN NOT NULL DEFAULT FALSE, -- kiểm tra admin có sửa bản dịch hay không
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (translation_id),
    UNIQUE KEY uq_translation_poi_language (poi_id, language_code),
    CONSTRAINT fk_translation_poi FOREIGN KEY (poi_id) REFERENCES poi (poi_id),
    CONSTRAINT fk_translation_language FOREIGN KEY (language_code) REFERENCES language (code),
    CONSTRAINT chk_translation_status CHECK (status IN ('Pending', 'Completed', 'FAILED')),
    CONSTRAINT chk_translation_completed_has_content CHECK (status <> 'Completed' OR content IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- file audio thuyết minh
CREATE TABLE audio
(
    audio_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của file audio
    translation_id BIGINT NOT NULL, -- mã định danh của bản dịch
    file_path VARCHAR(500) NULL, -- đường dẫn đến file âm thanh thuyết minh
    durtation_rec INT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'Pending', -- trạng thái của file audio thuyết minh
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audio_id),
    UNIQUE KEY uq_audio_translation (translation_id),
    CONSTRAINT fk_audio_translation FOREIGN KEY (translation_id) REFERENCES translation (translation_id),
    CONSTRAINT chk_audio_status CHECK (status IN ('Pending', 'Completed', 'FAILED')),
    CONSTRAINT chk_audio_completed_has_content CHECK (status <> 'Completed' OR (file_path IS NOT NULL AND durtation_rec IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- xử lý tác vụ pipeline mỗi tác vụ cho 1 (poi, language) duy nhất
CREATE TABLE pipeline_task
(
    pipeline_task_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của tác vụ pipeline
    poi_id BIGINT NOT NULL, -- mã định danh của điểm tham quan
    target_language_code VARCHAR(10) NOT NULL, -- mã ngôn ngữ của bản dịch
    task_type VARCHAR(15) NOT NULL, -- TRANSLATE_TTS hoặc TTS_ONLY
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, COMPLETED, FAILED
    retry_count INT NOT NULL DEFAULT 0, -- số lần thử lại
    error_message TEXT NULL, -- thông báo lỗi
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (pipeline_task_id),
    KEY idx_pipeline_task_status_created (status, created_at),
    KEY idx_pipeline_task_poi_language   (poi_id, target_language_code),
    CONSTRAINT fk_pipeline_task_poi
        FOREIGN KEY (poi_id) REFERENCES poi (poi_id) ON DELETE CASCADE,
    CONSTRAINT fk_pipeline_task_language
        FOREIGN KEY (target_language_code) REFERENCES language (code),
    CONSTRAINT chk_pipeline_task_type
        CHECK (task_type IN ('TRANSLATE_TTS', 'TTS_ONLY')),
    CONSTRAINT chk_pipeline_task_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_pipeline_task_retry
        CHECK (retry_count BETWEEN 0 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;