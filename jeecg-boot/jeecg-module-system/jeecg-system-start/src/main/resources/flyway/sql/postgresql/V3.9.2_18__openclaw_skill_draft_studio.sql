CREATE TABLE IF NOT EXISTS openclaw_skill_draft (
  id varchar(36) NOT NULL,
  skill_id varchar(36) DEFAULT NULL,
  draft_name varchar(128) NOT NULL,
  skill_slug varchar(128) NOT NULL,
  owner_user_id varchar(36) NOT NULL,
  owner_username varchar(100) DEFAULT NULL,
  status varchar(32) NOT NULL DEFAULT 'editing',
  description text DEFAULT NULL,
  base_version varchar(64) DEFAULT NULL,
  draft_path varchar(500) NOT NULL,
  last_lint_status varchar(32) DEFAULT NULL,
  last_lint_result_json text DEFAULT NULL,
  last_test_status varchar(32) DEFAULT NULL,
  last_test_run_id varchar(36) DEFAULT NULL,
  submit_time timestamp DEFAULT NULL,
  review_status varchar(32) DEFAULT NULL,
  review_comment text DEFAULT NULL,
  reviewed_by varchar(64) DEFAULT NULL,
  reviewed_time timestamp DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill_draft IS 'OpenClaw skill draft';
COMMENT ON COLUMN openclaw_skill_draft.skill_id IS 'Base published skill id';
COMMENT ON COLUMN openclaw_skill_draft.draft_path IS 'Draft file directory';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_owner ON openclaw_skill_draft (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_skill ON openclaw_skill_draft (skill_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_status ON openclaw_skill_draft (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_slug ON openclaw_skill_draft (skill_slug);

CREATE TABLE IF NOT EXISTS openclaw_skill_draft_file (
  id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  file_path varchar(500) NOT NULL,
  file_type varchar(20) NOT NULL,
  size_bytes bigint DEFAULT 0,
  checksum varchar(128) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill_draft_file IS 'OpenClaw skill draft file';
COMMENT ON COLUMN openclaw_skill_draft_file.file_path IS 'Relative file path';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_openclaw_skill_draft_file'
  ) THEN
    ALTER TABLE openclaw_skill_draft_file
      ADD CONSTRAINT uk_openclaw_skill_draft_file UNIQUE (draft_id, file_path);
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_draft_file_draft ON openclaw_skill_draft_file (draft_id);

INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000011', 'ocp000000000000000000000000001', 'Skill Draft Studio', '/openclaw/skill-drafts', 'openclaw/skill/SkillDraftList', 1, 'OpenclawSkillDraftList', NULL, 1, 'openclaw:skill:draft:list', '1', 3.50, 0, 'ant-design:code-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000206', 'ocp000000000000000000000000011', 'Skill Draft Add', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000207', 'ocp000000000000000000000000011', 'Skill Draft Edit', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000208', 'ocp000000000000000000000000011', 'Skill Draft Lint', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:lint', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
url = EXCLUDED.url,
component = EXCLUDED.component,
is_route = EXCLUDED.is_route,
component_name = EXCLUDED.component_name,
menu_type = EXCLUDED.menu_type,
perms = EXCLUDED.perms,
sort_no = EXCLUDED.sort_no,
icon = EXCLUDED.icon,
update_by = 'admin',
update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT MD5(CONCAT('openclaw-admin-draft-', p.id)), 'oc000000000000000000000000000002', p.id
FROM sys_permission p WHERE p.id IN (
  'ocp000000000000000000000000011',
  'ocp000000000000000000000000206',
  'ocp000000000000000000000000207',
  'ocp000000000000000000000000208'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-emp-draft-011'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000011'),
(MD5('openclaw-emp-draft-206'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000206'),
(MD5('openclaw-emp-draft-207'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000207'),
(MD5('openclaw-emp-draft-208'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000208'),
(MD5('openclaw-reviewer-draft-011'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000011'),
(MD5('openclaw-reviewer-draft-207'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000207')
ON CONFLICT (id) DO NOTHING;
