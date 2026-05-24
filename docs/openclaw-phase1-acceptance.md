# OpenClaw Phase 1 Acceptance

## 部署地址

- 前端: `http://43.250.173.37:3100/`
- 后端: `http://43.250.173.37:8081/jeecg-boot`

## 验收账号

- `admin/123456`: 管理员账号，已通过登录和 OpenClaw 管理菜单验收。
- `ceshi/123456`: 普通员工账号，已通过登录和普通员工权限隔离验收。
- `jeecg/123456`: 普通员工账号，已通过登录和普通员工权限隔离验收。
- `zhangsan/123456`: 未通过登录，不作为第一阶段通过账号。

## 数据库

- 容器: `auth-postgres`
- 数据库: `openclaw_platform`
- Owner: `keycloak`
- 已验证 `openclaw_` 8 张表真实落库。

## 已通过验收项

- JeecgBoot 原生登录通过。
- JeecgBoot 前端、后端、Redis、PostgreSQL 云端联调通过。
- 42 项自动化检查全部通过。
- 管理员可见 OpenClaw 平台管理菜单。
- 普通员工菜单权限隔离通过。
- `openclaw_` 8 张表建表和基础数据通过。
- Agent 创建时自动创建 Workspace 记录。
- Agent 创建配额限制通过。
- 普通员工数据隔离通过，员工 A 看不到员工 B 的 Agent/Skill。
- Skill zip 导入通过，导入包必须包含 `SKILL.md`。
- Skill zip 导出通过。
- Agent-Skill 绑定和解绑通过。
- 审计日志记录 Agent 创建/删除、Skill 导入/导出、配额修改、Agent-Skill 绑定/解绑。

## 未实现项

- Agent Runs 当前只是运行记录列表，尚未接 OpenClaw Gateway 执行层。
- 第一阶段未实现 Agent 对话。
- 第一阶段未实现 Agent 真实执行。
- 第一阶段未实现运行日志回写。
- 第一阶段未接 Keycloak Header 自动登录。
- 第一阶段未配置 oauth2-proxy 代理 JeecgBoot。
- 第一阶段未接 OpenClaw Gateway Pool。
