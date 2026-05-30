import { defHttp } from '/@/utils/http/axios';
import { downloadFile } from '/@/api/common/api';
import { getToken } from '/@/utils/auth';

export const listAgents = (params) => defHttp.get({ url: '/openclaw/agent/list', params });
export const addAgent = (params) => defHttp.post({ url: '/openclaw/agent/add', params });
export const editAgent = (params) => defHttp.post({ url: '/openclaw/agent/edit', params });
export const deleteAgent = (params) => defHttp.delete({ url: '/openclaw/agent/delete', params }, { joinParamsToUrl: true });
export const disableAgent = (params) => defHttp.post({ url: '/openclaw/agent/disable', params }, { joinParamsToUrl: true });
export const bindSkill = (params) => defHttp.post({ url: '/openclaw/agent/bindSkill', params });
export const unbindSkill = (params) => defHttp.post({ url: '/openclaw/agent/unbindSkill', params });

export const listWorkspaces = (params) => defHttp.get({ url: '/openclaw/workspace/list', params });
export const listSkills = (params) => defHttp.get({ url: '/openclaw/skill/list', params });
export const addSkill = (params) => defHttp.post({ url: '/openclaw/skill/add', params });
export const editSkill = (params) => defHttp.post({ url: '/openclaw/skill/edit', params });
export const deleteSkill = (params) => defHttp.delete({ url: '/openclaw/skill/delete', params }, { joinParamsToUrl: true });
export const disableSkill = (params) => defHttp.post({ url: '/openclaw/skill/disable', params }, { joinParamsToUrl: true });
export const importSkill = (file: File) => defHttp.uploadFile({ url: '/openclaw/skill/import' }, { name: 'file', file });
export const exportSkill = (record) => downloadFile(`/openclaw/skill/${record.id}/export`, `${record.slug}-${record.version}.zip`);

export const listRuns = (params) => defHttp.get({ url: '/openclaw/run/list', params });
export const listQuotas = (params) => defHttp.get({ url: '/openclaw/quota/list', params });
export const myQuota = () => defHttp.get({ url: '/openclaw/quota/my' });
export const myQuotaUsage = () => defHttp.get({ url: '/openclaw/quota/myUsage' });
export const editQuota = (params) => defHttp.post({ url: '/openclaw/quota/edit', params });

export const listGateways = (params) => defHttp.get({ url: '/openclaw/gateway/list', params });
export const addGateway = (params) => defHttp.post({ url: '/openclaw/gateway/add', params });
export const editGateway = (params) => defHttp.post({ url: '/openclaw/gateway/edit', params });
export const deleteGateway = (params) => defHttp.delete({ url: '/openclaw/gateway/delete', params }, { joinParamsToUrl: true });
export const previewGatewayConfig = (id: string) => defHttp.get({ url: `/openclaw/gateway/${id}/configPreview` });
export const syncGatewayConfig = (id: string) => defHttp.post({ url: `/openclaw/gateway/${id}/sync` });

export const listAuditLogs = (params) => defHttp.get({ url: '/openclaw/audit/list', params });
export const listAgentSkills = (params) => defHttp.get({ url: '/openclaw/agentSkill/list', params });
export const runAgentTest = (id: string, params) => defHttp.post({ url: `/openclaw/agent/${id}/run-test`, params, timeout: 90 * 1000 });

export const streamAgentChat = async (id: string, params) => {
  const response = await fetch(`/jeecg-boot/openclaw/agent/${id}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Access-Token': getToken() || '',
    },
    credentials: 'same-origin',
    body: JSON.stringify(params),
  });
  if (!response.ok || !response.body) {
    const message = await response.text().catch(() => '');
    throw new Error(message || `HTTP ${response.status}`);
  }
  return response.body;
};
