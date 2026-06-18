CREATE INDEX IF NOT EXISTS idx_openclaw_agent_user_status_del ON openclaw_agent (user_id, status, del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_agent_del_flag ON openclaw_agent (del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_agent_create_time ON openclaw_agent (create_time);

CREATE INDEX IF NOT EXISTS idx_openclaw_workspace_user_status_del ON openclaw_workspace (user_id, status, del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_workspace_del_flag ON openclaw_workspace (del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_workspace_create_time ON openclaw_workspace (create_time);

CREATE INDEX IF NOT EXISTS idx_openclaw_skill_slug ON openclaw_skill (slug);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_owner_status_del ON openclaw_skill (owner_user_id, status, del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_del_flag ON openclaw_skill (del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_skill_create_time ON openclaw_skill (create_time);

CREATE INDEX IF NOT EXISTS idx_openclaw_as_enabled_del ON openclaw_agent_skill (enabled, del_flag);
CREATE INDEX IF NOT EXISTS idx_openclaw_as_create_time ON openclaw_agent_skill (create_time);

CREATE INDEX IF NOT EXISTS idx_openclaw_run_agent_status_time ON openclaw_agent_run (agent_id, status, create_time);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_user_status_time ON openclaw_agent_run (user_id, status, create_time);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_type_status ON openclaw_agent_run (run_type, status);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_error_type ON openclaw_agent_run (error_type);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_del_flag ON openclaw_agent_run (del_flag);

CREATE INDEX IF NOT EXISTS idx_openclaw_gateway_sync_status ON openclaw_gateway_node (last_sync_status, last_sync_time);
CREATE INDEX IF NOT EXISTS idx_openclaw_gateway_del_flag ON openclaw_gateway_node (del_flag);

CREATE INDEX IF NOT EXISTS idx_openclaw_audit_result_time ON openclaw_audit_log (result, create_time);
CREATE INDEX IF NOT EXISTS idx_openclaw_audit_action_result_time ON openclaw_audit_log (action, result, create_time);
