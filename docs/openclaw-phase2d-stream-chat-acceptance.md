# OpenClaw Phase 2D Stream Chat Acceptance

## Scope

Phase 2D adds the first JeecgBoot-to-Agent chat loop:

- Agent list provides a "实时对话" action.
- Frontend calls `POST /jeecg-boot/openclaw/agent/{id}/chat/stream`.
- Backend validates ownership, quota, prompt, and Agent status.
- Backend creates an `openclaw_agent_run` row with `run_type=chat` and `streaming=1`.
- Backend streams output to the browser using SSE events.
- Backend writes final status, output, duration, model, and conversation id back to `openclaw_agent_run`.

This phase does not add Gateway Pool, Redis queue, complex scheduling, OpenClaw Control UI changes, or Keycloak SSO changes.

## Deployment

- JeecgBoot SSO frontend: `https://agent.test-link.xin`
- Native rollback frontend: `http://43.250.173.37:3100`
- Backend: `http://43.250.173.37:8081/jeecg-boot`
- PostgreSQL container: `auth-postgres`
- Database: `openclaw_platform`
- Gateway config strategy remains `/root/.openclaw/jeecg-agents.json` include.

## Database Migration

Migration file:

- `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/postgresql/V3.9.2_3__openclaw_agent_chat_stream.sql`
- `jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.2_3__openclaw_agent_chat_stream.sql`

Verified columns on cloud PostgreSQL:

- `conversation_id`
- `run_type`
- `streaming`
- `model`

## Gateway Call Path

The backend first supports OpenAI-compatible HTTP streaming through:

- `{gatewayBaseUrl}/v1/chat/completions`
- model: `openclaw/{agentKey}`

The current cloud Gateway is exposed to JeecgBoot as a WebSocket Gateway:

- `OPENCLAW_GATEWAY_URL=ws://172.20.0.1:18790`

For this deployment, the stream endpoint uses an internal CLI-compatible fallback for WebSocket Gateway addresses. The browser API and JeecgBoot SSE contract stay the same. The fallback sends the final Agent output as an SSE `delta`, then writes the run as `success`.

## SSE Events

Backend emits:

- `run_created`: `runId`, `conversationId`, `agentKey`, `status=running`
- `delta`: output text
- `done`: final status, output summary, duration
- `error`: failure details
- `timeout`: timeout details

## Cloud Acceptance Result

Validated cloud run:

- Request prompt: `Reply with exactly: OK`
- Agent id: `2057384279284858882`
- Agent key: `agt_2057384278555049985`
- Run id: `2059944336296628225`
- Status: `success`
- Run type: `chat`
- Streaming: `1`
- Model: `openclaw/agt_2057384278555049985`
- Output summary: `OK`
- Duration: `24649ms`

Database query confirmed:

```text
2059944336296628225|success|chat|1|openclaw/agt_2057384278555049985|OK||24649
```

## Frontend Acceptance

Verified:

- Agent list shows "实时对话".
- Chat modal accepts prompt.
- Backend SSE endpoint returns run id and conversation id.
- Output area displays `OK`.
- Final persisted run status is `success`.
- Agent Runs can display `runType`, `conversationId`, `model`, and `streaming`.

## Nginx Streaming Config

Auth Nginx includes a dedicated route for:

```text
/jeecg-boot/openclaw/agent/{id}/chat/stream
```

The route proxies to `JEECG_BACKEND_UPSTREAM` and disables proxy buffering with long read/send timeouts.

## Known Limitations

- The current cloud Gateway did not expose a working OpenAI-compatible HTTP streaming endpoint for this Agent path.
- The first version keeps JeecgBoot's SSE contract but falls back to OpenClaw CLI-compatible execution when the Gateway base URL is `ws://` or `wss://`.
- Token-by-token streaming depends on a future direct Gateway WebSocket adapter or a confirmed HTTP streaming Gateway endpoint.
- Agent Run execution and stream output are single-node only; no Gateway Pool or queue is implemented.
