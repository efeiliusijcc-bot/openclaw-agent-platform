# Project Memory

## 2026-06-21 - Skill Draft Closed Loop Productization

- Scope: productize the Skill Draft AI Edit / Test / Repair loop without publishing draft Skills and without creating formal Agent/Skill binding rows for tests.
- Test Report is now standardized on `openclaw_skill_test_run`: `testRunId`, `draftId`, `agentKey`, `status` (`PASSED/FAILED` in the report), `lintStatus`, `gatewayStatus`, `input`, `output`, `error.type`, `error.message`, `error.code`, `logs`, `startedAt`, `finishedAt`, and `durationMs`. The raw internal run status remains `success/failed/timeout` for existing draft workflow compatibility. A report snapshot is persisted in `report_json`, and the report is queryable with `/openclaw/skill/draft/{draftId}/tests/{testRunId}/report`.
- Repair Preview now requires a related `testRunId` or an existing latest test run. It stores suggestions in `openclaw_skill_ai_edit_record` with `record_type=AI_REPAIR`, `test_run_id`, base version/hash, and the pre-repair test status. Repair Apply now requires `recordId`; it no longer trusts frontend-returned file content. It reuses the same action normalization, path whitelist, content limit, base version/hash validation, and file apply logic as AI Edit.
- Repair status tracking: applying a repair records the pre-apply test status; later draft tests update the latest applied AI_REPAIR record with the latest post-repair test status.
- Frontend `SkillEditor.vue` now shows a structured Test Report panel, loads the latest report after test history loads, loads the new report after a test run, applies AI Repair by `recordId`, and keeps a repair-applied modal open with Run Lint Again / Run Test Again actions.
- Added server acceptance script `scripts/openclaw_skill_draft_closed_loop_acceptance.py` covering invoice AI Edit preview/apply/lint, Echo local test passed with report lookup, failed local test plus AI Repair preview/apply, and recent Gateway log checks for `unknown agent id`, `1006`, `abnormal`, and `FailoverError`.
- Database migration added as `V3.9.2_23__openclaw_skill_test_report_repair_record.sql` for both MySQL and PostgreSQL. Current production has historically disabled Flyway in prod; if still disabled, these columns/indexes must be applied manually during deployment.

## 2026-06-21 - Skill Draft AI Edit Action Compatibility Fix

- During the invoice AI Edit acceptance flow, `/openclaw/skill/draft/{id}/ai-edit/preview` failed when the model returned a file suggestion with `action: update`; the backend validator only allowed `upsert/delete` and returned `Unsupported AI edit action: update`.
- Fixed `SkillAiEditValidator` to normalize common model synonyms before validation: `create/update/replace` -> `upsert`, `remove` -> `delete`. Unknown actions remain rejected, and path/content/delete safety rules are unchanged.
- Required verification after deployment: Maven compile, invoice AI Edit preview/apply/readback/lint/test, and failed-test AI Repair smoke.

## 2026-06-21 - Skill Draft AI Edit/Test/Repair Acceptance

### Acceptance Flow

- User-visible flow under test: open Skill draft editor, click AI Edit, enter `把这个 Skill 改成发票抽取 Skill`, display backend `summary/files/warnings`, apply suggestions, write draft files, run Lint, run Test, then use AI Repair when a test fails.
- Frontend locations: `jeecgboot-vue3/src/views/openclaw/skill/SkillEditor.vue` and `jeecgboot-vue3/src/views/openclaw/api.ts`.
- Existing UI supports AI Edit preview modal, result modal with `summary/files/warnings`, apply, Lint, Test, AI Repair, and test history refresh.

### Runtime Fixes

