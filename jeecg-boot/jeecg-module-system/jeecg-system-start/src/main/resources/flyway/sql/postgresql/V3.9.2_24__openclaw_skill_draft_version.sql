CREATE TABLE IF NOT EXISTS openclaw_skill_draft_version (
  id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  version_no integer NOT NULL,
  source_type varchar(32) NOT NULL,
  source_record_id varchar(36) DEFAULT NULL,
  test_run_id varchar(36) DEFAULT NULL,
  file_snapshot text NOT NULL,
  file_hash varchar(64) NOT NULL,
  summary varchar(1000) DEFAULT NULL,
  lint_status varchar(32) DEFAULT NULL,
  test_status varchar(32) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_openclaw_skill_draft_version_no UNIQUE (draft_id, version_no)
);

COMMENT ON TABLE openclaw_skill_draft_version IS 'OpenClaw Skill draft version snapshots';
COMMENT ON COLUMN openclaw_skill_draft_version.draft_id IS 'Skill draft id';
COMMENT ON COLUMN openclaw_skill_draft_version.version_no IS 'Draft version number';
COMMENT ON COLUMN openclaw_skill_draft_version.source_type IS 'manual/ai_edit/ai_repair/rollback';
COMMENT ON COLUMN openclaw_skill_draft_version.source_record_id IS 'AI edit/repair record id or rollback source version id';
COMMENT ON COLUMN openclaw_skill_draft_version.test_run_id IS 'Related test run id';
COMMENT ON COLUMN openclaw_skill_draft_version.file_snapshot IS 'Full draft file snapshot JSON';
COMMENT ON COLUMN openclaw_skill_draft_version.file_hash IS 'SHA-256 hash of draft files';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_version_draft ON openclaw_skill_draft_version (draft_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_version_source ON openclaw_skill_draft_version (source_type, source_record_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_version_test ON openclaw_skill_draft_version (test_run_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_version_hash ON openclaw_skill_draft_version (file_hash);

ALTER TABLE openclaw_skill_test_run
  ADD COLUMN draft_version_no integer DEFAULT NULL,
  ADD COLUMN file_hash varchar(64) DEFAULT NULL;

COMMENT ON COLUMN openclaw_skill_test_run.draft_version_no IS 'Bound draft version number at test start';
COMMENT ON COLUMN openclaw_skill_test_run.file_hash IS 'Bound draft file hash at test start';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_version ON openclaw_skill_test_run (draft_id, draft_version_no);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_file_hash ON openclaw_skill_test_run (file_hash);
