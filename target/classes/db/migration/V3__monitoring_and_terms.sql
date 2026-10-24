-- sự kiện khi người dùng mở app, chạm poi, tự phát âm thanh, hoàn thành âm thanh hoặc chọn tuyến
CREATE TABLE usage_event
(
    usage_event_id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(20) NOT NULL, -- loại sự kiện hệ thống
    poi_id BIGINT NULL, -- id của poi nếu sự kiện liên quan đến poi
    language_code VARCHAR(10) NULL, -- mã ngôn ngữ
    route_id BIGINT NULL, -- id của tuyến
    anon_client_id VARCHAR(64) NULL, -- id ẩn danh của người dùng
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), -- thời gian xảy ra sự kiện
    PRIMARY KEY (usage_event_id),
    KEY idx_usage_event_time      (occurred_at),
    KEY idx_usage_event_type_time (event_type, occurred_at),
    KEY idx_usage_event_poi_time  (poi_id, occurred_at),
    KEY idx_usage_event_client    (anon_client_id, occurred_at),
    CONSTRAINT chk_usage_event_type
        CHECK (event_type IN ('APP_OPEN', 'AUTO_PLAY', 'TAP_POI', 'AUDIO_COMPLETED', 'ROUTE_SELECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- nhật ký hệ thống lưu thông báo cảnh báo lỗi để admin theo dõi trên dashboard
CREATE TABLE system_log
(
    systemlog_id BIGINT       NOT NULL AUTO_INCREMENT,
    severity     VARCHAR(10)  NOT NULL, -- INFO, WARNING hoặc ERROR
    component    VARCHAR(100) NOT NULL, -- Database, API, UI
    message      TEXT         NOT NULL, -- thông báo cảnh báo lỗi
    occurred_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (systemlog_id),
    KEY idx_system_log_severity_time  (severity, occurred_at),
    KEY idx_system_log_component_time (component, occurred_at),
    CONSTRAINT chk_system_log_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lưu số liệu đo lường như thời gian phản hồi API, tỷ lệ lỗi hoặc mức sử dụng tài nguyên
CREATE TABLE system_metric
(
    systemmetric_id BIGINT       NOT NULL AUTO_INCREMENT,
    component       VARCHAR(100) NOT NULL,
    metric_name     VARCHAR(100) NOT NULL,
    `value`         DOUBLE       NOT NULL,
    collected_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (systemmetric_id),
    KEY idx_system_metric_lookup (component, metric_name, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lưu tên riêng, địa danh và thuật ngữ cần giữ nguyên hoặc xử lý đặc biệt khi dịch
CREATE TABLE protected_term
(
    protectedterm_id BIGINT       NOT NULL AUTO_INCREMENT,
    term             VARCHAR(255) NOT NULL,
    note             VARCHAR(500) NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (protectedterm_id),
    UNIQUE KEY uq_protected_term_term (term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;