- After Draft Agents were visible to Gateway, test execution still failed because OpenClaw CLI defaulted to the built-in `openai/gpt-5.5`; this server provider accepts `openai/mimo-v2.5-pro`. Gateway returned `FailoverError: LLM request failed: provider rejected the request schema or tool payload`.
- Fix: backend now supports `OPENCLAW_DRAFT_TEST_MODEL` / `openclaw.skill-draft.test-model`. Draft Test CLI execution passes `--model`, and the temporary draft-agent registry entry includes `model.primary`.
- Gateway registry diagnostics were too noisy on stderr and polluted CLI failure text. `scripts/patch-openclaw-draft-agent-registry.py` now emits active/read/merge diagnostics only with `OPENCLAW_DRAFT_AGENT_DEBUG=1`; `read_failed` remains visible.
- Additional Gateway WebSocket diagnostic patch: `client-1OJ6okpi.js` now emits `[jeecg-gateway-ws-close] {"code":...,"reason":...}` for non-1000 closes, or for all closes when `OPENCLAW_DRAFT_AGENT_DEBUG=1`.
- Echo no-external-model acceptance now uses an explicit local Draft Test runner. Request body sets `localExecution: true`, and the server must enable `OPENCLAW_DRAFT_LOCAL_TEST_ENABLED=true`. The backend still performs JEECG auth, Lint, isolated draft workspace materialization, temporary draft-agent registry write, Run/Test persistence, artifact writing, and AI Repair context. For local execution it runs the single `skills/<slug>/main.py` entrypoint (`run(input_text)` or `main(input_text)`) via `python3` and does not call `openclaw agent` or any model provider. Ordinary Draft Tests still use the Gateway CLI path unless `localExecution` is explicitly requested and enabled.

### Mistakes Recorded

- Do not push this repo to `origin` by habit: local `origin` points to upstream `https://github.com/jeecgboot/JeecgBoot.git` and returns 403. The writable remote is `openclaw-platform`.
- After restarting `openclaw-jeecg-backend`, do not run acceptance immediately. JeecgBoot took about 52 seconds to reach `Tomcat started on port 8081`; verify with logs or `ss -ltnp` first.
- Avoid sending indented Python through nested PowerShell -> SSH -> Bash heredocs. In this environment indentation was flattened and caused `IndentationError`; use `scp` for temporary scripts.

### Deployment And Verification

- Server build repo `/opt/openclaw-build/jeecgboot` is at commit `5b61a15b`.
- Re-applied Gateway dist patch, rebuilt backend jar, copied it to `/opt/openclaw-jeecg/backend/app.jar`, restarted `openclaw-jeecg-backend` and `openclaw-gateway`.
- Services are active, and only one system-level Gateway process remains.
- Echo Draft acceptance passed: create draft, save files, Lint, Test, and expected `ECHO_OK_*` output all succeeded.
- Invoice AI Edit acceptance passed: preview, apply, read back invoice content, Lint, Test, and test history succeeded.
- AI Repair after failed test passed: inserting `rm -rf` into `main.py` made Test fail at lint; AI Repair returned a summary and file suggestion.
- After the local runner patch, Echo Draft acceptance passed with artifact `runner=local-skill`, `exitCode=0`, and exact output `ECHO_OK_*`; this proves the Echo smoke did not call the external model path. Invoice AI Edit still passes through the normal Gateway path. AI Repair after failed lint still returns a summary and file suggestion. Recent Gateway logs contain no `1006` or `FailoverError`; one historical non-1000 close diagnostic was observed as `code=1005, reason=""`.

## 2026-06-21 - Skill Draft Test Temporary Agent Registry

### Context

- AI Edit Preview / Apply / Lint had already passed on the server. The remaining blocker was Skill Draft Test failing with `unknown agent id "skill_draft_test_..."`.
- Root cause: the old draft test path created persisted test `openclaw_agent`, `openclaw_skill`, and `openclaw_agent_skill` rows, then executed through the formal Agent Run path. Gateway runtime did not know that test agent, and the approach blurred the draft-vs-published boundary.

### Decision

- Draft Test now creates a temporary agent key shaped as `skill_draft_test_{draftId}_{uuid}`.
- The backend materializes current draft files into the isolated test workspace under `skills/<skillSlug>`, writes a short-lived entry to `/root/.openclaw/draft-agents.json`, runs through Gateway, then actively removes the registry entry.
- Draft Test no longer creates formal Agent, Skill, or Agent-Skill binding rows.
- Registry payload is non-secret operational metadata only: agent id, draft id, workspace id/path, user id/name, skill slug, test run id, and `expiresAt`.

### Gateway Patch Note

