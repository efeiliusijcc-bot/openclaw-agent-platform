ALTER TABLE openclaw_gateway_node
  ADD COLUMN IF NOT EXISTS config_path varchar(500) DEFAULT '/data/openclaw-gateway/.openclaw/jeecg-agents.json',
  ADD COLUMN IF NOT EXISTS workspace_root varchar(500) DEFAULT '/data/openclaw-platform/workspaces',
  ADD COLUMN IF NOT EXISTS last_sync_time timestamp DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS last_sync_status varchar(20) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS last_sync_message text DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS last_sync_checksum varchar(128) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS restart_required smallint NOT NULL DEFAULT 1;

COMMENT ON COLUMN openclaw_gateway_node.config_path IS 'JeecgBoot generated OpenClaw agents include file path';
COMMENT ON COLUMN openclaw_gateway_node.workspace_root IS 'Workspace root visible to OpenClaw Gateway';
COMMENT ON COLUMN openclaw_gateway_node.last_sync_time IS 'Last gateway config sync time';
COMMENT ON COLUMN openclaw_gateway_node.last_sync_status IS 'success/failed';
COMMENT ON COLUMN openclaw_gateway_node.last_sync_message IS 'Last gateway config sync message';
COMMENT ON COLUMN openclaw_gateway_node.last_sync_checksum IS 'SHA-256 of generated config';
COMMENT ON COLUMN openclaw_gateway_node.restart_required IS 'Whether Gateway restart is required';

UPDATE openclaw_gateway_node
SET config_path = '/data/openclaw-gateway/.openclaw/jeecg-agents.json'
WHERE config_path IS NULL OR config_path = '';

UPDATE openclaw_gateway_node
SET workspace_root = '/data/openclaw-platform/workspaces'
WHERE workspace_root IS NULL OR workspace_root = '';

INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000404', 'ocp000000000000000000000000009', 'Gateway Sync', '', '', 0, NULL, NULL, 2, 'openclaw:gateway:sync', '1', 4.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
perms = EXCLUDED.perms,
sort_no = EXCLUDED.sort_no,
update_by = 'admin',
update_time = NOW(),
del_flag = EXCLUDED.del_flag,
status = EXCLUDED.status;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (md5('openclaw-admin-ocp000000000000000000000000404'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000404')
ON CONFLICT (id) DO NOTHING;
