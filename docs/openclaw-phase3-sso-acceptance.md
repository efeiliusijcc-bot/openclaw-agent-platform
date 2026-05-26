# OpenClaw Phase 3 SSO Acceptance

## Scope

- Date: 2026-05-27
- Target host: 43.250.173.37
- SSO entry: https://agent.company.test-link.xin
- Native rollback entry: http://43.250.173.37:3100
- Backend: http://43.250.173.37:8081/jeecg-boot
- Goal: access JeecgBoot through Keycloak + oauth2-proxy + Nginx Header SSO without showing the JeecgBoot native login page.

## Deployed Changes

### auth-system

- Reused the existing oauth2-proxy protected Nginx entry for the JeecgBoot Agent workspace test domain.
- Current test env maps:
  - `OPENCLAW_DOMAIN=agent.company.test-link.xin`
  - `OPENCLAW_BASE_URL=https://agent.company.test-link.xin`
  - `OPENCLAW_UPSTREAM=http://host.docker.internal:3100`
- Nginx forwards these headers to JeecgBoot:
  - `X-Auth-Request-Email`
  - `X-Auth-Request-User`
  - `X-Auth-Request-Groups`
- Added `proxy_set_header Cookie "";` for the protected upstream so the large oauth2-proxy session cookie is not forwarded to JeecgBoot/Tomcat.
- Keycloak client `openclaw-auth-proxy` allows:
  - `https://openclaw.company.test-link.xin/oauth2/callback`
  - `https://agent.company.test-link.xin/oauth2/callback`

### JeecgBoot Backend

- Added `GET /sys/sso/header-login`.
- Reads oauth2-proxy headers and creates or finds a local `sys_user`.
- Generates a normal JeecgBoot token and returns the same token payload expected by the frontend.
- Maps Keycloak groups to JeecgBoot roles.

### JeecgBoot Frontend

- Header SSO mode is enabled for production.
- When no token exists, the route guard calls `/sys/sso/header-login`.
- On success, the token is stored and the normal permission bootstrap continues through `getUserPermissionByToken`.
- The original `http://43.250.173.37:3100` native login entry remains available as rollback.

## Group To Role Mapping

| Keycloak group | JeecgBoot role |
| --- | --- |
| `/openclaw-users` | `openclaw_employee` |
| `/openclaw-admins` | `openclaw_admin` |
| `/openclaw-skill-reviewer` | `openclaw_skill_reviewer` |

Admin privileges are only granted when `/openclaw-admins` is present.

## Test Accounts

- `agent.admin`: Keycloak test user with `/openclaw-users` and `/openclaw-admins`.
- `agent.employee`: Keycloak test user with `/openclaw-users`.

Passwords are managed in Keycloak and are not stored in this repository.

## Acceptance Results

| Item | Result |
| --- | --- |
| Unauthenticated request to agent domain | Passed: Nginx/oauth2-proxy returns 302 to Keycloak |
| Keycloak callback for agent domain | Passed after adding the agent callback URL to Keycloak client |
| JeecgBoot native login skipped | Passed at HTTP flow level: oauth2 callback returns frontend and `header-login` returns token |
| `/sys/sso/header-login` | Passed: 200 for admin and employee |
| `/sys/permission/getUserPermissionByToken` | Passed: 200 for admin and employee |
| `/sys/user/getUserSectionInfo` | Passed: 200 for admin and employee |
| `/openclaw/agent/list` | Passed: admin sees 10 records, employee sees 0 own records |
| `/openclaw/run/list` | Passed: admin sees 18 records, employee sees 0 own records |
| Admin menu permissions | Passed: Gateway Nodes, Quota Management, Audit Logs, Skill Review visible in permission tree |
| Employee menu permissions | Passed: management menus absent; My Agents, My Skills, My Workspaces, Agent Runs present |
| Employee privileged API access | Passed: Gateway sync is denied without `openclaw:gateway:sync` |
| Agent Run regression | Passed: SSO token ran Agent `2057384279284858882`, returned `OK`, run id `2059317488145731585` |
| Native rollback entry | Passed: `http://43.250.173.37:3100` remains separate from SSO entry |

## Browser Note

The automated Chrome client in this Codex environment blocked `https://agent.company.test-link.xin` with `ERR_BLOCKED_BY_CLIENT`. The server-side SSO chain was therefore verified with a real HTTP form-login flow through Nginx, oauth2-proxy, Keycloak, oauth2 callback, and JeecgBoot APIs. Manual browser verification should be run from a browser/profile that does not block the test domain.

## Known Risks

- `OPENCLAW_DOMAIN` is currently reused as the JeecgBoot Agent workspace SSO domain. This is acceptable for the test stage, but should be split later:
  - `AGENT_DOMAIN=agent.company.test-link.xin`
  - `AGENT_BASE_URL=https://agent.company.test-link.xin`
  - `AGENT_UPSTREAM=http://host.docker.internal:3100`
  - `OPENCLAW_DOMAIN=openclaw.company.test-link.xin`
  - `OPENCLAW_BASE_URL=https://openclaw.company.test-link.xin`
  - `OPENCLAW_UPSTREAM=http://host.docker.internal:18789`
- The native login entry is still public and should later be limited to admin IPs, VPN, or a maintenance-only route.

## Rollback

1. Use the native JeecgBoot login entry: `http://43.250.173.37:3100`.
2. Restore auth-system env to the previous OpenClaw Control UI domain/upstream values.
3. Recreate `auth-nginx` and `auth-oauth2-proxy`.
4. Disable frontend Header SSO mode if the SSO route guard must be bypassed.
5. Keep the JeecgBoot Header SSO backend endpoint deployed; it is inert unless Nginx sends the auth headers and the frontend calls it.