- Server OpenClaw is installed as bundled npm dist. The tracked patch script is `scripts/patch-openclaw-draft-agent-registry.py`.
- The patch updates `agent-scope-config-KLbWcRY1.js`, `agent-via-gateway-kIPK668y.js`, and `agent-YgzFw64q.js` so Draft Agents from the registry participate in normal `listAgentIds`, `resolveAgentConfig`, and `resolveAgentWorkspaceDir` lookups.
- Important mistake recorded: patching only `agent-via-gateway` and the Gateway `agent` handler changed the error from `invalid agent params: unknown agent id` to a later session-layer `Unknown agent id`. The correct fix is to patch the shared `agent-scope-config` lookup as well.

### Verification

- Local backend compile passed: `mvn -pl jeecg-boot-module/jeecg-module-demo -am -DskipTests compile`.
- Server smoke: a temporary `draftsmoke` entry in `/root/.openclaw/draft-agents.json` appears in `openclaw agents list --json`; `openclaw agent --agent draftsmoke ...` no longer fails with unknown agent id. The remaining smoke failure was provider schema/tool payload rejection, which is a model execution failure and should be recorded as a Test failure with AI Repair context.
- Deployment note: server had both system-level `/etc/systemd/system/openclaw-gateway.service` and root user-level `/root/.config/systemd/user/openclaw-gateway.service` enabled. The user-level service started a second `openclaw gateway --port 18089` process and caused repeated stale-process SIGTERM/restarts. Fix was `XDG_RUNTIME_DIR=/run/user/0 systemctl --user disable --now openclaw-gateway.service`, then restart the system-level `openclaw-gateway.service`.

## 2026-06-21 - AI Edit 工程化闭环部署复盘

### 背景

本次任务是把 Skill 草稿系统中的 AI Edit 从“直接返回并应用前端传回的建议”升级为安全闭环：

- `preview` 只生成、校验并保存 AI 编辑建议。
- `apply` 只根据 `recordId` 读取已保存建议，做版本/hash、路径、action、content 校验后写入草稿。
- 不直接修改生产 Skill，不绕过 JEECG Boot 鉴权，不绕过 Lint/Test/Publish。

### 本次犯过的错误

1. **误以为新增 Flyway 脚本会在生产环境自动执行。**

   事实：线上使用 `--spring.profiles.active=prod`，而 `application-prod.yml` 中 `spring.flyway.enabled=false`，并且排除了 Flyway 自动配置。因此新增 `V3.9.2_22__openclaw_skill_ai_edit_record.sql` 随 jar 发布后不会自动建表。

   正确做法：生产部署后必须显式验证新增表是否存在。若 prod 继续禁用 Flyway，需要手动执行对应 SQL，或先明确改造生产迁移策略。

2. **远程 MySQL 查询时多次被 PowerShell/SSH/Bash/MySQL 嵌套引号坑到。**

   表现：`SELECT COUNT(*) ...` 在本地 PowerShell 被截断，或在远端 Bash 中丢失 SQL 引号，导致语法错误。

   正确做法：跨 PowerShell -> SSH -> Bash -> MySQL 时，优先使用以下低歧义方案：

   - 简单检查可用十六进制字符串，避免 SQL 字符串引号。
   - 复杂 SQL 优先把 SQL 文件放在服务器上，用 `mysql db < file.sql` 执行。
   - 不要在一条命令里混用多层双引号、单引号和管道，除非已经逐层验证。

3. **本地前端 build 失败时不能直接判断代码有问题。**

   本地 Windows 前端 build 失败在既有 Less timeout，例如：

   - `src/components/jeecg/JVxeTable/src/style/index.less`
   - 之前也出现过其他无关 Less timeout

   这类失败与本次 `SkillEditor.vue` 改动无直接关系。服务器 Linux 环境 `pnpm run build` 成功才是本次部署的最终前端构建证据。

4. **一开始 grep 线上前端产物路径不完整。**

   首次只查了部分目录，没有命中新接口字符串。后来全量查 `/opt/openclaw-jeecg/frontend/html` 才确认产物中包含：

   - `/openclaw/skill/draft/${id}/ai-edit/preview`
   - `/openclaw/skill/draft/${id}/ai-edit/apply`

   正确做法：Vite 产物分片可能在 `html/js` 或 `html/assets`，线上验证应全量查发布目录。

