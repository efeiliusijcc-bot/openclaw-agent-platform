ALTER TABLE openclaw_agent_run
  ADD COLUMN full_output_path varchar(500) DEFAULT NULL COMMENT 'Full run output file path in workspace/output',
  ADD COLUMN log_path varchar(500) DEFAULT NULL COMMENT 'Run diagnostic log file path in workspace/logs',
  ADD COLUMN error_type varchar(64) DEFAULT NULL COMMENT 'Classified run error type';

CREATE INDEX idx_openclaw_run_error_type ON openclaw_agent_run (error_type);
