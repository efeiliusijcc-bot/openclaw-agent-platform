INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000013', 'ocp000000000000000000000000001', 'System Health', '/openclaw/system-health', 'openclaw/ops/SystemHealth', 1, 'OpenclawSystemHealth', NULL, 1, 'openclaw:system:health', '1', 10.00, 0, 'ant-design:monitor-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
url = EXCLUDED.url,
component = EXCLUDED.component,
component_name = EXCLUDED.component_name,
perms = EXCLUDED.perms,
sort_no = EXCLUDED.sort_no,
icon = EXCLUDED.icon,
update_by = 'admin',
update_time = NOW(),
del_flag = EXCLUDED.del_flag,
status = EXCLUDED.status;

INSERT INTO sys_role_permission (id, role_id, permission_id)
VALUES (md5('openclaw-admin-ocp000000000000000000000000013'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000013')
ON CONFLICT (id) DO NOTHING;