### 本次正确落地的做法

- 后端新增 `openclaw_skill_ai_edit_record` 记录表，保存 preview 的建议、baseVersion、baseHash 和状态。
- `previewAiEdit` 负责读取草稿、读取可选测试上下文、调用 AI、解析 JSON、校验建议、保存记录并返回 `recordId/baseVersion/baseHash`。
- `applyAiEdit` 只根据 `recordId` 读取建议，不信任前端传回的 files。
- `SkillAiEditValidator` 集中做结构、路径白名单、action 白名单、content 和版本/hash 校验。
- 前端 `AI Edit` 使用新接口，展示 summary、warnings、files、diff、新内容，并要求用户确认后应用。
- 应用成功后提示继续运行 Lint/Test。

### 部署与验证记录

- 本地后端编译通过：
  `mvn -pl jeecg-boot-module/jeecg-module-demo -am -DskipTests compile`
- 本地前端 build 因既有 Less timeout 失败，未作为本次代码错误处理。
- 服务器后端 package 成功。
- 服务器前端 `pnpm run build` 成功。
- 服务器部署版本：`7ea41922`
- 后端服务：`openclaw-jeecg-backend active`
- 烟测结果：
  - 后端 randomImage: `200`
  - 前端 root: `200`
  - OpenClaw gateway health: `200`
  - AI Edit preview 未登录: `401`
  - AI Edit apply 未登录: `401`
- 因 prod 禁用 Flyway，已手动执行 MySQL 建表 SQL，并验证 `openclaw_skill_ai_edit_record` 表存在。

### 后续注意事项

- 新增 OpenClaw 表/字段时，必须同时维护 MySQL 和 PostgreSQL Flyway SQL。
- 部署到当前 prod 环境时，不能假设 Flyway 自动执行；必须验证表结构。
- 新接口必须保留 `@RequiresPermissions`，并复用现有 `requireDraft` 权限/状态检查。
- AI 生成内容只允许进入草稿目录，不能直接修改生产 Skill。
- 对 AI 返回的 action 不要静默纠正为安全值；应保留原值并由校验器拒绝非法 action。
- 当前 `workspaceId` 在 AI Edit 记录中为 TODO，因为现有 Skill draft 实体没有 workspace 归属字段。

## 2026-06-21 - AI Edit 发票抽取验收记录

### 本次验收结论

- 服务器 `116.204.135.83` 已部署后端提交 `401c1e98`。
- Skill AI Edit 已接入 `/root/.openclaw/openclaw.json` 中的 OpenAI-compatible 模型配置，`mimo-v2.5-pro` 连通性测试返回 HTTP 200 且有 `choices`。
- 端到端 API 验收通过：登录、创建临时草稿、AI Edit preview、展示所需字段、apply 写入草稿文件、读取 `SKILL.md`、Lint、Test、Test 失败后 AI Repair。
- 验收草稿 `2068408661029535746` 的 AI Edit preview 返回 `source=ai`、`fileCount=5`、`warningCount=1`；apply 后 `SKILL.md` 包含发票/invoice 相关内容；Lint 通过。
- Test 当前失败原因是 OpenClaw Gateway 不认识临时草稿 agent id：`unknown agent id "skill_draft_test_2068408661029535746"`。这触发了 AI Repair，AI Repair 返回 `source=ai`、`fileCount=3`、`warningCount=3`，说明失败后继续修复入口可用。

### 本次发现并修复的问题

1. 生产库仍缺少历史迁移字段 `openclaw_audit_log.result`，导致创建草稿时审计日志插入失败。原因仍是 prod 禁用 Flyway。已按 `V3.9.2_6__openclaw_audit_result.sql` 在 MySQL 手动补齐 `result` 字段和相关索引。
2. `previewAiEdit` 审计日志使用 `Map.of(...)`，当无测试上下文时 `testRunId=null`，会触发 NPE。已改为 `LinkedHashMap`，仅在 `testRunId` 非空时写入；提交为 `401c1e98 fix(openclaw): allow ai edit preview without test run`。
3. 服务器未配置 `OPENCLAW_SKILL_AI_BASE_URL`、`OPENCLAW_SKILL_AI_MODEL`、`OPENCLAW_SKILL_AI_API_KEY` 时，AI Edit 会走 `fallback`，只能返回安全说明和 lint 补全，不会真正把 Skill 改成“发票抽取”。已新增 systemd drop-in `skill-ai.conf`，从 OpenClaw 模型配置接入 Skill AI。

