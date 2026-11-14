-- Keep legacy Tour Session rows and their original IDs as archival data.
-- Existing route/session_id values remain intact, but no longer constrain routes.
ALTER TABLE route
    DROP FOREIGN KEY fk_route_session,
    MODIFY COLUMN session_id BIGINT NULL,
    ADD COLUMN description TEXT NULL;

-- CLOSED is the legacy equivalent of a route hidden from visitors.
UPDATE route SET status = 'DRAFT' WHERE UPPER(status) = 'CLOSED';
UPDATE route SET status = UPPER(status);
ALTER TABLE route
    DROP CHECK chk_route_status,
    ADD CONSTRAINT chk_route_status CHECK (status IN ('DRAFT', 'PUBLISHED'));
