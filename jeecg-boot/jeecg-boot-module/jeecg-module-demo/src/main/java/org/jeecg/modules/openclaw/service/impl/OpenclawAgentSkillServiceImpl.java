package org.jeecg.modules.openclaw.service.impl;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSkill(String agentId, String skillId) {
        OpenclawAgent agent = agentMapper.selectById(agentId);
        if (agent == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(agent.getDelFlag())) {
            throw new JeecgBootException("Agent 不存在");
        }
        OpenclawSkill skill = skillMapper.selectById(skillId);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill 不存在");
        }
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        if (!permissionService.isAdmin(permissionService.currentUser())) {
            permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        }
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
        if (binding == null) {
            binding = new OpenclawAgentSkill();
            binding.setAgentId(agentId);
            binding.setSkillId(skillId);
        }
        binding.setEnabled(1);
        binding.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        saveOrUpdate(binding);
        auditLogService.log("agent_bind_skill", "agent_skill", binding.getId(), binding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindSkill(String agentId, String skillId) {
        OpenclawAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new JeecgBootException("Agent 不存在");
        }
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
        auditLogService.log("agent_unbind_skill", "agent_skill", binding.getId(), binding);
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
}
