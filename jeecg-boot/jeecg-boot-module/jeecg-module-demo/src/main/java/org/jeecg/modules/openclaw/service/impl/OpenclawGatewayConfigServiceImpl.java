package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawGatewayNodeMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawGatewayConfigService;
import org.jeecg.modules.openclaw.vo.OpenclawGatewaySyncResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class OpenclawGatewayConfigServiceImpl implements IOpenclawGatewayConfigService {
    @Autowired
    private OpenclawGatewayNodeMapper gatewayNodeMapper;
    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private OpenclawAgentSkillMapper agentSkillMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;
    @Autowired
    private OpenclawWorkspaceMaterializer workspaceMaterializer;
    @Autowired
    private OpenclawSkillMaterializer skillMaterializer;
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @Override
    public OpenclawGatewaySyncResultVO preview(String gatewayId) {
        OpenclawGatewayNode node = requireNode(gatewayId);
        precheckNode(node);
        RenderedConfig rendered = render(node, false);
        return toResult(node, rendered, "Gateway config preview generated", true);
    }

    @Override
    public OpenclawGatewaySyncResultVO sync(String gatewayId) {
        OpenclawGatewayNode node = requireNode(gatewayId);
        Path backup = null;
        try {
            auditLogService.logSuccess("gateway_sync_start", "gateway", node.getId(), syncStartDetail(node));
            precheckNode(node);
            RenderedConfig rendered = render(node, true);
            PublishResult publishResult = writeConfig(configPath(node), rendered);
            backup = publishResult.backup;
            node.setConfigPath(configPath(node));
            node.setWorkspaceRoot(workspaceRoot(node));
            node.setCurrentAgents(rendered.agentCount);
            node.setLastSyncTime(new Date());
            node.setLastSyncStatus("success");
            node.setLastSyncMessage("Config atomically written. Restart OpenClaw Gateway manually.");
            node.setLastSyncChecksum(rendered.checksum);
            node.setRestartRequired(1);
            gatewayNodeMapper.updateById(node);
            bindAgentsToGateway(rendered.agentIds, node.getId());
            OpenclawGatewaySyncResultVO result = toResult(node, rendered, "Config atomically written. Restart OpenClaw Gateway manually.", false);
            auditLogService.logSuccess("gateway_sync", "gateway", node.getId(), syncAuditDetail(result, "success"));
            return result;
        } catch (Exception e) {
            restoreBackup(configPath(node), backup);
            markFailed(node, e);
            auditLogService.logFailure("gateway_sync", "gateway", node.getId(), failureDetail(e));
            if (e instanceof JeecgBootException) {
                throw (JeecgBootException) e;
            }
            throw new JeecgBootException("Sync OpenClaw Gateway config failed: " + e.getMessage(), e);
        }
    }

    private RenderedConfig render(OpenclawGatewayNode node, boolean materializeFiles) {
        String workspaceRoot = workspaceRoot(node);
        ensureWorkspaceRoot(workspaceRoot, materializeFiles);
        JSONObject root = new JSONObject(true);
        JSONObject defaults = new JSONObject(true);
        defaults.put("workspace", workspaceRoot);
        JSONArray list = new JSONArray();
        List<String> renderedAgentIds = new ArrayList<>();
        Set<String> uniqueSkills = new LinkedHashSet<>();

        List<OpenclawAgent> agents = agentMapper.selectList(new LambdaQueryWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .eq(OpenclawAgent::getStatus, OpenclawConstants.AGENT_STATUS_ENABLED));
        agents.sort(Comparator.comparing(OpenclawAgent::getAgentKey, Comparator.nullsLast(String::compareTo)));

        for (OpenclawAgent agent : agents) {
            OpenclawWorkspace workspace = requireWorkspace(agent);
            precheckWorkspace(agent, workspace, workspaceRoot);
            if (materializeFiles) {
                workspaceMaterializer.materialize(agent, workspace);
            }
            renderedAgentIds.add(agent.getId());
            List<OpenclawSkill> skills = enabledSkills(agent);
            if (materializeFiles) {
                for (OpenclawSkill skill : skills) {
                    skillMaterializer.copySkillToAgent(agent, skill);
                }
            }
            JSONArray skillSlugs = new JSONArray();
            for (OpenclawSkill skill : skills) {
                skillSlugs.add(skill.getSlug());
                uniqueSkills.add(skill.getSlug());
            }
            JSONObject item = new JSONObject(true);
            item.put("id", agent.getAgentKey());
            item.put("workspace", workspace.getPath());
            item.put("skills", skillSlugs);
            JSONObject identity = new JSONObject(true);
            identity.put("name", agent.getName());
            item.put("identity", identity);
            list.add(item);
        }

        root.put("defaults", defaults);
        root.put("list", list);
        JSONObject previewRoot = new JSONObject(true);
        previewRoot.put("agents", root);
        String content = JSON.toJSONString(root, SerializerFeature.PrettyFormat);
        String previewContent = JSON.toJSONString(previewRoot, SerializerFeature.PrettyFormat);
        RenderedConfig rendered = new RenderedConfig();
        rendered.content = content + System.lineSeparator();
        rendered.previewContent = previewContent + System.lineSeparator();
        rendered.agentCount = agents.size();
        rendered.skillCount = uniqueSkills.size();
        rendered.agentIds = renderedAgentIds;
        rendered.checksum = sha256(rendered.content);
        validateGeneratedJson(rendered.content);
        return rendered;
    }

    private void bindAgentsToGateway(List<String> agentIds, String gatewayId) {
        for (String agentId : agentIds) {
            OpenclawAgent update = new OpenclawAgent();
            update.setId(agentId);
            update.setGatewayId(gatewayId);
            agentMapper.updateById(update);
        }
    }

    private OpenclawGatewayNode requireNode(String gatewayId) {
        OpenclawGatewayNode node = gatewayNodeMapper.selectById(gatewayId);
        if (node == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(node.getDelFlag())) {
            throw new JeecgBootException("Gateway node does not exist");
        }
        return node;
    }

    private void precheckNode(OpenclawGatewayNode node) {
        if ("disabled".equalsIgnoreCase(node.getStatus()) || OpenclawConstants.STATUS_DISABLED.equalsIgnoreCase(node.getStatus())) {
            throw new JeecgBootException("Gateway node is disabled: " + node.getId());
        }
        Path config = configPathAsPath(node);
        validateGeneratedConfigPath(config);
        Path parent = config.getParent();
        if (parent == null) {
            throw new JeecgBootException("Gateway config path must include parent directory");
        }
        if (Files.exists(parent) && !Files.isDirectory(parent)) {
            throw new JeecgBootException("Gateway config parent is not a directory: " + parent);
        }
    }

    private OpenclawWorkspace requireWorkspace(OpenclawAgent agent) {
        OpenclawWorkspace workspace = workspaceMapper.selectById(agent.getWorkspaceId());
        if (workspace == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(workspace.getDelFlag())) {
            throw new JeecgBootException("Workspace does not exist for agent: " + agent.getId());
        }
        if (!StringUtils.hasText(workspace.getPath())) {
            throw new JeecgBootException("Workspace path is empty for agent: " + agent.getId());
        }
        return workspace;
    }

    private void precheckWorkspace(OpenclawAgent agent, OpenclawWorkspace workspace, String workspaceRoot) {
        Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
        Path path = Paths.get(workspace.getPath()).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new JeecgBootException("Workspace path is outside gateway workspace root for agent: " + agent.getId());
        }
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new JeecgBootException("Workspace directory does not exist for agent: " + agent.getId());
        }
        try {
            workspaceMaterializer.ensureNoSymbolicLink(path);
        } catch (IOException e) {
            throw new JeecgBootException("Workspace path is not safe for agent: " + agent.getId() + ", " + e.getMessage(), e);
        }
    }

    private List<OpenclawSkill> enabledSkills(OpenclawAgent agent) {
        List<OpenclawAgentSkill> bindings = agentSkillMapper.selectList(new LambdaQueryWrapper<OpenclawAgentSkill>()
            .eq(OpenclawAgentSkill::getAgentId, agent.getId())
            .eq(OpenclawAgentSkill::getEnabled, 1)
            .eq(OpenclawAgentSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL));
        List<OpenclawSkill> skills = new ArrayList<>();
        for (OpenclawAgentSkill binding : bindings) {
            OpenclawSkill skill = skillMapper.selectById(binding.getSkillId());
            if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
                throw new JeecgBootException("Bound skill does not exist: " + binding.getSkillId());
            }
            if (!OpenclawConstants.STATUS_DISABLED.equals(skill.getStatus())) {
                skills.add(skill);
            }
        }
        skills.sort(Comparator.comparing(OpenclawSkill::getSlug, Comparator.nullsLast(String::compareTo)));
        return skills;
    }

    private void ensureWorkspaceRoot(String workspaceRoot, boolean createIfMissing) {
        try {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            if (createIfMissing) {
                Files.createDirectories(root);
            }
            if (!Files.exists(root) || !Files.isDirectory(root)) {
                throw new JeecgBootException("Workspace root does not exist: " + workspaceRoot);
            }
            workspaceMaterializer.ensureNoSymbolicLink(root);
        } catch (IOException e) {
            throw new JeecgBootException("Workspace root is not writable: " + workspaceRoot, e);
        }
    }

    private PublishResult writeConfig(String configPath, RenderedConfig rendered) throws IOException {
        if (!StringUtils.hasText(configPath)) {
            throw new JeecgBootException("Gateway config path is empty");
        }
        Path target = Paths.get(configPath).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new JeecgBootException("Gateway config path must include parent directory");
        }
        validateGeneratedConfigPath(target);
        Files.createDirectories(parent);
        workspaceMaterializer.ensureNoSymbolicLink(parent);
        if (!Files.isWritable(parent)) {
            throw new JeecgBootException("Gateway config parent directory is not writable: " + parent);
        }
        Path temp = parent.resolve(target.getFileName().toString() + ".tmp-" + IdWorker.getIdStr());
        Path backup = parent.resolve(target.getFileName().toString() + ".bak." + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        Files.writeString(temp, rendered.content, StandardCharsets.UTF_8);
        validateGeneratedJson(Files.readString(temp, StandardCharsets.UTF_8));
        if (Files.exists(target)) {
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveError) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        validateGeneratedJson(Files.readString(target, StandardCharsets.UTF_8));
        PublishResult result = new PublishResult();
        result.backup = Files.exists(backup) ? backup : null;
        return result;
    }

    private void validateGeneratedConfigPath(Path target) {
        String fileName = target.getFileName().toString();
        if ("openclaw.json".equals(fileName)) {
            throw new JeecgBootException("Gateway config path must point to the generated agents include file, not main openclaw.json");
        }
        if (!fileName.endsWith(".json")) {
            throw new JeecgBootException("Gateway config path must be a json file");
        }
    }

    private String configPath(OpenclawGatewayNode node) {
        return StringUtils.hasText(node.getConfigPath()) ? node.getConfigPath() : OpenclawConstants.DEFAULT_GATEWAY_CONFIG_PATH;
    }

    private Path configPathAsPath(OpenclawGatewayNode node) {
        return Paths.get(configPath(node)).toAbsolutePath().normalize();
    }

    private String workspaceRoot(OpenclawGatewayNode node) {
        return StringUtils.hasText(node.getWorkspaceRoot()) ? node.getWorkspaceRoot() : OpenclawConstants.WORKSPACE_ROOT;
    }

    private OpenclawGatewaySyncResultVO toResult(OpenclawGatewayNode node, RenderedConfig rendered, String message, boolean includeContent) {
        OpenclawGatewaySyncResultVO result = new OpenclawGatewaySyncResultVO();
        result.setGatewayId(node.getId());
        result.setConfigPath(configPath(node));
        result.setWorkspaceRoot(workspaceRoot(node));
        result.setAgentCount(rendered.agentCount);
        result.setSkillCount(rendered.skillCount);
        result.setChecksum(rendered.checksum);
        result.setRestartRequired(true);
        result.setMessage(message);
        result.setContent(includeContent ? rendered.previewContent : null);
        return result;
    }

    private void markFailed(OpenclawGatewayNode node, Exception e) {
        node.setConfigPath(configPath(node));
        node.setWorkspaceRoot(workspaceRoot(node));
        node.setLastSyncTime(new Date());
        node.setLastSyncStatus("failed");
        node.setLastSyncMessage(trim(e.getMessage(), 2000));
        node.setRestartRequired(1);
        gatewayNodeMapper.updateById(node);
    }

    private JSONObject failureDetail(Exception e) {
        JSONObject detail = new JSONObject(true);
        detail.put("status", "failed");
        detail.put("message", trim(e.getMessage(), 2000));
        return detail;
    }

    private JSONObject syncAuditDetail(OpenclawGatewaySyncResultVO result, String status) {
        JSONObject detail = new JSONObject(true);
        detail.put("status", status);
        detail.put("gatewayId", result.getGatewayId());
        detail.put("configPath", result.getConfigPath());
        detail.put("workspaceRoot", result.getWorkspaceRoot());
        detail.put("agentCount", result.getAgentCount());
        detail.put("skillCount", result.getSkillCount());
        detail.put("checksum", result.getChecksum());
        detail.put("restartRequired", result.getRestartRequired());
        detail.put("message", result.getMessage());
        return detail;
    }

    private JSONObject syncStartDetail(OpenclawGatewayNode node) {
        JSONObject detail = new JSONObject(true);
        detail.put("gatewayId", node.getId());
        detail.put("name", node.getName());
        detail.put("configPath", configPath(node));
        detail.put("workspaceRoot", workspaceRoot(node));
        detail.put("previousLastSyncStatus", node.getLastSyncStatus());
        detail.put("previousLastSyncTime", node.getLastSyncTime());
        return detail;
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new JeecgBootException("Current JDK does not support SHA-256", e);
        }
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void validateGeneratedJson(String content) {
        try {
            JSONObject root = JSON.parseObject(content);
            if (!root.containsKey("defaults") || !root.containsKey("list")) {
                throw new JeecgBootException("Generated Gateway config must contain defaults and list");
            }
            if (!(root.get("list") instanceof JSONArray)) {
                throw new JeecgBootException("Generated Gateway config list must be an array");
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("Generated Gateway config is not valid JSON: " + e.getMessage(), e);
        }
    }

    private void restoreBackup(String configPath, Path backup) {
        if (backup == null || !Files.exists(backup)) {
            return;
        }
        try {
            Files.copy(backup, Paths.get(configPath).toAbsolutePath().normalize(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Keep the original sync failure as the user-facing error.
        }
    }

    private static class RenderedConfig {
        private String content;
        private String previewContent;
        private List<String> agentIds = new ArrayList<>();
        private int agentCount;
        private int skillCount;
        private String checksum;
    }

    private static class PublishResult {
        private Path backup;
    }
}
