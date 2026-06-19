INSERT IGNORE INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000011', 'ocp000000000000000000000000002', 'Workspace Health Check', '', '', 0, NULL, NULL, 2, 'openclaw:workspace:health', '1', 1.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000012', 'ocp000000000000000000000000002', 'Workspace Rematerialize', '', '', 0, NULL, NULL, 2, 'openclaw:workspace:rematerialize', '1', 2.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0);

INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-admin-ocp000000000000000000000000011'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000011'),
(MD5('openclaw-admin-ocp000000000000000000000000012'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000012'),
(MD5('openclaw-emp-ocp000000000000000000000000011'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000011'),
(MD5('openclaw-emp-ocp000000000000000000000000012'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000012');
