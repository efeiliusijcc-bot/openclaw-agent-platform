CREATE TABLE IF NOT EXISTS openclaw_skill_draft_version (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  draft_id varchar(36) NOT NULL COMMENT 'Skill draft id',
  version_no int NOT NULL COMMENT 'Draft version number',
  source_type varchar(32) NOT NULL COMMENT 'manual/ai_edit/ai_repair/rollback',
  source_record_id varchar(36) DEFAULT NULL COMMENT 'AI edit/repair record id or rollback source version id',
  test_run_id varchar(36) DEFAULT NULL COMMENT 'Related test run id',
  file_snapshot longtext NOT NULL COMMENT 'Full draft file snapshot JSON',
  file_hash varchar(64) NOT NULL COMMENT 'SHA-256 hash of draft files',
  summary varchar(1000) DEFAULT NULL COMMENT 'Version summary',
  lint_status varchar(32) DEFAULT NULL COMMENT 'lint_passed/lint_failed',
  test_status varchar(32) DEFAULT NULL COMMENT 'success/failed',
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_skill_draft_version_no (draft_id, version_no),
  KEY idx_openclaw_skill_draft_version_draft (draft_id),
  KEY idx_openclaw_skill_draft_version_source (source_type, source_record_id),
  KEY idx_openclaw_skill_draft_version_test (test_run_id),
  KEY idx_openclaw_skill_draft_version_hash (file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw Skill draft version snapshots';

ALTER TABLE openclaw_skill_test_run
  ADD COLUMN draft_version_no int DEFAULT NULL COMMENT 'Bound draft version number at test start' AFTER output_summary,
  ADD COLUMN file_hash varchar(64) DEFAULT NULL COMMENT 'Bound draft file hash at test start' AFTER draft_version_no;

CREATE INDEX idx_openclaw_skill_test_version ON openclaw_skill_test_run (draft_id, draft_version_no);
CREATE INDEX idx_openclaw_skill_test_file_hash ON openclaw_skill_test_run (file_hash);
