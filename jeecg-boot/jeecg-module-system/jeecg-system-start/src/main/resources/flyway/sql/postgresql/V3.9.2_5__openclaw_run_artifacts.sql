ALTER TABLE openclaw_agent_run
  ADD COLUMN IF NOT EXISTS full_output_path varchar(500) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS log_path varchar(500) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS error_type varchar(64) DEFAULT NULL;

COMMENT ON COLUMN openclaw_agent_run.full_output_path IS 'Full run output file path in workspace/output';
COMMENT ON COLUMN openclaw_agent_run.log_path IS 'Run diagnostic log file path in workspace/logs';
COMMENT ON COLUMN openclaw_agent_run.error_type IS 'Classified run error type';

CREATE INDEX IF NOT EXISTS idx_openclaw_run_error_type ON openclaw_agent_run (error_type);