### 后续注意

- 验收自然语言改写质量时必须检查 `source=ai`；`source=fallback` 只能说明降级链路可用，不能证明模型改写能力可用。
- 生产部署后不仅要检查新表，也要检查历史 OpenClaw 增量字段和索引，尤其是 prod 禁用 Flyway 的环境。
- Skill Draft Test 目前依赖 Gateway 能识别临时草稿 agent id；若要让 Test 通过，需要补齐 Gateway 对 draft skill test agent 的注册/临时执行支持。

## 2026-06-21 - Skill 草稿 AI Edit/Test/Repair 闭环产品化验收

### 本次实现范围

- 本地与服务器代码提交：`8a7d95ce feat(openclaw): productize skill draft test repair loop`。
- 服务器 `116.204.135.83` 已拉取该提交，后端 jar 与前端 dist 已部署到 `/opt/openclaw-jeecg`。
- 生产环境 Flyway 仍为禁用状态，本次新增 MySQL 迁移已手动执行并验证字段存在；PostgreSQL 迁移脚本已同步维护但未在当前 MySQL 生产库执行。

### 后端落地方式

- `openclaw_skill_test_run` 增加标准化 Test Report 字段：`agent_key`、`lint_status`、`gateway_status`、`input_json`、`output_json`、`error_type`、`error_code`、`logs_json`、`report_json`。
- 新增 Test Report 查询接口：`GET /openclaw/skill/draft/{draftId}/tests/{testRunId}/report`，继续受 `openclaw:skill:draft:edit` 权限保护。
- Draft Test 只使用临时 Draft Agent 注册与隔离测试工作区，不写正式 `openclaw_agent`、`openclaw_skill` 或绑定表。
- AI Repair 必须关联 `testRunId` 或最近一次测试记录，保存为 `record_type=AI_REPAIR`。
- Repair Apply 只信任后端保存的 `recordId`，复用 AI Edit 的 action 归一化、路径白名单、版本/hash 校验和草稿写入流程，不信任前端回传 files。
- 后续 Test 会回写最近已应用 AI Repair 记录的 `repair_after_status`。

### 前端落地方式

- Skill 编辑器补齐 AI Edit、AI Repair、summary/files/warnings 展示、修改建议查看与应用。
- Lint/Test 后展示标准化 Test Report，包括状态、lint、gateway、输入输出、错误和日志。
- Repair 应用成功后保留操作入口，可继续重新 Lint 和重新 Test。

### 服务器验收结果

- 验收脚本：`scripts/openclaw_skill_draft_closed_loop_acceptance.py`。
- 执行位置：服务器 `/opt/openclaw-build/jeecgboot`。
- 结果：`success=true`。

关键链路：

- AI Edit 发票抽取：创建临时草稿成功；输入“把这个 Skill 改成发票抽取 Skill”后，preview 返回 `recordId`、`summary`、`files`、`warnings`；apply 成功；读取草稿确认内容包含发票/invoice；Lint passed。
- Echo 正向验收：创建临时 Echo Skill 草稿，输入 `{"text":"hello"}`，Test `runStatus=success`，输出包含期望值；Test Report 查询返回 `status=PASSED`、`lintStatus=lint_passed`、`gatewayStatus=OK`。
- 故意失败 + AI Repair：故意抛异常的草稿 Test `runStatus=failed`；AI Repair preview 关联失败 `testRunId`，返回 `recordId`、summary 和 files；Repair apply 成功；apply 后 Lint passed。
- Gateway 日志检查：未发现 `unknown agent id`、`1006`、`abnormal`、`FailoverError`。

### 后续注意

- 当前验收证明 Repair preview/apply 和重新 Lint 链路可用；脚本没有强制要求 Repair 后重新 Test 必须由失败变成功，因为模型修复结果存在不确定性。
- 生产继续禁用 Flyway 时，每次新增 OpenClaw 表或字段都必须手动执行并验证 MySQL 结构。
- 不要把草稿测试结果持久化为正式 Agent/Skill；正式发布仍应走后续独立 Publish/Review 流程。

