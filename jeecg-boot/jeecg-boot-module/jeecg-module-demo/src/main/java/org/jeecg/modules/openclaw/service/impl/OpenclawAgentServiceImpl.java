package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
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

import java.util.Set;

@Service
public class OpenclawAgentServiceImpl extends ServiceImpl<OpenclawAgentMapper, OpenclawAgent> implements IOpenclawAgentService {
    private static final Set<String> EDITABLE_AGENT_STATUSES = Set.of(
        OpenclawConstants.AGENT_STATUS_DRAFT,
        OpenclawConstants.AGENT_STATUS_ENABLED,
        OpenclawConstants.AGENT_STATUS_DISABLED,
        OpenclawConstants.AGENT_STATUS_ERROR
    );

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
        validateAgentCreateRequest(user, dto);
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
        String name = dto.getName().trim();
        OpenclawWorkspace workspace = workspaceService.createForAgent(user, name, agentKey);
        OpenclawAgent agent = new OpenclawAgent();
        agent.setUserId(user.getId());
        agent.setUsername(user.getUsername());
        agent.setWorkspaceId(workspace.getId());
        agent.setAgentKey(agentKey);
        agent.setName(name);
        agent.setDescription(dto.getDescription());
        agent.setStatus(OpenclawConstants.AGENT_STATUS_DRAFT);
        agent.setMaxSkills(dto.getMaxSkills() == null ? 10 : dto.getMaxSkills());
        agent.setMaxDailyRuns(dto.getMaxDailyRuns() == null ? quota.getMaxDailyRuns() : dto.getMaxDailyRuns());
        agent.setConfigJson(dto.getConfigJson());
        agent.setRemark(dto.getRemark());
        agent.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        workspaceMaterializer.materialize(agent, workspace);
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_READY);
        workspaceService.updateById(workspace);
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
        validateAgentEditRequest(agent, dto);
        agent.setName(dto.getName().trim());
        agent.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getStatus())) {
            agent.setStatus(normalizeEditableStatus(dto.getStatus()));
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
        agent.setStatus(OpenclawConstants.AGENT_STATUS_DELETED);
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

    private void validateAgentCreateRequest(LoginUser user, OpenclawAgentCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new JeecgBootException("Agent name is required");
        }
        validateAgentName(user.getId(), null, dto.getName());
        validateAgentLimits(dto.getMaxSkills(), dto.getMaxDailyRuns());
        validateConfigJson(dto.getConfigJson());
    }

    private void validateAgentEditRequest(OpenclawAgent agent, OpenclawAgentEditDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new JeecgBootException("Agent name is required");
        }
        validateAgentName(agent.getUserId(), agent.getId(), dto.getName());
        validateAgentLimits(dto.getMaxSkills(), dto.getMaxDailyRuns());
        validateConfigJson(dto.getConfigJson());
    }

    private void validateAgentName(String userId, String currentAgentId, String name) {
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new JeecgBootException("Agent name is too long, max length is 100");
        }
        var query = lambdaQuery()
            .eq(OpenclawAgent::getUserId, userId)
            .eq(OpenclawAgent::getName, normalized)
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
        if (StringUtils.hasText(currentAgentId)) {
            query.ne(OpenclawAgent::getId, currentAgentId);
        }
        if (query.count() > 0) {
            throw new JeecgBootException("Agent name already exists for this user");
        }
    }

    private void validateAgentLimits(Integer maxSkills, Integer maxDailyRuns) {
        if (maxSkills != null && maxSkills < 0) {
            throw new JeecgBootException("Agent maxSkills must not be negative");
        }
        if (maxDailyRuns != null && maxDailyRuns < 0) {
            throw new JeecgBootException("Agent maxDailyRuns must not be negative");
        }
    }

    private void validateConfigJson(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return;
        }
        try {
            JSON.parseObject(configJson);
        } catch (Exception e) {
            throw new JeecgBootException("Agent configJson must be a valid JSON object", e);
        }
    }

    private String normalizeEditableStatus(String status) {
        String normalized = status.trim().toLowerCase();
        if ("active".equals(normalized)) {
            normalized = OpenclawConstants.AGENT_STATUS_ENABLED;
        }
        if (!EDITABLE_AGENT_STATUSES.contains(normalized)) {
            throw new JeecgBootException("Unsupported Agent status: " + status);
        }
        return normalized;
    }
}
