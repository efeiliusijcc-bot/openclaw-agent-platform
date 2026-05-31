ALTER TABLE openclaw_agent_run
  ADD COLUMN conversation_id varchar(64) DEFAULT NULL COMMENT 'Conversation id for chat turns',
  ADD COLUMN run_type varchar(20) NOT NULL DEFAULT 'test' COMMENT 'test/chat',
  ADD COLUMN streaming tinyint NOT NULL DEFAULT 0 COMMENT 'Whether this run used streaming output',
  ADD COLUMN model varchar(100) DEFAULT NULL COMMENT 'Gateway model id used for this run';

CREATE INDEX idx_openclaw_run_conversation ON openclaw_agent_run (conversation_id);
CREATE INDEX idx_openclaw_run_type ON openclaw_agent_run (run_type);
