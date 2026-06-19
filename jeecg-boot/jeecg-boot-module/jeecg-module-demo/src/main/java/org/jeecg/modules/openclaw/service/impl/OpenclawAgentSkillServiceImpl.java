package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
public class OpenclawAgentSkillServiceImpl extends ServiceImpl<OpenclawAgentSkillMapper, OpenclawAgentSkill> implements IOpenclawAgentSkillService {
    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private OpenclawSkillMaterializer skillMaterializer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSkill(String agentId, String skillId) {
        try {
            doBindSkill(agentId, skillId);
        } catch (RuntimeException e) {
            auditSkillBindingFailure("agent_bind_skill", agentId, skillId, e);
            throw e;
        }
    }

    private void doBindSkill(String agentId, String skillId) {
        OpenclawAgent agent = requireAgent(agentId);
        OpenclawSkill skill = requireBindableSkill(agent, skillId);
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        checkSkillAccess(agent, skill);
        long currentBindings = lambdaQuery()
            .eq(OpenclawAgentSkill::getAgentId, agentId)
            .eq(OpenclawAgentSkill::getEnabled, 1)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count();
        if (agent.getMaxSkills() != null && currentBindings >= agent.getMaxSkills()) {
            throw new JeecgBootException("Agent 可绑定 Skill 数量已达上限");
        }
        OpenclawAgentSkill binding = lambdaQuery()
            .eq(OpenclawAgentSkill::getAgentId, agentId)
            .eq(OpenclawAgentSkill::getSkillId, skillId)
            .one();
        if (binding != null
            && Integer.valueOf(1).equals(binding.getEnabled())
            && Integer.valueOf(OpenclawConstants.DEL_FLAG_NORMAL).equals(binding.getDelFlag())) {
            throw new JeecgBootException("Agent 已绑定该 Skill");
        }
        if (binding == null) {
            binding = new OpenclawAgentSkill();
        }
        binding.setAgentId(agentId);
        binding.setSkillId(skillId);
        binding.setEnabled(1);
        binding.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        saveOrUpdate(binding);
        skillMaterializer.copySkillToAgent(agent, skill);
        boolean gatewayAssignmentCleared = clearGatewayAssignment(agent);
        auditLogService.log("agent_bind_skill", "agent_skill", binding.getId(), bindingAuditDetail(binding, gatewayAssignmentCleared));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindSkill(String agentId, String skillId) {
        try {
            doUnbindSkill(agentId, skillId);
        } catch (RuntimeException e) {
            auditSkillBindingFailure("agent_unbind_skill", agentId, skillId, e);
            throw e;
        }
    }

    private void doUnbindSkill(String agentId, String skillId) {
        OpenclawAgent agent = requireAgent(agentId);
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        OpenclawAgentSkill binding = lambdaQuery()
            .eq(OpenclawAgentSkill::getAgentId, agentId)
            .eq(OpenclawAgentSkill::getSkillId, skillId)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .one();
        if (binding == null) {
            return;
        }
        binding.setEnabled(0);
        binding.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(binding);
        OpenclawSkill skill = skillMapper.selectById(skillId);
        skillMaterializer.removeSkillFromAgent(agent, skill);
        boolean gatewayAssignmentCleared = clearGatewayAssignment(agent);
        auditLogService.log("agent_unbind_skill", "agent_skill", binding.getId(), bindingAuditDetail(binding, gatewayAssignmentCleared));
    }

    private boolean clearGatewayAssignment(OpenclawAgent agent) {
        if (agent == null || !StringUtils.hasText(agent.getGatewayId())) {
            return false;
        }
        agentMapper.update(null, new LambdaUpdateWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getId, agent.getId())
            .set(OpenclawAgent::getGatewayId, null));
        agent.setGatewayId(null);
        return true;
    }

    private JSONObject bindingAuditDetail(OpenclawAgentSkill binding, boolean gatewayAssignmentCleared) {
        JSONObject detail = new JSONObject();
        detail.put("bindingId", binding.getId());
        detail.put("agentId", binding.getAgentId());
        detail.put("skillId", binding.getSkillId());
        detail.put("enabled", binding.getEnabled());
        detail.put("delFlag", binding.getDelFlag());
        detail.put("gatewayAssignmentCleared", gatewayAssignmentCleared);
        detail.put("gatewaySyncRequired", gatewayAssignmentCleared);
        return detail;
    }

    private void auditSkillBindingFailure(String action, String agentId, String skillId, RuntimeException e) {
        try {
            JSONObject detail = new JSONObject();
            detail.put("agentId", agentId);
            detail.put("skillId", skillId);
            detail.put("errorType", e.getClass().getSimpleName());
            detail.put("errorMessage", e.getMessage());
            auditLogService.logFailure(action, "agent_skill", agentId + ":" + skillId, detail);
        } catch (Exception ignored) {
            // Audit failures must not hide the original binding error.
        }
    }

    @Override
    public void disableByAgent(String agentId) {
        lambdaUpdate()
            .eq(OpenclawAgentSkill::getAgentId, agentId)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .set(OpenclawAgentSkill::getEnabled, 0)
            .set(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_DELETED)
            .update();
    }

    @Override
    public int countEnabledBySkill(String skillId) {
        return Math.toIntExact(lambdaQuery()
            .eq(OpenclawAgentSkill::getSkillId, skillId)
            .eq(OpenclawAgentSkill::getEnabled, 1)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count());
    }

    private OpenclawAgent requireAgent(String agentId) {
        OpenclawAgent agent = agentMapper.selectById(agentId);
        if (agent == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(agent.getDelFlag())) {
            throw new JeecgBootException("Agent 不存在");
        }
        if (!OpenclawConstants.AGENT_STATUS_DRAFT.equals(agent.getStatus())
            && !OpenclawConstants.AGENT_STATUS_ENABLED.equals(agent.getStatus())) {
            throw new JeecgBootException("Agent status does not allow skill binding: " + agent.getStatus());
        }
        return agent;
    }

    private OpenclawSkill requireBindableSkill(OpenclawAgent agent, String skillId) {
        OpenclawSkill skill = skillMapper.selectById(skillId);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill 不存在");
        }
        if (OpenclawConstants.SKILL_STATUS_DISABLED.equals(skill.getStatus())) {
            throw new JeecgBootException("已禁用 Skill 不能绑定");
        }
        if (OpenclawConstants.SKILL_STATUS_DRAFT.equals(skill.getStatus())) {
            throw new JeecgBootException("草稿 Skill 需审核或导入后才能绑定");
        }
        if (!OpenclawConstants.SKILL_STATUS_APPROVED.equals(skill.getStatus())
            && !OpenclawConstants.SKILL_STATUS_PRIVATE.equals(skill.getStatus())) {
            throw new JeecgBootException("Skill 状态不允许绑定: " + (StringUtils.hasText(skill.getStatus()) ? skill.getStatus() : "empty"));
        }
        if (OpenclawConstants.SKILL_STATUS_PRIVATE.equals(skill.getStatus())
            && !Objects.equals(agent.getUserId(), skill.getOwnerUserId())) {
            throw new JeecgBootException("私有 Skill 只能绑定到同一用户的 Agent");
        }
        return skill;
    }

    private void checkSkillAccess(OpenclawAgent agent, OpenclawSkill skill) {
        if (OpenclawConstants.SKILL_STATUS_APPROVED.equals(skill.getStatus())) {
            return;
        }
        if (!Objects.equals(agent.getUserId(), skill.getOwnerUserId())) {
            throw new JeecgBootException("无权绑定该 Skill");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
    }
}
