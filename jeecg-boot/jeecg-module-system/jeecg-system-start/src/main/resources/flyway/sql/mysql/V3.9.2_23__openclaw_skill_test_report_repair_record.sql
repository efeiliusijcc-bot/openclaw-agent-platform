ALTER TABLE openclaw_skill_test_run
  ADD COLUMN agent_key varchar(160) DEFAULT NULL COMMENT 'Temporary draft test agent key' AFTER username,
  ADD COLUMN lint_status varchar(32) DEFAULT NULL COMMENT 'lint_passed/lint_failed' AFTER status,
  ADD COLUMN gateway_status varchar(32) DEFAULT NULL COMMENT 'PENDING/REGISTERED/OK/ERROR/NOT_STARTED' AFTER lint_status,
  ADD COLUMN input_json longtext DEFAULT NULL COMMENT 'Standardized test input payload' AFTER expected_output,
  ADD COLUMN output_json longtext DEFAULT NULL COMMENT 'Standardized test output payload' AFTER input_json,
  ADD COLUMN error_type varchar(64) DEFAULT NULL COMMENT 'Standardized error type' AFTER output_summary,
  ADD COLUMN error_code varchar(64) DEFAULT NULL COMMENT 'Standardized error code' AFTER error_type,
  ADD COLUMN logs_json longtext DEFAULT NULL COMMENT 'Standardized test report logs' AFTER error_message,
  ADD COLUMN report_json longtext DEFAULT NULL COMMENT 'Standardized test report snapshot' AFTER logs_json;

ALTER TABLE openclaw_skill_ai_edit_record
  ADD COLUMN record_type varchar(32) NOT NULL DEFAULT 'AI_EDIT' COMMENT 'AI_EDIT/AI_REPAIR' AFTER user_id,
  ADD COLUMN test_run_id varchar(36) DEFAULT NULL COMMENT 'Related Skill test run id' AFTER record_type,
  ADD COLUMN repair_before_status varchar(32) DEFAULT NULL COMMENT 'Test status before repair apply' AFTER error_message,
  ADD COLUMN repair_after_status varchar(32) DEFAULT NULL COMMENT 'Latest test status after repair apply/retest' AFTER repair_before_status;

CREATE INDEX idx_openclaw_skill_test_agent ON openclaw_skill_test_run (agent_key);
CREATE INDEX idx_openclaw_skill_ai_edit_type ON openclaw_skill_ai_edit_record (record_type);
CREATE INDEX idx_openclaw_skill_ai_edit_test ON openclaw_skill_ai_edit_record (test_run_id);
