CREATE TABLE IF NOT EXISTS openclaw_user_quota (
  id varchar(36) NOT NULL,
  user_id varchar(36) NOT NULL,
  username varchar(100) NOT NULL,
  max_agents integer NOT NULL DEFAULT 5,
  max_workspaces integer NOT NULL DEFAULT 5,
  max_skills integer NOT NULL DEFAULT 20,
  max_storage_mb integer NOT NULL DEFAULT 1024,
  max_daily_runs integer NOT NULL DEFAULT 100,
  max_concurrent_runs integer NOT NULL DEFAULT 2,
  status varchar(20) NOT NULL DEFAULT 'enabled',
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_user_quota IS 'OpenClaw user quota';
COMMENT ON COLUMN openclaw_user_quota.id IS 'Primary key';
COMMENT ON COLUMN openclaw_user_quota.user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_user_quota.username IS 'Display username';
COMMENT ON COLUMN openclaw_user_quota.max_agents IS 'Max agents';
COMMENT ON COLUMN openclaw_user_quota.max_workspaces IS 'Max workspaces';
COMMENT ON COLUMN openclaw_user_quota.max_skills IS 'Max skills';
COMMENT ON COLUMN openclaw_user_quota.max_storage_mb IS 'Max storage MB';
COMMENT ON COLUMN openclaw_user_quota.max_daily_runs IS 'Max daily runs';
COMMENT ON COLUMN openclaw_user_quota.max_concurrent_runs IS 'Max concurrent runs';
COMMENT ON COLUMN openclaw_user_quota.status IS 'enabled/disabled';

CREATE UNIQUE INDEX IF NOT EXISTS uk_openclaw_quota_user ON openclaw_user_quota (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_quota_username ON openclaw_user_quota (username);
CREATE INDEX IF NOT EXISTS idx_openclaw_quota_status ON openclaw_user_quota (status);

CREATE TABLE IF NOT EXISTS openclaw_workspace (
  id varchar(36) NOT NULL,
  user_id varchar(36) NOT NULL,
  username varchar(100) NOT NULL,
  name varchar(100) NOT NULL,
  workspace_key varchar(100) NOT NULL,
  path varchar(500) NOT NULL,
  quota_size_mb integer NOT NULL DEFAULT 1024,
  used_size_mb integer NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'active',
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_workspace IS 'OpenClaw workspace';
COMMENT ON COLUMN openclaw_workspace.id IS 'Primary key';
COMMENT ON COLUMN openclaw_workspace.user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_workspace.username IS 'Display username';
COMMENT ON COLUMN openclaw_workspace.name IS 'Workspace name';
COMMENT ON COLUMN openclaw_workspace.workspace_key IS 'Backend generated workspace key';
COMMENT ON COLUMN openclaw_workspace.path IS 'Backend generated path';
COMMENT ON COLUMN openclaw_workspace.status IS 'active/disabled/deleted';

CREATE UNIQUE INDEX IF NOT EXISTS uk_openclaw_workspace_key ON openclaw_workspace (workspace_key);
CREATE INDEX IF NOT EXISTS idx_openclaw_workspace_user ON openclaw_workspace (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_workspace_status ON openclaw_workspace (status);

CREATE TABLE IF NOT EXISTS openclaw_agent (
  id varchar(36) NOT NULL,
  user_id varchar(36) NOT NULL,
  username varchar(100) NOT NULL,
  workspace_id varchar(36) NOT NULL,
  agent_key varchar(100) NOT NULL,
  name varchar(100) NOT NULL,
  description text DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'draft',
  max_skills integer NOT NULL DEFAULT 10,
  max_daily_runs integer NOT NULL DEFAULT 100,
  config_json text DEFAULT NULL,
  gateway_id varchar(36) DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_agent IS 'OpenClaw agent';
COMMENT ON COLUMN openclaw_agent.id IS 'Primary key';
COMMENT ON COLUMN openclaw_agent.user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_agent.username IS 'Display username';
COMMENT ON COLUMN openclaw_agent.workspace_id IS 'Dedicated workspace id';
COMMENT ON COLUMN openclaw_agent.agent_key IS 'Backend generated global unique key';
COMMENT ON COLUMN openclaw_agent.name IS 'Agent name';
COMMENT ON COLUMN openclaw_agent.status IS 'draft/active/running/stopped/disabled';
COMMENT ON COLUMN openclaw_agent.gateway_id IS 'Reserved gateway id; no scheduling in phase 1';

CREATE UNIQUE INDEX IF NOT EXISTS uk_openclaw_agent_key ON openclaw_agent (agent_key);
CREATE INDEX IF NOT EXISTS idx_openclaw_agent_user ON openclaw_agent (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_agent_status ON openclaw_agent (status);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'uk_openclaw_agent_workspace'
  ) THEN
    ALTER TABLE openclaw_agent
      ADD CONSTRAINT uk_openclaw_agent_workspace UNIQUE (workspace_id);
  END IF;
END
$$;

CREATE TABLE IF NOT EXISTS openclaw_skill (
  id varchar(36) NOT NULL,
  owner_user_id varchar(36) NOT NULL,
  owner_username varchar(100) NOT NULL,
  name varchar(100) NOT NULL,
  slug varchar(100) NOT NULL,
  version varchar(50) NOT NULL DEFAULT '1.0.0',
  scope varchar(20) NOT NULL DEFAULT 'private',
  status varchar(30) NOT NULL DEFAULT 'draft',
  description text DEFAULT NULL,
  path varchar(500) DEFAULT NULL,
  checksum varchar(128) DEFAULT NULL,
  file_size bigint DEFAULT 0,
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_skill IS 'OpenClaw skill';
COMMENT ON COLUMN openclaw_skill.id IS 'Primary key';
COMMENT ON COLUMN openclaw_skill.owner_user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_skill.owner_username IS 'Display username';
COMMENT ON COLUMN openclaw_skill.name IS 'Skill name';
COMMENT ON COLUMN openclaw_skill.slug IS 'Backend generated or validated slug';
COMMENT ON COLUMN openclaw_skill.scope IS 'private/team/global';
COMMENT ON COLUMN openclaw_skill.status IS 'draft/private/pending_review/published/rejected/disabled';
COMMENT ON COLUMN openclaw_skill.path IS 'Backend generated path';

CREATE UNIQUE INDEX IF NOT EXISTS uk_openclaw_skill_owner_slug_ver ON openclaw_skill (owner_user_id, slug, version);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_owner ON openclaw_skill (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_scope ON openclaw_skill (scope);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_status ON openclaw_skill (status);

CREATE TABLE IF NOT EXISTS openclaw_agent_skill (
  id varchar(36) NOT NULL,
  agent_id varchar(36) NOT NULL,
  skill_id varchar(36) NOT NULL,
  enabled smallint NOT NULL DEFAULT 1,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_agent_skill IS 'OpenClaw agent skill binding';
COMMENT ON COLUMN openclaw_agent_skill.id IS 'Primary key';
COMMENT ON COLUMN openclaw_agent_skill.agent_id IS 'Agent id';
COMMENT ON COLUMN openclaw_agent_skill.skill_id IS 'Skill id';

CREATE UNIQUE INDEX IF NOT EXISTS uk_openclaw_agent_skill ON openclaw_agent_skill (agent_id, skill_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_as_agent ON openclaw_agent_skill (agent_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_as_skill ON openclaw_agent_skill (skill_id);

CREATE TABLE IF NOT EXISTS openclaw_agent_run (
  id varchar(36) NOT NULL,
  user_id varchar(36) NOT NULL,
  username varchar(100) NOT NULL,
  agent_id varchar(36) NOT NULL,
  agent_name varchar(100) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'pending',
  input_summary text DEFAULT NULL,
  output_summary text DEFAULT NULL,
  error_message text DEFAULT NULL,
  start_time timestamp DEFAULT NULL,
  finish_time timestamp DEFAULT NULL,
  duration_ms bigint DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_agent_run IS 'OpenClaw agent run';
COMMENT ON COLUMN openclaw_agent_run.id IS 'Primary key';
COMMENT ON COLUMN openclaw_agent_run.user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_agent_run.username IS 'Display username';
COMMENT ON COLUMN openclaw_agent_run.agent_id IS 'Agent id';
COMMENT ON COLUMN openclaw_agent_run.agent_name IS 'Agent name';
COMMENT ON COLUMN openclaw_agent_run.status IS 'pending/running/success/failed/cancelled/timeout';

CREATE INDEX IF NOT EXISTS idx_openclaw_run_user ON openclaw_agent_run (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_agent ON openclaw_agent_run (agent_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_status ON openclaw_agent_run (status);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_time ON openclaw_agent_run (create_time);

CREATE TABLE IF NOT EXISTS openclaw_gateway_node (
  id varchar(36) NOT NULL,
  name varchar(100) NOT NULL,
  base_url varchar(500) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'offline',
  max_agents integer NOT NULL DEFAULT 10,
  current_agents integer NOT NULL DEFAULT 0,
  max_concurrent_runs integer NOT NULL DEFAULT 5,
  current_running integer NOT NULL DEFAULT 0,
  last_heartbeat timestamp DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time timestamp DEFAULT NULL,
  del_flag smallint DEFAULT 0,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_gateway_node IS 'OpenClaw gateway node registration only';
COMMENT ON COLUMN openclaw_gateway_node.id IS 'Primary key';
COMMENT ON COLUMN openclaw_gateway_node.name IS 'Gateway name';
COMMENT ON COLUMN openclaw_gateway_node.base_url IS 'Gateway url';
COMMENT ON COLUMN openclaw_gateway_node.status IS 'online/offline/disabled';

CREATE INDEX IF NOT EXISTS idx_openclaw_gateway_status ON openclaw_gateway_node (status);

CREATE TABLE IF NOT EXISTS openclaw_audit_log (
  id varchar(36) NOT NULL,
  user_id varchar(36) DEFAULT NULL,
  username varchar(100) DEFAULT NULL,
  action varchar(100) NOT NULL,
  target_type varchar(50) NOT NULL,
  target_id varchar(36) DEFAULT NULL,
  ip varchar(100) DEFAULT NULL,
  user_agent varchar(500) DEFAULT NULL,
  detail_json text DEFAULT NULL,
  create_time timestamp DEFAULT NULL,
  PRIMARY KEY (id)
);

COMMENT ON TABLE openclaw_audit_log IS 'OpenClaw audit log';
COMMENT ON COLUMN openclaw_audit_log.id IS 'Primary key';
COMMENT ON COLUMN openclaw_audit_log.user_id IS 'sys_user.id';
COMMENT ON COLUMN openclaw_audit_log.username IS 'Display username';
COMMENT ON COLUMN openclaw_audit_log.target_type IS 'agent/workspace/skill/quota/gateway';

CREATE INDEX IF NOT EXISTS idx_openclaw_audit_user ON openclaw_audit_log (user_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_audit_target ON openclaw_audit_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_audit_action ON openclaw_audit_log (action);
CREATE INDEX IF NOT EXISTS idx_openclaw_audit_time ON openclaw_audit_log (create_time);

INSERT INTO sys_role (id, role_name, role_code, description, create_by, create_time, tenant_id) VALUES
('oc000000000000000000000000000001', 'OpenClaw Employee', 'openclaw_employee', 'OpenClaw employee role', 'admin', NOW(), 0),
('oc000000000000000000000000000002', 'OpenClaw Admin', 'openclaw_admin', 'OpenClaw platform admin role', 'admin', NOW(), 0),
('oc000000000000000000000000000003', 'OpenClaw Skill Reviewer', 'openclaw_skill_reviewer', 'OpenClaw skill reviewer role', 'admin', NOW(), 0)
ON CONFLICT (id) DO UPDATE SET
role_name = EXCLUDED.role_name,
role_code = EXCLUDED.role_code,
description = EXCLUDED.description,
update_by = 'admin',
update_time = NOW();

INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000001', '', 'OpenClaw Platform', '/openclaw', 'layouts/default/index', 1, NULL, '/openclaw/agent', 0, NULL, '1', 50.00, 0, 'ant-design:robot-outlined', 0, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000002', 'ocp000000000000000000000000001', 'My Workspaces', '/openclaw/workspace', 'openclaw/workspace/WorkspaceList', 1, 'OpenclawWorkspaceList', NULL, 1, 'openclaw:workspace:list', '1', 1.00, 0, 'ant-design:folder-open-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000003', 'ocp000000000000000000000000001', 'My Agents', '/openclaw/agent', 'openclaw/agent/AgentList', 1, 'OpenclawAgentList', NULL, 1, 'openclaw:agent:list', '1', 2.00, 0, 'ant-design:deployment-unit-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000004', 'ocp000000000000000000000000001', 'My Skills', '/openclaw/skill', 'openclaw/skill/SkillList', 1, 'OpenclawSkillList', NULL, 1, 'openclaw:skill:list', '1', 3.00, 0, 'ant-design:tool-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000005', 'ocp000000000000000000000000001', 'Skill Import Export', '/openclaw/skill-io', 'openclaw/skill/SkillImportExport', 1, 'OpenclawSkillImportExport', NULL, 1, 'openclaw:skill:import', '1', 4.00, 0, 'ant-design:import-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000006', 'ocp000000000000000000000000001', 'Agent Runs', '/openclaw/run', 'openclaw/run/AgentRunList', 1, 'OpenclawAgentRunList', NULL, 1, 'openclaw:run:list', '1', 5.00, 0, 'ant-design:history-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000007', 'ocp000000000000000000000000001', 'Quota Management', '/openclaw/quota', 'openclaw/quota/QuotaList', 1, 'OpenclawQuotaList', NULL, 1, 'openclaw:quota:list', '1', 6.00, 0, 'ant-design:dashboard-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000008', 'ocp000000000000000000000000001', 'Skill Review', '/openclaw/skill-review', 'openclaw/skill/SkillReviewList', 1, 'OpenclawSkillReviewList', NULL, 1, 'openclaw:skill:review', '1', 7.00, 0, 'ant-design:audit-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000009', 'ocp000000000000000000000000001', 'Gateway Nodes', '/openclaw/gateway', 'openclaw/gateway/GatewayNodeList', 1, 'OpenclawGatewayNodeList', NULL, 1, 'openclaw:gateway:list', '1', 8.00, 0, 'ant-design:cloud-server-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000010', 'ocp000000000000000000000000001', 'Audit Logs', '/openclaw/audit', 'openclaw/audit/AuditLogList', 1, 'OpenclawAuditLogList', NULL, 1, 'openclaw:audit:list', '1', 9.00, 0, 'ant-design:file-search-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000101', 'ocp000000000000000000000000003', 'Agent Add', '', '', 0, NULL, NULL, 2, 'openclaw:agent:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000102', 'ocp000000000000000000000000003', 'Agent Edit', '', '', 0, NULL, NULL, 2, 'openclaw:agent:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000103', 'ocp000000000000000000000000003', 'Agent Delete', '', '', 0, NULL, NULL, 2, 'openclaw:agent:delete', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000104', 'ocp000000000000000000000000003', 'Agent Disable', '', '', 0, NULL, NULL, 2, 'openclaw:agent:disable', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000105', 'ocp000000000000000000000000003', 'Bind Skill', '', '', 0, NULL, NULL, 2, 'openclaw:agent:bindSkill', '1', 5.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000106', 'ocp000000000000000000000000003', 'Unbind Skill', '', '', 0, NULL, NULL, 2, 'openclaw:agent:unbindSkill', '1', 6.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000201', 'ocp000000000000000000000000004', 'Skill Add', '', '', 0, NULL, NULL, 2, 'openclaw:skill:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000202', 'ocp000000000000000000000000004', 'Skill Edit', '', '', 0, NULL, NULL, 2, 'openclaw:skill:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000203', 'ocp000000000000000000000000004', 'Skill Delete', '', '', 0, NULL, NULL, 2, 'openclaw:skill:delete', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000204', 'ocp000000000000000000000000004', 'Skill Export', '', '', 0, NULL, NULL, 2, 'openclaw:skill:export', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000205', 'ocp000000000000000000000000004', 'Skill Disable', '', '', 0, NULL, NULL, 2, 'openclaw:skill:disable', '1', 5.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000301', 'ocp000000000000000000000000007', 'Quota Edit', '', '', 0, NULL, NULL, 2, 'openclaw:quota:edit', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000302', 'ocp000000000000000000000000007', 'My Quota', '', '', 0, NULL, NULL, 2, 'openclaw:quota:my', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000401', 'ocp000000000000000000000000009', 'Gateway Add', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000402', 'ocp000000000000000000000000009', 'Gateway Edit', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000403', 'ocp000000000000000000000000009', 'Gateway Disable', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:disable', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
url = EXCLUDED.url,
component = EXCLUDED.component,
is_route = EXCLUDED.is_route,
component_name = EXCLUDED.component_name,
redirect = EXCLUDED.redirect,
menu_type = EXCLUDED.menu_type,
perms = EXCLUDED.perms,
perms_type = EXCLUDED.perms_type,
sort_no = EXCLUDED.sort_no,
always_show = EXCLUDED.always_show,
icon = EXCLUDED.icon,
is_leaf = EXCLUDED.is_leaf,
keep_alive = EXCLUDED.keep_alive,
hidden = EXCLUDED.hidden,
hide_tab = EXCLUDED.hide_tab,
description = EXCLUDED.description,
update_by = 'admin',
update_time = NOW(),
del_flag = EXCLUDED.del_flag,
rule_flag = EXCLUDED.rule_flag,
status = EXCLUDED.status,
internal_or_external = EXCLUDED.internal_or_external;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT md5('openclaw-admin-' || p.id), 'oc000000000000000000000000000002', p.id
FROM sys_permission p WHERE p.id LIKE 'ocp%'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(md5('openclaw-emp-001'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000001'),
(md5('openclaw-emp-002'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000002'),
(md5('openclaw-emp-003'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000003'),
(md5('openclaw-emp-004'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000004'),
(md5('openclaw-emp-005'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000005'),
(md5('openclaw-emp-006'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000006'),
(md5('openclaw-emp-101'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000101'),
(md5('openclaw-emp-102'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000102'),
(md5('openclaw-emp-103'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000103'),
(md5('openclaw-emp-105'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000105'),
(md5('openclaw-emp-106'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000106'),
(md5('openclaw-emp-201'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000201'),
(md5('openclaw-emp-202'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000202'),
(md5('openclaw-emp-203'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000203'),
(md5('openclaw-emp-204'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000204'),
(md5('openclaw-emp-302'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000302')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(md5('openclaw-reviewer-001'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000001'),
(md5('openclaw-reviewer-004'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000004'),
(md5('openclaw-reviewer-008'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000008'),
(md5('openclaw-reviewer-review'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000205')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT md5('openclaw-user-role-admin-' || u.id), u.id, 'oc000000000000000000000000000002'
FROM sys_user u
WHERE u.username = 'admin'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT md5('openclaw-user-role-employee-' || u.id), u.id, 'oc000000000000000000000000000001'
FROM sys_user u
WHERE u.username IN ('ceshi', 'jeecg', 'zhangsan')
ON CONFLICT (id) DO NOTHING;
