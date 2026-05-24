package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

@Service
public class OpenclawSkillMaterializer {
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;
    @Autowired
    private OpenclawAgentSkillMapper agentSkillMapper;
    @Autowired
    private OpenclawWorkspaceMaterializer workspaceMaterializer;

    public void materializeAgentSkills(OpenclawAgent agent) {
        OpenclawWorkspace workspace = requireWorkspace(agent);
        workspaceMaterializer.materialize(agent, workspace);
        List<OpenclawAgentSkill> bindings = agentSkillMapper.selectList(new LambdaQueryWrapper<OpenclawAgentSkill>()
            .eq(OpenclawAgentSkill::getAgentId, agent.getId())
            .eq(OpenclawAgentSkill::getEnabled, 1)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL));
        for (OpenclawAgentSkill binding : bindings) {
            OpenclawSkill skill = skillMapper.selectById(binding.getSkillId());
            if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
                throw new JeecgBootException("Bound skill does not exist: " + binding.getSkillId());
            }
            if (!OpenclawConstants.STATUS_DISABLED.equals(skill.getStatus())) {
                copySkillToAgent(agent, skill);
            }
        }
    }

    public void copySkillToAgent(OpenclawAgent agent, OpenclawSkill skill) {
        OpenclawWorkspace workspace = requireWorkspace(agent);
        workspaceMaterializer.materialize(agent, workspace);
        Path source = requireSkillSource(skill);
        Path skillsRoot = Paths.get(workspace.getPath()).normalize().resolve("skills").normalize();
        Path target = skillsRoot.resolve(skill.getSlug()).normalize();
        if (!target.startsWith(skillsRoot)) {
            throw new JeecgBootException("Invalid skill slug path: " + skill.getSlug());
        }
        Path temp = skillsRoot.resolve("." + skill.getSlug() + ".tmp-" + System.nanoTime()).normalize();
        Path backup = skillsRoot.resolve("." + skill.getSlug() + ".bak-" + System.currentTimeMillis()).normalize();
        try {
            Files.createDirectories(skillsRoot);
            deleteDirectory(temp);
            copyDirectory(source, temp);
            if (Files.exists(target)) {
                Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteDirectory(backup);
        } catch (IOException e) {
            restoreBackup(target, backup);
            deleteDirectory(temp);
            throw new JeecgBootException("Copy skill to workspace failed: " + e.getMessage(), e);
        }
    }

    public void removeSkillFromAgent(OpenclawAgent agent, OpenclawSkill skill) {
        if (skill == null || !StringUtils.hasText(skill.getSlug())) {
            return;
        }
        OpenclawWorkspace workspace = requireWorkspace(agent);
        Path skillsRoot = Paths.get(workspace.getPath()).normalize().resolve("skills").normalize();
        Path target = skillsRoot.resolve(skill.getSlug()).normalize();
        if (!target.startsWith(skillsRoot)) {
            throw new JeecgBootException("Invalid skill slug path: " + skill.getSlug());
        }
        deleteDirectory(target);
    }

    private OpenclawWorkspace requireWorkspace(OpenclawAgent agent) {
        OpenclawWorkspace workspace = workspaceMapper.selectById(agent.getWorkspaceId());
        if (workspace == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(workspace.getDelFlag())) {
            throw new JeecgBootException("Workspace does not exist for agent: " + agent.getId());
        }
        return workspace;
    }

    private Path requireSkillSource(OpenclawSkill skill) {
        if (skill == null || !StringUtils.hasText(skill.getPath())) {
            throw new JeecgBootException("Skill source path is empty");
        }
        Path source = Paths.get(skill.getPath()).normalize();
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            throw new JeecgBootException("Skill source directory does not exist: " + skill.getId());
        }
        return source;
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path item : walk.sorted(Comparator.comparingInt(Path::getNameCount)).toList()) {
                Path destination = target.resolve(source.relativize(item)).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Invalid copied path: " + destination);
                }
                if (Files.isSymbolicLink(item)) {
                    throw new IOException("Symbolic links are not allowed in skills: " + item);
                }
                if (Files.isDirectory(item)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(item, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void restoreBackup(Path target, Path backup) {
        if (!Files.exists(backup)) {
            return;
        }
        try {
            deleteDirectory(target);
            Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Keep the original exception as the user-facing failure.
        }
    }

    private void deleteDirectory(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Best effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }
}
