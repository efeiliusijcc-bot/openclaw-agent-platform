CREATE TABLE IF NOT EXISTS openclaw_skill_draft (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  skill_id varchar(36) DEFAULT NULL COMMENT 'Base published skill id',
  draft_name varchar(128) NOT NULL COMMENT 'Draft name',
  skill_slug varchar(128) NOT NULL COMMENT 'Skill slug',
  owner_user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  owner_username varchar(100) DEFAULT NULL COMMENT 'Display username',
  status varchar(32) NOT NULL DEFAULT 'editing' COMMENT 'editing/lint_failed/lint_passed/testing/test_failed/test_passed/submitted/approved/rejected/published',
  description text DEFAULT NULL,
  base_version varchar(64) DEFAULT NULL,
  draft_path varchar(500) NOT NULL COMMENT 'Draft file directory',
  last_lint_status varchar(32) DEFAULT NULL,
  last_lint_result_json longtext DEFAULT NULL,
  last_test_status varchar(32) DEFAULT NULL,
  last_test_run_id varchar(36) DEFAULT NULL,
  submit_time datetime DEFAULT NULL,
  review_status varchar(32) DEFAULT NULL,
  review_comment text DEFAULT NULL,
  reviewed_by varchar(64) DEFAULT NULL,
  reviewed_time datetime DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_openclaw_skill_draft_owner (owner_user_id),
  KEY idx_openclaw_skill_draft_skill (skill_id),
  KEY idx_openclaw_skill_draft_status (status),
  KEY idx_openclaw_skill_draft_slug (skill_slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw skill draft';

CREATE TABLE IF NOT EXISTS openclaw_skill_draft_file (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  draft_id varchar(36) NOT NULL COMMENT 'Skill draft id',
  file_path varchar(500) NOT NULL COMMENT 'Relative file path',
  file_type varchar(20) NOT NULL COMMENT 'file/directory',
  size_bytes bigint DEFAULT 0,
  checksum varchar(128) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_skill_draft_file (draft_id, file_path),
  KEY idx_openclaw_skill_draft_file_draft (draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw skill draft file';

INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000011', 'ocp000000000000000000000000001', 'Skill Draft Studio', '/openclaw/skill-drafts', 'openclaw/skill/SkillDraftList', 1, 'OpenclawSkillDraftList', NULL, 1, 'openclaw:skill:draft:list', '1', 3.50, 0, 'ant-design:code-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000206', 'ocp000000000000000000000000011', 'Skill Draft Add', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000207', 'ocp000000000000000000000000011', 'Skill Draft Edit', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000208', 'ocp000000000000000000000000011', 'Skill Draft Lint', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:lint', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT MD5(CONCAT('openclaw-admin-draft-', p.id)), 'oc000000000000000000000000000002', p.id
FROM sys_permission p WHERE p.id IN (
  'ocp000000000000000000000000011',
  'ocp000000000000000000000000206',
  'ocp000000000000000000000000207',
  'ocp000000000000000000000000208'
);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-emp-draft-011'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000011'),
(MD5('openclaw-emp-draft-206'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000206'),
(MD5('openclaw-emp-draft-207'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000207'),
(MD5('openclaw-emp-draft-208'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000208');

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-reviewer-draft-011'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000011'),
(MD5('openclaw-reviewer-draft-207'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000207');
