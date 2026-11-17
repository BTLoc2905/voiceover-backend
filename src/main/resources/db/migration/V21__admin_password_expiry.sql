-- Give existing accounts a 24-hour transition period; preserve all credentials.
ALTER TABLE admin_account
    ADD COLUMN password_changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
