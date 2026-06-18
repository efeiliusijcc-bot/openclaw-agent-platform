package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawQuotaUpdateDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentRunMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawUserQuotaMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.vo.OpenclawQuotaUsageVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;

@Service
public class OpenclawUserQuotaServiceImpl extends ServiceImpl<OpenclawUserQuotaMapper, OpenclawUserQuota> implements IOpenclawUserQuotaService {
    private static final Set<String> QUOTA_STATUSES = Set.of(
        OpenclawConstants.STATUS_ENABLED,
        OpenclawConstants.STATUS_DISABLED
    );

    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;
    @Autowired
    private OpenclawAgentRunMapper runMapper;
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @Override
    public OpenclawUserQuota getOrCreateQuota(LoginUser user) {
        OpenclawUserQuota quota = lambdaQuery()
            .eq(OpenclawUserQuota::getUserId, user.getId())
            .eq(OpenclawUserQuota::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .one();
        if (quota != null) {
            return quota;
        }
        quota = new OpenclawUserQuota();
        quota.setUserId(user.getId());
        quota.setUsername(user.getUsername());
        quota.setMaxAgents(5);
        quota.setMaxWorkspaces(5);
        quota.setMaxSkills(20);
        quota.setMaxStorageMb(1024);
        quota.setMaxDailyRuns(100);
        quota.setMaxConcurrentRuns(2);
        quota.setStatus(OpenclawConstants.STATUS_ENABLED);
        quota.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(quota);
        return quota;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuota(OpenclawQuotaUpdateDTO dto) {
        if (!permissionService.isAdmin(permissionService.currentUser())) {
            throw new JeecgBootException("只有 OpenClaw 管理员可以修改用户配额");
        }
        if (!StringUtils.hasText(dto.getUserId())) {
            throw new JeecgBootException("userId 不能为空");
        }
        OpenclawUserQuota quota = null;
        if (StringUtils.hasText(dto.getId())) {
            quota = getById(dto.getId());
        }
        if (quota == null) {
            quota = lambdaQuery().eq(OpenclawUserQuota::getUserId, dto.getUserId())
                .eq(OpenclawUserQuota::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL).one();
        }
        if (quota == null) {
            quota = new OpenclawUserQuota();
            quota.setUserId(dto.getUserId());
            quota.setUsername(dto.getUsername());
            quota.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        }
        JSONObject before = quota.getId() == null ? null : quotaSnapshot(quota);
        boolean created = quota.getId() == null;
        BeanUtils.copyProperties(dto, quota, "id", "userId", "username");
        quota.setUserId(dto.getUserId());
        if (StringUtils.hasText(dto.getUsername())) {
            quota.setUsername(dto.getUsername());
        }
        if (!StringUtils.hasText(quota.getStatus())) {
            quota.setStatus(OpenclawConstants.STATUS_ENABLED);
        }
        if (quota.getMaxAgents() == null) {
            quota.setMaxAgents(5);
        }
        if (quota.getMaxWorkspaces() == null) {
            quota.setMaxWorkspaces(5);
        }
        if (quota.getMaxSkills() == null) {
            quota.setMaxSkills(20);
        }
        if (quota.getMaxStorageMb() == null) {
            quota.setMaxStorageMb(1024);
        }
        if (quota.getMaxDailyRuns() == null) {
            quota.setMaxDailyRuns(100);
        }
        if (quota.getMaxConcurrentRuns() == null) {
            quota.setMaxConcurrentRuns(2);
        }
        validateQuota(quota);
        saveOrUpdate(quota);
        auditLogService.logSuccess(created ? "quota_create" : "quota_update", "quota", quota.getId(), quotaAuditDetail(before, quota));
    }

    @Override
    public OpenclawQuotaUsageVO getMyUsage() {
        LoginUser user = permissionService.currentUser();
        OpenclawQuotaUsageVO vo = new OpenclawQuotaUsageVO();
        vo.setQuota(getOrCreateQuota(user));
        vo.setUsedAgents(Math.toIntExact(agentMapper.selectCount(ownerAgentWrapper(user.getId()))));
        vo.setUsedWorkspaces(Math.toIntExact(workspaceMapper.selectCount(ownerWorkspaceWrapper(user.getId()))));
        vo.setUsedSkills(Math.toIntExact(skillMapper.selectCount(ownerSkillWrapper(user.getId()))));
        vo.setTodayRuns(Math.toIntExact(runMapper.selectCount(todayRunWrapper(user.getId()))));
        vo.setRunningRuns(Math.toIntExact(runMapper.selectCount(runningRunWrapper(user.getId()))));
        return vo;
    }

    private LambdaQueryWrapper<OpenclawAgent> ownerAgentWrapper(String userId) {
        return new LambdaQueryWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getUserId, userId)
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<OpenclawWorkspace> ownerWorkspaceWrapper(String userId) {
        return new LambdaQueryWrapper<OpenclawWorkspace>()
            .eq(OpenclawWorkspace::getUserId, userId)
            .eq(OpenclawWorkspace::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<OpenclawSkill> ownerSkillWrapper(String userId) {
        return new LambdaQueryWrapper<OpenclawSkill>()
            .eq(OpenclawSkill::getOwnerUserId, userId)
            .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<OpenclawAgentRun> todayRunWrapper(String userId) {
        Date start = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        return new LambdaQueryWrapper<OpenclawAgentRun>()
            .eq(OpenclawAgentRun::getUserId, userId)
            .ge(OpenclawAgentRun::getCreateTime, start)
            .eq(OpenclawAgentRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private LambdaQueryWrapper<OpenclawAgentRun> runningRunWrapper(String userId) {
        return new LambdaQueryWrapper<OpenclawAgentRun>()
            .eq(OpenclawAgentRun::getUserId, userId)
            .in(OpenclawAgentRun::getStatus, OpenclawConstants.RUN_STATUS_QUEUED, OpenclawConstants.RUN_STATUS_RUNNING)
            .eq(OpenclawAgentRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private void validateQuota(OpenclawUserQuota quota) {
        quota.setStatus(normalizeQuotaStatus(quota.getStatus()));
        validateNonNegative("maxAgents", quota.getMaxAgents());
        validateNonNegative("maxWorkspaces", quota.getMaxWorkspaces());
        validateNonNegative("maxSkills", quota.getMaxSkills());
        validateNonNegative("maxStorageMb", quota.getMaxStorageMb());
        validateNonNegative("maxDailyRuns", quota.getMaxDailyRuns());
        validateNonNegative("maxConcurrentRuns", quota.getMaxConcurrentRuns());

        int usedAgents = Math.toIntExact(agentMapper.selectCount(ownerAgentWrapper(quota.getUserId())));
        int usedWorkspaces = Math.toIntExact(workspaceMapper.selectCount(ownerWorkspaceWrapper(quota.getUserId())));
        int usedSkills = Math.toIntExact(skillMapper.selectCount(ownerSkillWrapper(quota.getUserId())));
        int todayRuns = Math.toIntExact(runMapper.selectCount(todayRunWrapper(quota.getUserId())));
        int runningRuns = Math.toIntExact(runMapper.selectCount(runningRunWrapper(quota.getUserId())));
        validateNotBelowUsage("maxAgents", quota.getMaxAgents(), usedAgents);
        validateNotBelowUsage("maxWorkspaces", quota.getMaxWorkspaces(), usedWorkspaces);
        validateNotBelowUsage("maxSkills", quota.getMaxSkills(), usedSkills);
        validateNotBelowUsage("maxDailyRuns", quota.getMaxDailyRuns(), todayRuns);
        validateNotBelowUsage("maxConcurrentRuns", quota.getMaxConcurrentRuns(), runningRuns);
    }

    private String normalizeQuotaStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toLowerCase() : OpenclawConstants.STATUS_ENABLED;
        if (!QUOTA_STATUSES.contains(normalized)) {
            throw new JeecgBootException("Unsupported quota status: " + status);
        }
        return normalized;
    }

    private void validateNonNegative(String label, Integer value) {
        if (value != null && value < 0) {
            throw new JeecgBootException(label + " must not be negative");
        }
    }

    private void validateNotBelowUsage(String label, Integer limit, int used) {
        if (limit != null && limit < used) {
            throw new JeecgBootException(label + " cannot be lower than current usage: " + used);
        }
    }

    private JSONObject quotaAuditDetail(JSONObject before, OpenclawUserQuota after) {
        JSONObject detail = new JSONObject(true);
        detail.put("userId", after.getUserId());
        detail.put("username", after.getUsername());
        detail.put("before", before);
        detail.put("after", quotaSnapshot(after));
        return detail;
    }

    private JSONObject quotaSnapshot(OpenclawUserQuota quota) {
        JSONObject snapshot = new JSONObject(true);
        snapshot.put("id", quota.getId());
        snapshot.put("userId", quota.getUserId());
        snapshot.put("username", quota.getUsername());
        snapshot.put("maxAgents", quota.getMaxAgents());
        snapshot.put("maxWorkspaces", quota.getMaxWorkspaces());
        snapshot.put("maxSkills", quota.getMaxSkills());
        snapshot.put("maxStorageMb", quota.getMaxStorageMb());
        snapshot.put("maxDailyRuns", quota.getMaxDailyRuns());
        snapshot.put("maxConcurrentRuns", quota.getMaxConcurrentRuns());
        snapshot.put("status", quota.getStatus());
        return snapshot;
    }
}
