INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000210', 'ocp000000000000000000000000011', 'Skill Draft Submit', '', '', 0, NULL, NULL, 2, 'openclaw:skill:draft:submit', '1', 5.00, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
perms = EXCLUDED.perms,
update_by = 'admin',
update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-admin-draft-submit-210'), 'oc000000000000000000000000000002', 'ocp000000000000000000000000210'),
(MD5('openclaw-emp-draft-submit-210'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000210')
ON CONFLICT (id) DO NOTHING;
