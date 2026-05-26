import { useGlobSetting } from '/@/hooks/setting';

const HEADER_SSO_HOSTS = ['agent.test-link.xin', 'agent.company.test-link.xin'];
const KEYCLOAK_LOGOUT_URL = 'https://auth.company.test-link.xin/realms/company/protocol/openid-connect/logout';
const KEYCLOAK_CLIENT_ID = 'openclaw-auth-proxy';

export function isHeaderSsoEnv() {
  const globSetting = useGlobSetting();
  return globSetting.headerSso === 'true' || HEADER_SSO_HOSTS.includes(window.location.hostname);
}

export function getHeaderSsoHomeUrl() {
  return `${window.location.origin}/`;
}

export function redirectToHeaderSsoStart(redirectUrl = getHeaderSsoHomeUrl()) {
  window.location.href = `/oauth2/start?rd=${encodeURIComponent(redirectUrl)}`;
}

export function redirectToHeaderSsoSignOut(redirectUrl = getHeaderSsoHomeUrl()) {
  const keycloakLogoutUrl = `${KEYCLOAK_LOGOUT_URL}?client_id=${encodeURIComponent(KEYCLOAK_CLIENT_ID)}&post_logout_redirect_uri=${encodeURIComponent(redirectUrl)}`;
  window.location.href = `/oauth2/sign_out?rd=${encodeURIComponent(keycloakLogoutUrl)}`;
}
