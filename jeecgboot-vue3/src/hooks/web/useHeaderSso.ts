import { useGlobSetting } from '/@/hooks/setting';

const HEADER_SSO_HOST = 'agent.company.test-link.xin';

export function isHeaderSsoEnv() {
  const globSetting = useGlobSetting();
  return globSetting.headerSso === 'true' || window.location.hostname === HEADER_SSO_HOST;
}
