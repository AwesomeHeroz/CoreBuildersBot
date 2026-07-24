ALTER TABLE applications
    ADD COLUMN pending_guard VARCHAR(32) NULL,
    ADD UNIQUE KEY uq_applications_pending_guard (pending_guard);
