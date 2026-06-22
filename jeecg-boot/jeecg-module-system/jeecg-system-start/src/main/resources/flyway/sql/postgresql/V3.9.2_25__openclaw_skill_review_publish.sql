CREATE TABLE IF NOT EXISTS openclaw_skill_review (
  id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  version_no integer NOT NULL,
  skill_id varchar(36) DEFAULT NULL,
  workspace_id varchar(36) DEFAULT NULL,
  submitter_id varchar(36) NOT NULL,
  submitter_username varchar(100) DEFAULT NULL,
  reviewer_id varchar(36) DEFAULT NULL,
  reviewer_username varchar(100) DEFAULT NULL,
  status varchar(32) NOT NULL,
  file_snapshot_json text NOT NULL,
  file_hash varchar(64) NOT NULL,
  test_run_id varchar(36) NOT NULL,
  test_report_json text DEFAULT NULL,
  ai_record_ids_json text DEFAULT NULL,
  submit_comment varchar(1000) DEFAULT NULL,
  review_comment varchar(1000) DEFAULT NULL,
  submitted_time timestamp DEFAULT NULL,
  reviewed_time timestamp DEFAULT NULL,
  published_version_no integer DEFAULT NULL,
  published_skill_id varchar(36) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill_review IS 'OpenClaw Skill fixed-version review';
COMMENT ON COLUMN openclaw_skill_review.status IS 'SUBMITTED/APPROVED/REJECTED/CANCELLED';
COMMENT ON COLUMN openclaw_skill_review.file_snapshot_json IS 'Immutable submitted file snapshot JSON';
COMMENT ON COLUMN openclaw_skill_review.test_report_json IS 'Immutable test report JSON';
COMMENT ON COLUMN openclaw_skill_review.ai_record_ids_json IS 'Related AI edit/repair record id list JSON';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_draft_version ON openclaw_skill_review (draft_id, version_no);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_status ON openclaw_skill_review (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_submitter ON openclaw_skill_review (submitter_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_reviewer ON openclaw_skill_review (reviewer_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_test ON openclaw_skill_review (test_run_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_review_skill ON openclaw_skill_review (skill_id);

CREATE TABLE IF NOT EXISTS openclaw_published_skill_version (
  id varchar(36) NOT NULL,
  skill_id varchar(36) NOT NULL,
  review_id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  draft_version_no integer NOT NULL,
  published_version_no integer NOT NULL,
  file_snapshot_json text NOT NULL,
  file_hash varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  published_by varchar(100) DEFAULT NULL,
  published_time timestamp DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT uk_openclaw_published_skill_version UNIQUE (skill_id, published_version_no),
  CONSTRAINT uk_openclaw_published_review UNIQUE (review_id)
);

COMMENT ON TABLE openclaw_published_skill_version IS 'OpenClaw immutable published Skill versions';
COMMENT ON COLUMN openclaw_published_skill_version.status IS 'PUBLISHED/DEPRECATED';
COMMENT ON COLUMN openclaw_published_skill_version.file_snapshot_json IS 'Immutable published file snapshot JSON';

CREATE INDEX IF NOT EXISTS idx_openclaw_published_skill ON openclaw_published_skill_version (skill_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_published_draft ON openclaw_published_skill_version (draft_id, draft_version_no);
CREATE INDEX IF NOT EXISTS idx_openclaw_published_status ON openclaw_published_skill_version (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_published_hash ON openclaw_published_skill_version (file_hash);
