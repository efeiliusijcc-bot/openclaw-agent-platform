# OpenClaw Phase 3 Header SSO

## Goal

Add a new SSO entry for JeecgBoot without replacing the existing native login entry.

- Native rollback entry: `http://43.250.173.37:3100`
- New SSO test entry: `https://agent.company.test-link.xin`
- Auth chain: Nginx -> oauth2-proxy -> Keycloak -> Nginx auth headers -> JeecgBoot

Phase 3 does not change OpenClaw Gateway Pool, task queues, OpenClaw Control UI, or the working Agent Run flow.

## Backend

New endpoint:

```text
GET /jeecg-boot/sys/sso/header-login
```

The endpoint reads these headers:

```text
X-Auth-Request-Email
X-Auth-Request-User
X-Auth-Request-Groups
```

Behavior:

- Uses `X-Auth-Request-Email` as the stable identity.
- Finds local `sys_user` by email.
- Creates a normal local user when the email does not exist.
- Generates a normal JeecgBoot JWT with the existing `JwtUtil` and Redis token cache.
- Returns the same login result shape as native login, so the frontend can call `getUserPermissionByToken` normally.

## Group Mapping

Keycloak group to JeecgBoot role mapping:

```text
/openclaw-users          -> openclaw_employee
/openclaw-admins         -> openclaw_admin
/openclaw-skill-reviewer -> openclaw_skill_reviewer
```

Rules:

- `openclaw_employee` is assigned as the normal-user baseline.
- `openclaw_admin` is assigned only when `/openclaw-admins` is present.
- SSO sync replaces only managed OpenClaw roles and preserves unrelated JeecgBoot roles.

## Frontend

Header SSO mode is enabled when either condition is true:

- Runtime config contains `VITE_GLOB_HEADER_SSO=true`.
- Browser hostname is `agent.company.test-link.xin`.

When Header SSO mode is active and no JeecgBoot token exists:

1. The router guard calls `/sys/sso/header-login`.
2. On success, it stores the returned token.
3. It continues the normal JeecgBoot permission route flow.
4. On failure, it shows `SSO 登录失败，请联系管理员`.

The existing IP entry keeps native login behavior.

## Nginx Suggestion

Example server block for the SSO entry:

```nginx
server {
    listen 443 ssl http2;
    server_name agent.company.test-link.xin;

    ssl_certificate     /etc/nginx/certs/agent.company.test-link.xin.crt;
    ssl_certificate_key /etc/nginx/certs/agent.company.test-link.xin.key;

    location /oauth2/ {
        proxy_pass http://auth-oauth2-proxy:4180;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Scheme $scheme;
    }

    location = /oauth2/auth {
        internal;
        proxy_pass http://auth-oauth2-proxy:4180/oauth2/auth;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Scheme $scheme;
        proxy_set_header Content-Length "";
        proxy_pass_request_body off;
    }

    location /jeecgboot/ {
        auth_request /oauth2/auth;
        error_page 401 = /oauth2/sign_in;

        auth_request_set $auth_email  $upstream_http_x_auth_request_email;
        auth_request_set $auth_user   $upstream_http_x_auth_request_user;
        auth_request_set $auth_groups $upstream_http_x_auth_request_groups;

        proxy_set_header X-Auth-Request-Email  $auth_email;
        proxy_set_header X-Auth-Request-User   $auth_user;
        proxy_set_header X-Auth-Request-Groups $auth_groups;

        proxy_pass http://openclaw-jeecg-backend:8080/jeecg-boot/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        auth_request /oauth2/auth;
        error_page 401 = /oauth2/sign_in;
        root /opt/openclaw-jeecg/frontend/html;
        try_files $uri $uri/ /index.html;
    }
}
```

The SSO frontend runtime config should keep:

```js
window.__PRODUCTION__JEECGBOOT__CONF__ = {
  VITE_GLOB_API_URL: "/jeecgboot",
  VITE_GLOB_HEADER_SSO: "true"
}
```

## Acceptance Checklist

- Unauthenticated access to `agent.company.test-link.xin` redirects to Keycloak.
- Keycloak login returns to JeecgBoot without showing the native JeecgBoot login page.
- `/sys/sso/header-login` returns a valid JeecgBoot token.
- A new email creates one local `sys_user`.
- `/openclaw-users` receives `openclaw_employee`.
- `/openclaw-admins` receives `openclaw_admin`.
- Admin role is not assigned without `/openclaw-admins`.
- `/openclaw-skill-reviewer` receives `openclaw_skill_reviewer`.
- Admin and normal-user menu permissions still match Phase 1 behavior.
- Normal users still see only their own Agent and Skill data.
- Agent Run test still works and writes `openclaw_agent_run`.
- Native login at `http://43.250.173.37:3100` still works.

## Rollback

1. Remove or disable the `agent.company.test-link.xin` Nginx server block.
2. Keep using `http://43.250.173.37:3100` native JeecgBoot login.
3. If needed, set `VITE_GLOB_HEADER_SSO=false` in the SSO frontend runtime config.
4. Existing users and roles do not need schema rollback.
