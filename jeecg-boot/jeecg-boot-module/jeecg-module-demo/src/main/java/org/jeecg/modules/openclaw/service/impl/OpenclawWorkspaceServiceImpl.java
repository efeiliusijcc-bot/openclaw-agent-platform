package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawWorkspaceService;
import org.jeecg.modules.openclaw.vo.OpenclawWorkspaceHealthCheckVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class OpenclawWorkspaceServiceImpl extends ServiceImpl<OpenclawWorkspaceMapper, OpenclawWorkspace> implements IOpenclawWorkspaceService {
    private static final List<String> REQUIRED_FILES = List.of("AGENTS.md", "USER.md", "IDENTITY.md");
    private static final List<String> REQUIRED_DIRS = List.of("skills", "files", "logs", "output");

    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private OpenclawWorkspaceMaterializer workspaceMaterializer;
    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawAgentSkillMapper agentSkillMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;

    @Override
    public OpenclawWorkspace createForAgent(LoginUser user, String agentName, String agentKey) {
        OpenclawWorkspace workspace = new OpenclawWorkspace();
        workspace.setUserId(user.getId());
        workspace.setUsername(user.getUsername());
        workspace.setName(agentName + " Workspace");
        workspace.setWorkspaceKey("ws_" + agentKey);
        workspace.setPath(OpenclawConstants.WORKSPACE_ROOT + "/" + user.getId() + "/" + agentKey + "/workspace");
        workspace.setQuotaSizeMb(1024);
        workspace.setUsedSizeMb(0);
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_CREATING);
        workspace.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(workspace);
        return workspace;
    }

    @Override
    public void markDeleted(String workspaceId) {
        OpenclawWorkspace workspace = getById(workspaceId);
        if (workspace == null) {
            return;
        }
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_DELETED);
        workspace.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(workspace);
    }

    @Override
    public OpenclawWorkspaceHealthCheckVO healthCheck(String workspaceId) {
        OpenclawWorkspace workspace = requireWorkspace(workspaceId);
        permissionService.checkOwnerOrAdmin(workspace.getUserId());
        OpenclawWorkspaceHealthCheckVO result = inspect(workspace);
        updateWorkspaceHealthStatus(workspace, result);
        auditWorkspaceResult("workspace_health_check", workspace, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawWorkspaceHealthCheckVO rematerialize(String workspaceId) {
        OpenclawWorkspace workspace = requireWorkspace(workspaceId);
        permissionService.checkOwnerOrAdmin(workspace.getUserId());
        OpenclawAgent agent = requireAgent(workspace.getId());
        workspaceMaterializer.materialize(agent, workspace);
        OpenclawWorkspaceHealthCheckVO result = inspect(workspace);
        updateWorkspaceHealthStatus(workspace, result);
        auditWorkspaceResult("workspace_rematerialize", workspace, result);
        return result;
    }

    private void auditWorkspaceResult(String action, OpenclawWorkspace workspace, OpenclawWorkspaceHealthCheckVO result) {
        if (result.isHealthy()) {
            auditLogService.logSuccess(action, "workspace", workspace.getId(), result);
        } else {
            auditLogService.logFailure(action, "workspace", workspace.getId(), result);
        }
    }

    private OpenclawWorkspaceHealthCheckVO inspect(OpenclawWorkspace workspace) {
        OpenclawWorkspaceHealthCheckVO result = new OpenclawWorkspaceHealthCheckVO();
        result.setWorkspaceId(workspace.getId());
        result.setWorkspaceKey(workspace.getWorkspaceKey());
        result.setPath(workspace.getPath());
        result.setStatus(workspace.getStatus());
        try {
            Path root = workspaceMaterializer.safeWorkspacePath(workspace.getPath());
            workspaceMaterializer.ensureNoSymbolicLink(root);
            if (!Files.exists(root)) {
                result.getErrors().add("Workspace directory is missing");
            } else if (!Files.isDirectory(root)) {
                result.getErrors().add("Workspace path is not a directory");
            } else {
                result.getCheckedItems().add("workspace directory");
            }
            checkRequiredFiles(root, result);
            checkRequiredDirs(root, result);
            checkBoundSkills(workspace, root, result);
        } catch (JeecgBootException | IOException e) {
            result.getErrors().add(e.getMessage());
        }
        result.setHealthy(result.getErrors().isEmpty());
        return result;
    }

    private void updateWorkspaceHealthStatus(OpenclawWorkspace workspace, OpenclawWorkspaceHealthCheckVO result) {
        workspace.setStatus(result.isHealthy() ? OpenclawConstants.WORKSPACE_STATUS_READY : OpenclawConstants.WORKSPACE_STATUS_ERROR);
        workspace.setRemark(result.isHealthy() ? null : String.join("; ", result.getErrors()));
        updateById(workspace);
        result.setStatus(workspace.getStatus());
    }

    private void checkRequiredFiles(Path root, OpenclawWorkspaceHealthCheckVO result) {
        for (String file : REQUIRED_FILES) {
            Path path = root.resolve(file).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                result.getErrors().add("Missing required file: " + file);
            } else {
                result.getCheckedItems().add(file);
            }
        }
    }

    private void checkRequiredDirs(Path root, OpenclawWorkspaceHealthCheckVO result) throws IOException {
        for (String dir : REQUIRED_DIRS) {
            Path path = root.resolve(dir).normalize();
            if (!path.startsWith(root) || !Files.isDirectory(path)) {
                result.getErrors().add("Missing required directory: " + dir);
            } else if (Files.isSymbolicLink(path)) {
                result.getErrors().add("Directory must not be symbolic link: " + dir);
            } else {
                workspaceMaterializer.ensureNoSymbolicLink(path);
                result.getCheckedItems().add(dir + "/");
            }
        }
    }

    private void checkBoundSkills(OpenclawWorkspace workspace, Path root, OpenclawWorkspaceHealthCheckVO result) {
        OpenclawAgent agent = agentMapper.selectOne(new LambdaQueryWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getWorkspaceId, workspace.getId())
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1"));
        if (agent == null) {
            result.getWarnings().add("No active agent is attached to this workspace");
            return;
        }
        List<OpenclawAgentSkill> bindings = agentSkillMapper.selectList(new LambdaQueryWrapper<OpenclawAgentSkill>()
            .eq(OpenclawAgentSkill::getAgentId, agent.getId())
            .eq(OpenclawAgentSkill::getEnabled, 1)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL));
        Path skillsRoot = root.resolve("skills").normalize();
        for (OpenclawAgentSkill binding : bindings) {
            OpenclawSkill skill = skillMapper.selectById(binding.getSkillId());
            if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
                result.getErrors().add("Bound skill is missing: " + binding.getSkillId());
                continue;
            }
            Path skillPath = skillsRoot.resolve(skill.getSlug()).normalize();
            if (!skillPath.startsWith(skillsRoot) || !Files.isDirectory(skillPath)) {
                result.getErrors().add("Bound skill files are missing: " + skill.getSlug());
            } else {
                result.getCheckedItems().add("skills/" + skill.getSlug());
            }
        }
    }

    private OpenclawWorkspace requireWorkspace(String workspaceId) {
        OpenclawWorkspace workspace = getById(workspaceId);
        if (workspace == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(workspace.getDelFlag())) {
            throw new JeecgBootException("Workspace does not exist");
        }
        return workspace;
    }

    private OpenclawAgent requireAgent(String workspaceId) {
        OpenclawAgent agent = agentMapper.selectOne(new LambdaQueryWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getWorkspaceId, workspaceId)
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1"));
        if (agent == null) {
            throw new JeecgBootException("Workspace has no active agent");
        }
        return agent;
    }
}
