CREATE TABLE IF NOT EXISTS openclaw_skill_test_run (
  id varchar(36) NOT NULL,
  draft_id varchar(36) NOT NULL,
  skill_slug varchar(128) NOT NULL,
  user_id varchar(36) NOT NULL,
  username varchar(100) DEFAULT NULL,
  status varchar(32) NOT NULL DEFAULT 'running',
  prompt text NOT NULL,
  expected_output text DEFAULT NULL,
  output_summary text DEFAULT NULL,
  error_message text DEFAULT NULL,
  workspace_path varchar(500) DEFAULT NULL,
  start_time timestamp DEFAULT NULL,
  finish_time timestamp DEFAULT NULL,
  duration_ms bigint DEFAULT NULL,
  agent_run_id varchar(36) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill_test_run IS 'OpenClaw skill draft test run';

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_draft ON openclaw_skill_test_run (draft_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_user ON openclaw_skill_test_run (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_status ON openclaw_skill_test_run (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_test_time ON openclaw_skill_test_run (create_time);

INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000209', 'ocp000000000000000000000000011', 'Skill Draft Test', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:test', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
perms = EXCLUDED.perms,
update_by = 'admin',
update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-admin-draft-test-209'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000209'),
(MD5('openclaw-emp-draft-test-209'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000209')
ON CONFLICT (id) DO NOTHING;
