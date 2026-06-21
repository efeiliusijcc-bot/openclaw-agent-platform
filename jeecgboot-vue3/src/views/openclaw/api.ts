import { defHttp } from '/@/utils/http/axios';
import { downloadFile } from '/@/api/common/api';
import { getToken } from '/@/utils/auth';

export const listAgents = (params) => defHttp.get({ url: '/openclaw/agent/list', params });
export const addAgent = (params) => defHttp.post({ url: '/openclaw/agent/add', params });
export const editAgent = (params) => defHttp.post({ url: '/openclaw/agent/edit', params });
export const deleteAgent = (params) => defHttp.delete({ url: '/openclaw/agent/delete', params }, { joinParamsToUrl: true });
export const disableAgent = (params) => defHttp.post({ url: '/openclaw/agent/disable', params }, { joinParamsToUrl: true });
export const enableAgent = (params) => defHttp.post({ url: '/openclaw/agent/enable', params }, { joinParamsToUrl: true });
export const bindSkill = (params) => defHttp.post({ url: '/openclaw/agent/bindSkill', params });
export const unbindSkill = (params) => defHttp.post({ url: '/openclaw/agent/unbindSkill', params });

export const listWorkspaces = (params) => defHttp.get({ url: '/openclaw/workspace/list', params });
export const checkWorkspaceHealth = (id: string) => defHttp.get({ url: `/openclaw/workspace/${id}/health-check` });
export const rematerializeWorkspace = (id: string) => defHttp.post({ url: `/openclaw/workspace/${id}/rematerialize` });
export const listSkills = (params) => defHttp.get({ url: '/openclaw/skill/list', params });
export const addSkill = (params) => defHttp.post({ url: '/openclaw/skill/add', params });
export const editSkill = (params) => defHttp.post({ url: '/openclaw/skill/edit', params });
export const deleteSkill = (params) => defHttp.delete({ url: '/openclaw/skill/delete', params }, { joinParamsToUrl: true });
export const disableSkill = (params) => defHttp.post({ url: '/openclaw/skill/disable', params }, { joinParamsToUrl: true });
export const approveSkill = (params) => defHttp.post({ url: '/openclaw/skill/approve', params }, { joinParamsToUrl: true });
export const rejectSkill = (params) => defHttp.post({ url: '/openclaw/skill/reject', params }, { joinParamsToUrl: true });
export const importSkill = (file: File) => defHttp.uploadFile({ url: '/openclaw/skill/import' }, { name: 'file', file });
export const exportSkill = (record) => downloadFile(`/openclaw/skill/${record.id}/export`, `${record.slug}-${record.version}.zip`);
export const checkSkillQuality = (id: string) => defHttp.get({ url: `/openclaw/skill/${id}/quality-check` });
export const listSkillDrafts = (params) => defHttp.get({ url: '/openclaw/skill/draft/list', params });
export const addSkillDraft = (params) => defHttp.post({ url: '/openclaw/skill/draft/add', params });
export const generateSkillDraft = (params) => defHttp.post({ url: '/openclaw/skill/draft/generate', params, timeout: 90 * 1000 });
export const createSkillDraftFromSkill = (skillId: string) =>
  defHttp.post({ url: '/openclaw/skill/draft/fromSkill', params: { skillId } }, { joinParamsToUrl: true });
export const getSkillDraft = (id: string) => defHttp.get({ url: `/openclaw/skill/draft/${id}` });
export const getSkillDraftTree = (id: string) => defHttp.get({ url: `/openclaw/skill/draft/${id}/tree` });
export const readSkillDraftFile = (id: string, path: string) => defHttp.get({ url: `/openclaw/skill/draft/${id}/file`, params: { path } });
export const saveSkillDraftFile = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/file`, params });
export const createSkillDraftFile = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/file/create`, params });
export const deleteSkillDraftFile = (id: string, path: string) =>
  defHttp.delete({ url: `/openclaw/skill/draft/${id}/file`, params: { path } }, { joinParamsToUrl: true });
export const lintSkillDraft = (id: string) => defHttp.post({ url: `/openclaw/skill/draft/${id}/lint` });
export const runSkillDraftTest = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/test`, params, timeout: 90 * 1000 });
export const runSkillDraftBatchTests = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/tests/run`, params, timeout: 10 * 90 * 1000 });
export const listSkillDraftTests = (id: string, params) => defHttp.get({ url: `/openclaw/skill/draft/${id}/tests`, params });
export const getSkillDraftTestReport = (id: string, testRunId: string) => defHttp.get({ url: `/openclaw/skill/draft/${id}/tests/${testRunId}/report` });
export const repairSkillDraft = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/repair`, params, timeout: 120 * 1000 });
export const applySkillDraftRepair = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/repair/apply`, params });
export const previewSkillDraftAiEdit = (id: string, params) =>
  defHttp.post({ url: `/openclaw/skill/draft/${id}/ai-edit/preview`, params, timeout: 120 * 1000 });
export const applySkillDraftAiEdit = (id: string, params) => defHttp.post({ url: `/openclaw/skill/draft/${id}/ai-edit/apply`, params });
export const submitSkillDraft = (id: string) => defHttp.post({ url: `/openclaw/skill/draft/${id}/submit` });
export const approveSkillDraft = (id: string) => defHttp.post({ url: `/openclaw/skill/draft/${id}/approve` });
export const rejectSkillDraft = (id: string, reason: string) =>
  defHttp.post({ url: `/openclaw/skill/draft/${id}/reject`, params: { reason } }, { joinParamsToUrl: true });
export const publishSkillDraft = (id: string) => defHttp.post({ url: `/openclaw/skill/draft/${id}/publish` });

export const listRuns = (params) => defHttp.get({ url: '/openclaw/run/list', params });
export const getRunDetail = (id: string) => defHttp.get({ url: `/openclaw/run/${id}` });
export const listQuotas = (params) => defHttp.get({ url: '/openclaw/quota/list', params });
export const myQuota = () => defHttp.get({ url: '/openclaw/quota/my' });
export const myQuotaUsage = () => defHttp.get({ url: '/openclaw/quota/myUsage' });
export const getQuotaUsage = (params) => defHttp.get({ url: '/openclaw/quota/usage', params });
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
export const getSystemHealth = () => defHttp.get({ url: '/openclaw/system/health' });

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
