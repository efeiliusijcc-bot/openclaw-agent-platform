INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000108', 'ocp000000000000000000000000003', 'Agent Run Test', '', '', 0, NULL, NULL, 2, 'openclaw:agent:run', '1', 8.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0),
('ocp000000000000000000000000109', 'ocp000000000000000000000000003', 'Agent Chat Stream', '', '', 0, NULL, NULL, 2, 'openclaw:agent:chat', '1', 9.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
perms = EXCLUDED.perms,
sort_no = EXCLUDED.sort_no,
update_by = 'admin',
update_time = NOW(),
del_flag = EXCLUDED.del_flag,
status = EXCLUDED.status;

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(md5('openclaw-admin-ocp000000000000000000000000108'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000108'),
(md5('openclaw-admin-ocp000000000000000000000000109'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000109'),
(md5('openclaw-emp-ocp000000000000000000000000108'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000108'),
(md5('openclaw-emp-ocp000000000000000000000000109'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000109')
ON CONFLICT (id) DO NOTHING;
