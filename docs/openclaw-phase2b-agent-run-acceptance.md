# OpenClaw Phase 2B Agent Run Acceptance

## 1. Phase Goal

Phase 2B goal is to close the minimum execution loop:

1. JeecgBoot can trigger a real OpenClaw agent run.
2. Run result is returned to UI/API caller.
3. Run record is written back into `openclaw_agent_run`.

This phase intentionally does not include Gateway Pool, Keycloak SSO, async queue, or streaming logs.

## 2. Related Commit

- `5c3b4d7d` `feat: run openclaw agent tests from jeecg`

## 3. Deployment and Endpoints

- Frontend: `http://43.250.173.37:3100/`
- Backend: `http://43.250.173.37:8081/jeecg-boot`

Phase 2B main API:

1. `POST /openclaw/agent/{id}/run-test`
2. `GET /openclaw/run/list?pageNo=1&pageSize=10`

## 4. Primary Test Data

- Admin user: `admin / 123456`
- Employee users: `ceshi / 123456`, `jeecg / 123456`
- Verified agent id (phase 2B baseline):
  - `2057150475727257601` (`Codex Agent B 20260521012208`)

## 5. Core Run Verification (Baseline)

Verified on **2026-05-22**:

1. Request:
   - `POST /jeecg-boot/openclaw/agent/2057150475727257601/run-test`
   - `prompt = "Reply with exactly: OK"`
2. Response:
   - `runId = 2057644883518550017`
   - `status = success`
   - `outputSummary = OK`
   - `durationMs = 14817`
3. Run list check:
   - `GET /jeecg-boot/openclaw/run/list?pageNo=1&pageSize=10`
   - same run id exists
   - `status = success`
   - `startTime = 2026-05-22 10:09:04`
   - `finishTime = 2026-05-22 10:09:18`

## 6. Phase 2B Acceptance Cases

### Case 1: Employee can run own agent

- Date: `2026-05-22`
- User: `ceshi`
- Agent: `2057384279284858882` (`test1`)
- Prompt: `Reply with exactly: OWN_OK`
- Result: **PASS**
  - `runId = 2057761309101256705`
  - `status = success`
  - `outputSummary = OWN_OK`

### Case 2: Employee cannot run another employee's agent

- Date: `2026-05-22`
- User: `ceshi`
- Target agent owned by `jeecg`: `2057150475727257601`
- Result: **PASS**
  - API returned failure message: `无权操作该 OpenClaw 资源`

### Case 3: Disabled agent cannot run

- Date: `2026-05-22`
- Test agent: `2057761551129374721` (`disabled-case-20260522`)
- Result: **PASS**
  - API returned failure message: `Agent is disabled`

### Case 4: Gateway unavailable should write failed run record

- Result: **PENDING (manual env cutover required)**
- Validation method:
  1. Stop OpenClaw Gateway process or set backend `OPENCLAW_GATEWAY_URL` to an unreachable address.
  2. Call `POST /openclaw/agent/{id}/run-test`.
  3. Verify `openclaw_agent_run.status = failed`.
  4. Verify `error_message` contains connection error.

### Case 5: Empty prompt is rejected

- Date: `2026-05-22`
- Request prompt: whitespace-only
- Result: **PASS**
  - API returned failure message: `Prompt cannot be empty`

### Case 6: Failed run exposes error_message

- Date: `2026-05-22`
- Unsynced test agent: `2057761723087450114` (`fail-case-unsynced-20260522`)
- Result: **PASS**
  - `runId = 2057761765672218625`
  - `status = failed`
  - `errorMessage = Error: Unknown agent id ...`
  - record can be queried in run list

## 7. Run Record Writeback Summary

For successful run:

- `status = success`
- `output_summary` filled
- `finish_time` filled
- `duration_ms` filled

For failed run:

- `status = failed`
- `error_message` filled
- `finish_time` filled
- `duration_ms` filled

For timeout run (code added in this phase closeout):

- `status = timeout`
- `error_message` includes timeout text

## 8. Unfinished Items

1. Gateway pool not implemented.
2. Keycloak header auto-login not implemented.
3. oauth2-proxy fronting JeecgBoot not implemented.
4. Async run queue not implemented.
5. WebSocket streaming logs not implemented.
6. Agent run log backfill from Gateway execution pipeline (full run lifecycle) not implemented.
