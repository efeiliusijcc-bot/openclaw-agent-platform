package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawAgentCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawAgentEditDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentService;
import org.jeecg.modules.openclaw.service.IOpenclawAgentSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.service.IOpenclawWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OpenclawAgentServiceImpl extends ServiceImpl<OpenclawAgentMapper, OpenclawAgent> implements IOpenclawAgentService {
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawUserQuotaService quotaService;
    @Autowired
    private IOpenclawWorkspaceService workspaceService;
    @Autowired
    private IOpenclawAgentSkillService agentSkillService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private OpenclawWorkspaceMaterializer workspaceMaterializer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawAgent createAgent(OpenclawAgentCreateDTO dto) {
        LoginUser user = permissionService.currentUser();
        if (!StringUtils.hasText(dto.getName())) {
            throw new JeecgBootException("Agent 名称不能为空");
        }
        OpenclawUserQuota quota = quotaService.getOrCreateQuota(user);
        if (!OpenclawConstants.STATUS_ENABLED.equals(quota.getStatus())) {
            throw new JeecgBootException("当前用户配额已禁用，请联系管理员");
        }
        long usedAgents = lambdaQuery()
            .eq(OpenclawAgent::getUserId, user.getId())
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count();
        if (usedAgents >= quota.getMaxAgents()) {
            throw new JeecgBootException("Agent 配额不足，请联系管理员增加配额");
        }
        String agentKey = generateAgentKey();
        OpenclawWorkspace workspace = workspaceService.createForAgent(user, dto.getName(), agentKey);
        OpenclawAgent agent = new OpenclawAgent();
        agent.setUserId(user.getId());
        agent.setUsername(user.getUsername());
        agent.setWorkspaceId(workspace.getId());
        agent.setAgentKey(agentKey);
        agent.setName(dto.getName());
        agent.setDescription(dto.getDescription());
        agent.setStatus(OpenclawConstants.AGENT_STATUS_DRAFT);
        agent.setMaxSkills(dto.getMaxSkills() == null ? 10 : dto.getMaxSkills());
        agent.setMaxDailyRuns(dto.getMaxDailyRuns() == null ? quota.getMaxDailyRuns() : dto.getMaxDailyRuns());
        agent.setConfigJson(dto.getConfigJson());
        agent.setRemark(dto.getRemark());
        agent.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        workspaceMaterializer.materialize(agent, workspace);
        save(agent);
        auditLogService.log("agent_create", "agent", agent.getId(), agent);
        return agent;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editAgent(OpenclawAgentEditDTO dto) {
        OpenclawAgent agent = getById(dto.getId());
        if (agent == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(agent.getDelFlag())) {
            throw new JeecgBootException("Agent 不存在");
        }
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        agent.setName(dto.getName());
        agent.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getStatus())) {
            agent.setStatus(dto.getStatus());
        }
        agent.setMaxSkills(dto.getMaxSkills());
        agent.setMaxDailyRuns(dto.getMaxDailyRuns());
        agent.setConfigJson(dto.getConfigJson());
        agent.setRemark(dto.getRemark());
        updateById(agent);
        workspaceMaterializer.materialize(agent, workspaceService.getById(agent.getWorkspaceId()));
        auditLogService.log("agent_update", "agent", agent.getId(), agent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logicDeleteAgent(String id) {
        OpenclawAgent agent = getById(id);
        if (agent == null) {
            return;
        }
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        agent.setStatus(OpenclawConstants.AGENT_STATUS_DISABLED);
        agent.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(agent);
        agentSkillService.disableByAgent(agent.getId());
        workspaceService.markDeleted(agent.getWorkspaceId());
        auditLogService.log("agent_delete", "agent", agent.getId(), agent);
    }

    @Override
    public void disableAgent(String id) {
        OpenclawAgent agent = getById(id);
        if (agent == null) {
            return;
        }
        if (!permissionService.isAdmin(permissionService.currentUser())) {
            throw new JeecgBootException("只有 OpenClaw 管理员可以禁用 Agent");
        }
        agent.setStatus(OpenclawConstants.AGENT_STATUS_DISABLED);
        updateById(agent);
        auditLogService.log("agent_disable", "agent", agent.getId(), agent);
    }

    private String generateAgentKey() {
        return "agt_" + IdWorker.getIdStr();
    }
}
