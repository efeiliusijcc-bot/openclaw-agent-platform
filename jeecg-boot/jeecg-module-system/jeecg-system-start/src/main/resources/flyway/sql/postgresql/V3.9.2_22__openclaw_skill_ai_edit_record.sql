CREATE TABLE IF NOT EXISTS openclaw_skill_ai_edit_record (
  id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  skill_id varchar(36) DEFAULT NULL,
  workspace_id varchar(36) DEFAULT NULL,
  user_id varchar(36) NOT NULL,
  user_instruction text DEFAULT NULL,
  summary text DEFAULT NULL,
  files_json text NOT NULL,
  warnings_json text DEFAULT NULL,
  base_version varchar(128) NOT NULL,
  base_hash varchar(128) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'PREVIEW',
  error_message text DEFAULT NULL,
  applied_time timestamp DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill_ai_edit_record IS 'OpenClaw skill AI edit preview record';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.files_json IS 'Structured AI file suggestions';
COMMENT ON COLUMN openclaw_skill_ai_edit_record.base_hash IS 'Draft file tree hash at preview time';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_draft ON openclaw_skill_ai_edit_record (draft_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_user ON openclaw_skill_ai_edit_record (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_status ON openclaw_skill_ai_edit_record (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_ai_edit_time ON openclaw_skill_ai_edit_record (create_time);
