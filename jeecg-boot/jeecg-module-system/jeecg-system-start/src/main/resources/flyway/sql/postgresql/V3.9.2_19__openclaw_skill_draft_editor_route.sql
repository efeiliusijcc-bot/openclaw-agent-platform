INSERT INTO sys_permission
(id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ocp000000000000000000000000012', 'ocp000000000000000000000000011', 'Skill Draft Editor', '/openclaw/skill-drafts/editor/:id', 'openclaw/skill/SkillEditor', 1, 'OpenclawSkillEditor', NULL, 1, 'openclaw:skill:draft:edit', '1', 1.00, 0, 'ant-design:edit-outlined', 1, 0, 1, 0, NULL, 'admin', NOW(), 0, 0, '1', 0)
ON CONFLICT (id) DO UPDATE SET
parent_id = EXCLUDED.parent_id,
name = EXCLUDED.name,
url = EXCLUDED.url,
component = EXCLUDED.component,
is_route = EXCLUDED.is_route,
component_name = EXCLUDED.component_name,
menu_type = EXCLUDED.menu_type,
perms = EXCLUDED.perms,
hidden = EXCLUDED.hidden,
update_by = 'admin',
update_time = NOW();

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT MD5(CONCAT('openclaw-admin-draft-editor-', p.id)), 'oc000000000000000000000000000002', p.id
FROM sys_permission p WHERE p.id = 'ocp000000000000000000000000012'
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
(MD5('openclaw-emp-draft-editor-012'), 'oc000000000000000000000000000001', 'ocp000000000000000000000000012'),
(MD5('openclaw-reviewer-draft-editor-012'), 'oc000000000000000000000000000003', 'ocp000000000000000000000000012')
ON CONFLICT (id) DO NOTHING;
