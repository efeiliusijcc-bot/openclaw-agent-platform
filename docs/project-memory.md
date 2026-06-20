# Project Memory

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
