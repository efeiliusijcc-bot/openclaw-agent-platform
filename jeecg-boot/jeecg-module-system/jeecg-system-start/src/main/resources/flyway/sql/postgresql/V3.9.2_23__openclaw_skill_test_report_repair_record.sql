ALTER TABLE openclaw_skill_test_run
  ADD COLUMN agent_key varchar(160) DEFAULT NULL,
  ADD COLUMN lint_status varchar(32) DEFAULT NULL,
  ADD COLUMN gateway_status varchar(32) DEFAULT NULL,
  ADD COLUMN input_json text DEFAULT NULL,
  ADD COLUMN output_json text DEFAULT NULL,
  ADD COLUMN error_type varchar(64) DEFAULT NULL,
  ADD COLUMN error_code varchar(64) DEFAULT NULL,
  ADD COLUMN logs_json text DEFAULT NULL,
  ADD COLUMN report_json text DEFAULT NULL;

ALTER TABLE openclaw_skill_ai_edit_record
  ADD COLUMN record_type varchar(32) NOT NULL DEFAULT 'AI_EDIT',
  ADD COLUMN test_run_id varchar(36) DEFAULT NULL,
  ADD COLUMN repair_before_status varchar(32) DEFAULT NULL,
  ADD COLUMN repair_after_status varchar(32) DEFAULT NULL;

COMMENT ON COLUMN openclaw_skill_test_run.agent_key IS 'Temporary draft test agent key';
COMMENT ON COLUMN openclaw_skill_test_run.lint_status IS 'lint_passed/lint_failed';
COMMENT ON COLUMN openclaw_skill_test_run.gateway_status IS 'PENDING/REGISTERED/OK/ERROR/NOT_STARTED';
COMMENT ON COLUMN openclaw_skill_test_run.input_json IS 'Standardized test input payload';
COMMENT ON COLUMN openclaw_skill_test_run.output_json IS 'Standardized test output payload';
COMMENT ON COLUMN openclaw_skill_test_run.error_type IS 'Standardized error type';
COMMENT ON COLUMN openclaw_skill_test_run.error_code IS 'Standardized error code';
COMMENT ON COLUMN openclaw_skill_test_run.logs_json IS 'Standardized test report logs';
COMMENT ON COLUMN openclaw_skill_test_run.report_json IS 'Standardized test report snapshot';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.record_type IS 'AI_EDIT/AI_REPAIR';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.test_run_id IS 'Related Skill test run id';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.repair_before_status IS 'Test status before repair apply';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.repair_after_status IS 'Latest test status after repair apply/retest';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_agent ON openclaw_skill_test_run (agent_key);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_type ON openclaw_skill_ai_edit_record (record_type);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_test ON openclaw_skill_ai_edit_record (test_run_id);