## 2026-06-22 - Skill Draft Versioning 与回滚验收

### 本次实现范围

- 功能提交：`04c0136f feat(openclaw): add skill draft versioning`。
- 修复提交：`71c7f630 fix(openclaw): allow rollback after passed draft test`。
- 服务器 `116.204.135.83` 已部署到 `71c7f630`。
- 生产环境 Flyway 仍禁用，本次 MySQL 迁移已手动执行；PostgreSQL 迁移脚本已同步维护但未在当前 MySQL 生产库执行。

### 设计与实现

- 新增 `openclaw_skill_draft_version` 表，保存完整文件快照、目录 hash、来源类型、关联 AI record、关联 Test run、Lint/Test 状态。
- `sourceType` 当前支持：`manual`、`ai_edit`、`ai_repair`、`rollback`。
- 手动保存、新建、删除文件都会生成 manual version。
- AI Edit apply 生成 `ai_edit` version，并关联 `openclaw_skill_ai_edit_record.id`。
- AI Repair apply 生成 `ai_repair` version，并关联 repair record 与失败 `testRunId`。
- rollback 会把目标 version 的文件快照恢复到当前草稿，并生成新的 `rollback` version；rollback 后清空当前草稿的 Lint/Test 状态，要求重新 Lint/Test。
- Test run 绑定测试开始时的 `draftVersionNo` 与 `fileHash`；Test 完成后回写对应 version 的 `testStatus`，Lint 会回写最新匹配文件 hash 的 version 的 `lintStatus`。
- 提交审核前要求最新 version 同时满足 `lintStatus=lint_passed` 且 `testStatus=success`。
- `test_passed` 已加入草稿可编辑状态白名单，否则测试通过后的版本回滚会被状态校验挡住。

### 新增接口

- `GET /openclaw/skill/draft/{draftId}/versions`：版本列表，返回 versionNo、sourceType、summary、lintStatus、testStatus、createdBy、createdTime。
- `GET /openclaw/skill/draft/{draftId}/versions/{versionNo}`：版本详情，返回文件快照、关联 AI record、关联 Test Report。
- `GET /openclaw/skill/draft/{draftId}/versions/diff?fromVersionNo=&toVersionNo=`：文件级 diff；缺省一端表示当前草稿。
- `POST /openclaw/skill/draft/{draftId}/versions/{versionNo}/rollback`：回滚到历史版本并生成新版本。

### 验收结果

- 本地后端编译通过：`mvn -pl jeecg-boot-module/jeecg-module-demo -am -DskipTests compile`。
- 本地前端 build 通过：`pnpm build`。
- 服务器后端 package 通过。
- 服务器前端 build 首次遇到既有 Less timeout，重试通过。
- 手动验证生产 MySQL 已存在 `openclaw_skill_draft_version` 表，以及 `openclaw_skill_test_run.draft_version_no/file_hash` 字段。
- 服务器验收脚本 `scripts/openclaw_skill_draft_closed_loop_acceptance.py` 结果：`success=true`。

关键链路：

- Echo 草稿连续 5 次手动保存生成 5 个 manual version。
- Lint passed 后最新 version 回写 `lintStatus=lint_passed`。
- Test passed 后 Test Report 返回 `draftVersionNo=5`，version 详情包含文件快照和 Test Report，最新 version 回写 `testStatus=success`。
- 当前草稿与 v1 diff 返回 4 个文件级变化。
- 回滚到 v1 成功并生成 v6，`sourceType=rollback`，回滚后重新 Lint passed。
- 发票 AI Edit 链路、失败 Test + AI Repair 链路仍通过。
- Gateway 日志未发现 `unknown agent id`、`1006`、`abnormal`、`FailoverError`。

### 后续审核发布流待办

- 增加“选择已通过版本提交审核”的显式接口与 UI，而不是仅提交当前最新版本。
- 审核页需要展示提交版本的固定快照、diff、Test Report 和 AI record。
- 发布时应从审核通过的 version 快照发布，而不是重新读取可变草稿目录。
- 需要定义发布后版本归档、审计记录和回滚到已发布版本的权限边界。
