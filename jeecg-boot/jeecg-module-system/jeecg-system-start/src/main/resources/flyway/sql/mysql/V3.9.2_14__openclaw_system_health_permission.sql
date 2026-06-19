INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000013', 'ocp000000000000000000000000001', '系统健康检查', '/openclaw/system-health', 'openclaw/ops/SystemHealth', 1, 'OpenclawSystemHealth', NULL, 1, 'openclaw:system:health', '1', 10.00, 0, 'ant-design:monitor-outlined', 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
VALUES (MD5('openclaw-admin-ocp000000000000000000000000013'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000013');
