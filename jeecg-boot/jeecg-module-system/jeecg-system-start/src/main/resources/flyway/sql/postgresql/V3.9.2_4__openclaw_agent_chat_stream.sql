ALTER TABLE openclaw_agent_run
  ADD COLUMN IF NOT EXISTS conversation_id varchar(64) DEFAULT NULL,
  ADD COLUMN IF NOT EXISTS run_type varchar(20) NOT NULL DEFAULT 'test',
  ADD COLUMN IF NOT EXISTS streaming smallint NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS model varchar(100) DEFAULT NULL;

COMMENT ON COLUMN openclaw_agent_run.conversation_id IS 'Conversation id for chat turns';
COMMENT ON COLUMN openclaw_agent_run.run_type IS 'test/chat';
COMMENT ON COLUMN openclaw_agent_run.streaming IS 'Whether this run used streaming output';
COMMENT ON COLUMN openclaw_agent_run.model IS 'Gateway model id used for this run';

CREATE INDEX IF NOT EXISTS idx_openclaw_run_conversation ON openclaw_agent_run (conversation_id);
CREATE INDEX IF NOT EXISTS idx_openclaw_run_type ON openclaw_agent_run (run_type);
