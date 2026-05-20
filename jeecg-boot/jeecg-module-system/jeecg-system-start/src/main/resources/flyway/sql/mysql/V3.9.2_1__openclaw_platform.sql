CREATE TABLE IF NOT EXISTS openclaw_user_quota (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  username varchar(100) NOT NULL COMMENT 'Display username',
  max_agents int NOT NULL DEFAULT 5 COMMENT 'Max agents',
  max_workspaces int NOT NULL DEFAULT 5 COMMENT 'Max workspaces',
  max_skills int NOT NULL DEFAULT 20 COMMENT 'Max skills',
  max_storage_mb int NOT NULL DEFAULT 1024 COMMENT 'Max storage MB',
  max_daily_runs int NOT NULL DEFAULT 100 COMMENT 'Max daily runs',
  max_concurrent_runs int NOT NULL DEFAULT 2 COMMENT 'Max concurrent runs',
  status varchar(20) NOT NULL DEFAULT 'enabled' COMMENT 'enabled/disabled',
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_quota_user (user_id),
  KEY idx_openclaw_quota_username (username),
  KEY idx_openclaw_quota_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw user quota';

CREATE TABLE IF NOT EXISTS openclaw_workspace (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  username varchar(100) NOT NULL COMMENT 'Display username',
  name varchar(100) NOT NULL COMMENT 'Workspace name',
  workspace_key varchar(100) NOT NULL COMMENT 'Backend generated workspace key',
  path varchar(500) NOT NULL COMMENT 'Backend generated path',
  quota_size_mb int NOT NULL DEFAULT 1024,
  used_size_mb int NOT NULL DEFAULT 0,
  status varchar(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled/deleted',
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_workspace_key (workspace_key),
  KEY idx_openclaw_workspace_user (user_id),
  KEY idx_openclaw_workspace_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw workspace';

CREATE TABLE IF NOT EXISTS openclaw_agent (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  username varchar(100) NOT NULL COMMENT 'Display username',
  workspace_id varchar(36) NOT NULL COMMENT 'Dedicated workspace id',
  agent_key varchar(100) NOT NULL COMMENT 'Backend generated global unique key',
  name varchar(100) NOT NULL COMMENT 'Agent name',
  description text DEFAULT NULL,
  status varchar(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/active/running/stopped/disabled',
  max_skills int NOT NULL DEFAULT 10,
  max_daily_runs int NOT NULL DEFAULT 100,
  config_json longtext DEFAULT NULL,
  gateway_id varchar(36) DEFAULT NULL COMMENT 'Reserved gateway id; no scheduling in phase 1',
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_agent_key (agent_key),
  UNIQUE KEY uk_openclaw_agent_workspace (workspace_id),
  KEY idx_openclaw_agent_user (user_id),
  KEY idx_openclaw_agent_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw agent';

CREATE TABLE IF NOT EXISTS openclaw_skill (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  owner_user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  owner_username varchar(100) NOT NULL COMMENT 'Display username',
  name varchar(100) NOT NULL COMMENT 'Skill name',
  slug varchar(100) NOT NULL COMMENT 'Backend generated or validated slug',
  version varchar(50) NOT NULL DEFAULT '1.0.0',
  scope varchar(20) NOT NULL DEFAULT 'private' COMMENT 'private/team/global',
  status varchar(30) NOT NULL DEFAULT 'draft' COMMENT 'draft/private/pending_review/published/rejected/disabled',
  description text DEFAULT NULL,
  path varchar(500) DEFAULT NULL COMMENT 'Backend generated path',
  checksum varchar(128) DEFAULT NULL,
  file_size bigint DEFAULT 0,
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_skill_owner_slug_ver (owner_user_id, slug, version),
  KEY idx_openclaw_skill_owner (owner_user_id),
  KEY idx_openclaw_skill_scope (scope),
  KEY idx_openclaw_skill_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw skill';

CREATE TABLE IF NOT EXISTS openclaw_agent_skill (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  agent_id varchar(36) NOT NULL COMMENT 'Agent id',
  skill_id varchar(36) NOT NULL COMMENT 'Skill id',
  enabled tinyint NOT NULL DEFAULT 1,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_openclaw_agent_skill (agent_id, skill_id),
  KEY idx_openclaw_as_agent (agent_id),
  KEY idx_openclaw_as_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw agent skill binding';

CREATE TABLE IF NOT EXISTS openclaw_agent_run (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  user_id varchar(36) NOT NULL COMMENT 'sys_user.id',
  username varchar(100) NOT NULL COMMENT 'Display username',
  agent_id varchar(36) NOT NULL COMMENT 'Agent id',
  agent_name varchar(100) NOT NULL COMMENT 'Agent name',
  status varchar(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed/cancelled/timeout',
  input_summary text DEFAULT NULL,
  output_summary text DEFAULT NULL,
  error_message text DEFAULT NULL,
  start_time datetime DEFAULT NULL,
  finish_time datetime DEFAULT NULL,
  duration_ms bigint DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_openclaw_run_user (user_id),
  KEY idx_openclaw_run_agent (agent_id),
  KEY idx_openclaw_run_status (status),
  KEY idx_openclaw_run_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw agent run';

CREATE TABLE IF NOT EXISTS openclaw_gateway_node (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  name varchar(100) NOT NULL COMMENT 'Gateway name',
  base_url varchar(500) NOT NULL COMMENT 'Gateway url',
  status varchar(20) NOT NULL DEFAULT 'offline' COMMENT 'online/offline/disabled',
  max_agents int NOT NULL DEFAULT 10,
  current_agents int NOT NULL DEFAULT 0,
  max_concurrent_runs int NOT NULL DEFAULT 5,
  current_running int NOT NULL DEFAULT 0,
  last_heartbeat datetime DEFAULT NULL,
  remark varchar(500) DEFAULT NULL,
  create_by varchar(50) DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  update_by varchar(50) DEFAULT NULL,
  update_time datetime DEFAULT NULL,
  del_flag tinyint DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_openclaw_gateway_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw gateway node registration only';

CREATE TABLE IF NOT EXISTS openclaw_audit_log (
  id varchar(36) NOT NULL COMMENT 'Primary key',
  user_id varchar(36) DEFAULT NULL COMMENT 'sys_user.id',
  username varchar(100) DEFAULT NULL COMMENT 'Display username',
  action varchar(100) NOT NULL,
  target_type varchar(50) NOT NULL COMMENT 'agent/workspace/skill/quota/gateway',
  target_id varchar(36) DEFAULT NULL,
  ip varchar(100) DEFAULT NULL,
  user_agent varchar(500) DEFAULT NULL,
  detail_json longtext DEFAULT NULL,
  create_time datetime DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_openclaw_audit_user (user_id),
  KEY idx_openclaw_audit_target (target_type, target_id),
  KEY idx_openclaw_audit_action (action),
  KEY idx_openclaw_audit_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenClaw audit log';

INSERT IGNORE INTO sys_role (id, role_name, role_code, description, create_by, create_time, tenant_id) VALUES
('oc000000000000000000000000000001', 'OpenClaw员工', 'openclaw_employee', 'OpenClaw 普通员工角色', 'admin', NOW(), 0),
('oc000000000000000000000000000002', 'OpenClaw管理员', 'openclaw_admin', 'OpenClaw 平台管理员角色', 'admin', NOW(), 0),
('oc000000000000000000000000000003', 'OpenClaw Skill审核员', 'openclaw_skill_reviewer', 'OpenClaw Skill 审核角色', 'admin', NOW(), 0);

INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000001', '', 'OpenClaw平台管理', '/openclaw', 'layouts/default/index', 1, NULL, '/openclaw/agent', 0, NULL, '1', 50.00, 0, 'ant-design:robot-outlined', 0, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000002', 'ocp000000000000000000000000001', '我的工作区', '/openclaw/workspace', 'openclaw/workspace/WorkspaceList', 1, 'OpenclawWorkspaceList', NULL, 1, 'openclaw:workspace:list', '1', 1.00, 0, 'ant-design:folder-open-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000003', 'ocp000000000000000000000000001', '我的Agent', '/openclaw/agent', 'openclaw/agent/AgentList', 1, 'OpenclawAgentList', NULL, 1, 'openclaw:agent:list', '1', 2.00, 0, 'ant-design:deployment-unit-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000004', 'ocp000000000000000000000000001', '我的Skill', '/openclaw/skill', 'openclaw/skill/SkillList', 1, 'OpenclawSkillList', NULL, 1, 'openclaw:skill:list', '1', 3.00, 0, 'ant-design:tool-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000005', 'ocp000000000000000000000000001', 'Skill导入导出', '/openclaw/skill-io', 'openclaw/skill/SkillImportExport', 1, 'OpenclawSkillImportExport', NULL, 1, 'openclaw:skill:import', '1', 4.00, 0, 'ant-design:import-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000006', 'ocp000000000000000000000000001', 'Agent运行记录', '/openclaw/run', 'openclaw/run/AgentRunList', 1, 'OpenclawAgentRunList', NULL, 1, 'openclaw:run:list', '1', 5.00, 0, 'ant-design:history-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000007', 'ocp000000000000000000000000001', '用户配额管理', '/openclaw/quota', 'openclaw/quota/QuotaList', 1, 'OpenclawQuotaList', NULL, 1, 'openclaw:quota:list', '1', 6.00, 0, 'ant-design:dashboard-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000008', 'ocp000000000000000000000000001', 'Skill审核管理', '/openclaw/skill-review', 'openclaw/skill/SkillReviewList', 1, 'OpenclawSkillReviewList', NULL, 1, 'openclaw:skill:review', '1', 7.00, 0, 'ant-design:audit-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000009', 'ocp000000000000000000000000001', 'Gateway节点管理', '/openclaw/gateway', 'openclaw/gateway/GatewayNodeList', 1, 'OpenclawGatewayNodeList', NULL, 1, 'openclaw:gateway:list', '1', 8.00, 0, 'ant-design:cloud-server-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000010', 'ocp000000000000000000000000001', '系统审计日志', '/openclaw/audit', 'openclaw/audit/AuditLogList', 1, 'OpenclawAuditLogList', NULL, 1, 'openclaw:audit:list', '1', 9.00, 0, 'ant-design:file-search-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000101', 'ocp000000000000000000000000003', 'Agent新增', '', '', 0, NULL, NULL, 2, 'openclaw:agent:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000102', 'ocp000000000000000000000000003', 'Agent编辑', '', '', 0, NULL, NULL, 2, 'openclaw:agent:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000103', 'ocp000000000000000000000000003', 'Agent删除', '', '', 0, NULL, NULL, 2, 'openclaw:agent:delete', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000104', 'ocp000000000000000000000000003', 'Agent禁用', '', '', 0, NULL, NULL, 2, 'openclaw:agent:disable', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000105', 'ocp000000000000000000000000003', '绑定Skill', '', '', 0, NULL, NULL, 2, 'openclaw:agent:bindSkill', '1', 5.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000106', 'ocp000000000000000000000000003', '解绑Skill', '', '', 0, NULL, NULL, 2, 'openclaw:agent:unbindSkill', '1', 6.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000201', 'ocp000000000000000000000000004', 'Skill新增', '', '', 0, NULL, NULL, 2, 'openclaw:skill:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000202', 'ocp000000000000000000000000004', 'Skill编辑', '', '', 0, NULL, NULL, 2, 'openclaw:skill:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000203', 'ocp000000000000000000000000004', 'Skill删除', '', '', 0, NULL, NULL, 2, 'openclaw:skill:delete', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000204', 'ocp000000000000000000000000004', 'Skill导出', '', '', 0, NULL, NULL, 2, 'openclaw:skill:export', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000205', 'ocp000000000000000000000000004', 'Skill禁用', '', '', 0, NULL, NULL, 2, 'openclaw:skill:disable', '1', 5.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000301', 'ocp000000000000000000000000007', '配额编辑', '', '', 0, NULL, NULL, 2, 'openclaw:quota:edit', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000302', 'ocp000000000000000000000000007', '我的配额', '', '', 0, NULL, NULL, 2, 'openclaw:quota:my', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000401', 'ocp000000000000000000000000009', 'Gateway新增', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:add', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000402', 'ocp000000000000000000000000009', 'Gateway编辑', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:edit', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000403', 'ocp000000000000000000000000009', 'Gateway禁用', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:disable', '1', 3.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT MD5(CONCAT('openclaw-admin-', p.id)), 'oc000000000000000000000000000002', p.id
FROM sys_permission p WHERE p.id LIKE 'ocp%';

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-emp-001'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000001'),
(MD5('openclaw-emp-002'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000002'),
(MD5('openclaw-emp-003'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000003'),
(MD5('openclaw-emp-004'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000004'),
(MD5('openclaw-emp-005'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000005'),
(MD5('openclaw-emp-006'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000006'),
(MD5('openclaw-emp-101'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000101'),
(MD5('openclaw-emp-102'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000102'),
(MD5('openclaw-emp-103'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000103'),
(MD5('openclaw-emp-105'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000105'),
(MD5('openclaw-emp-106'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000106'),
(MD5('openclaw-emp-201'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000201'),
(MD5('openclaw-emp-202'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000202'),
(MD5('openclaw-emp-203'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000203'),
(MD5('openclaw-emp-204'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000204'),
(MD5('openclaw-emp-302'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000302');

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-reviewer-001'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000001'),
(MD5('openclaw-reviewer-004'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000004'),
(MD5('openclaw-reviewer-008'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000008'),
(MD5('openclaw-reviewer-review'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000205');
