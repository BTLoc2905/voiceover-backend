ALTER TABLE admin_account
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ADMIN';

INSERT INTO admin_account (email, password_hash, full_name, status, role)
SELECT 'admin@voicemap.local',
       '$2a$10$QYWptaq69u6Xo7qxBzMNQub57TzNd5YQ8N40zu0jc6AtBQ8bNWXoO',
       'Administrator',
       'ACTIVE',
       'ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM admin_account WHERE email = 'admin@voicemap.local'
);
