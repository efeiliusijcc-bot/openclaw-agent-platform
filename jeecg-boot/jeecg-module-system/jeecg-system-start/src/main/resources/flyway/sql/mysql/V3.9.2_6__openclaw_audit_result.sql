ALTER TABLE openclaw_audit_log
  ADD COLUMN result varchar(32) NOT NULL DEFAULT 'success' COMMENT 'success/failed';

CREATE INDEX idx_openclaw_audit_result ON openclaw_audit_log (result);
