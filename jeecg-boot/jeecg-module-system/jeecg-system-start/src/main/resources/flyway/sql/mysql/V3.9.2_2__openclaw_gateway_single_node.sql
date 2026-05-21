ALTER TABLE openclaw_gateway_node
  ADD COLUMN config_path varchar(500) DEFAULT '/data/openclaw-gateway/.openclaw/jeecg-agents.json' COMMENT 'JeecgBoot generated OpenClaw agents include file path',
  ADD COLUMN workspace_root varchar(500) DEFAULT '/data/openclaw-platform/workspaces' COMMENT 'Workspace root visible to OpenClaw Gateway',
  ADD COLUMN last_sync_time datetime DEFAULT NULL COMMENT 'Last gateway config sync time',
  ADD COLUMN last_sync_status varchar(20) DEFAULT NULL COMMENT 'success/failed',
  ADD COLUMN last_sync_message longtext DEFAULT NULL COMMENT 'Last gateway config sync message',
  ADD COLUMN last_sync_checksum varchar(128) DEFAULT NULL COMMENT 'SHA-256 of generated config',
  ADD COLUMN restart_required tinyint NOT NULL DEFAULT 1 COMMENT 'Whether Gateway restart is required';

UPDATE openclaw_gateway_node
SET config_path = '/data/openclaw-gateway/.openclaw/jeecg-agents.json'
WHERE config_path IS NULL OR config_path = '';

UPDATE openclaw_gateway_node
SET workspace_root = '/data/openclaw-platform/workspaces'
WHERE workspace_root IS NULL OR workspace_root = '';

INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000404', 'ocp000000000000000000000000009', 'Gateway Sync', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:sync', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
VALUES (MD5('openclaw-admin-ocp000000000000000000000000404'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000404');
