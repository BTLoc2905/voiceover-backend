-- phiên tham quan session
CREATE TABLE session
(
    session_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của phiên tham quan
    name VARCHAR(255) NOT NULL, -- tên của phiên tham quan
    description VARCHAR(500) NULL, -- mô tả của phiên tham quan, có thể null
    status VARCHAR(10) NOT NULL DEFAULT 'Draft', -- trạng thái của phiên tham quan, mặc định là Draft
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (session_id),
    KEY idx_session_status (status),
    CONSTRAINT chk_session_status CHECK (status IN ('Draft', 'Published', 'Archived'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- tuyến tham quan route dùng để lưu các tuyến tham quan, mỗi tuyến được liên kết với 1 session
CREATE TABLE route
(
    route_id   BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của tuyến tham quan
    session_id BIGINT NOT NULL, -- mã định danh của phiên tham quan
    name       VARCHAR(255) NOT NULL, -- tên của tuyến tham quan
    status     VARCHAR(10)  NOT NULL DEFAULT 'DRAFT', -- trạng thái của tuyến tham quan
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (route_id),
    KEY idx_route_session (session_id),
    KEY idx_route_status  (status),
    CONSTRAINT fk_route_session
        FOREIGN KEY (session_id) REFERENCES session (session_id),
    CONSTRAINT chk_route_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- điểm dừng chân trên tuyến
CREATE TABLE route_stop
(
    routestop_id BIGINT NOT NULL AUTO_INCREMENT, -- mã định danh duy nhất của điểm dừng chân
    route_id     BIGINT NOT NULL, -- mã định danh của tuyến tham quan
    poi_id       BIGINT NOT NULL, -- mã định danh của điểm tham quan
    stop_order   INT NOT NULL, -- thứ tự điểm dừng chân trên tuyến tham quan
    PRIMARY KEY (routestop_id),
    UNIQUE KEY uq_route_stop_order (route_id, stop_order),
    KEY idx_route_stop_poi (poi_id),
    CONSTRAINT fk_route_stop_route
        FOREIGN KEY (route_id) REFERENCES route (route_id) ON DELETE CASCADE,
    CONSTRAINT fk_route_stop_poi
        FOREIGN KEY (poi_id) REFERENCES poi (poi_id),
    CONSTRAINT chk_route_stop_order CHECK (stop_order BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;