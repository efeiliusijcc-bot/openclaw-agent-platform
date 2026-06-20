CREATE TABLE IF NOT EXISTS openclaw_skill_test_run (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  draft_id varchar(36) NOT NULL COMMENT 'Skill draft id',
  skill_slug varchar(128) NOT NULL COMMENT 'Skill slug',
  user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  username varchar(100) DEFAULT NULL COMMENT 'Display username',
  status varchar(32) NOT NULL DEFAULT 'running' COMMENT 'running/success/failed/timeout/cancelled',
  prompt longtext NOT NULL,
  expected_output longtext DEFAULT NULL,
  output_summary longtext DEFAULT NULL,
  error_message longtext DEFAULT NULL,
  workspace_path varchar(500) DEFAULT NULL,
  start_time datetime DEFAULT NULL,
  finish_time datetime DEFAULT NULL,
  duration_ms bigint DEFAULT NULL,
  agent_run_id varchar(36) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_openclaw_skill_test_draft (draft_id),
  KEY idx_openclaw_skill_test_user (user_id),
  KEY idx_openclaw_skill_test_status (status),
  KEY idx_openclaw_skill_test_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw skill draft test run';

INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000209', 'ocp000000000000000000000000011', 'Skill Draft Test', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:test', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-admin-draft-test-209'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000209'),
(MD5('openclaw-emp-draft-test-209'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000209');
