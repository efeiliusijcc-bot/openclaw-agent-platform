ALTER TABLE openclaw_audit_log
  ADD COLUMN IF NOT EXISTS result varchar(32) NOT NULL DEFAULT 'success';

COMMENT ON COLUMN openclaw_audit_log.result IS 'success/failed';

CREATE INDEX IF NOT EXISTS idx_openclaw_audit_result ON openclaw_audit_log (result);
