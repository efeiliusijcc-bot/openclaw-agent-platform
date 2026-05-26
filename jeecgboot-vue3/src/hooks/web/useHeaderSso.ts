import { useGlobSetting } from '/@/hooks/setting';

const HEADER_SSO_HOSTS = ['agent.test-link.xin', 'agent.company.test-link.xin'];

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
  window.location.href = `/oauth2/sign_out?rd=${encodeURIComponent(redirectUrl)}`;
}
