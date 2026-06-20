# Project Memory

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
