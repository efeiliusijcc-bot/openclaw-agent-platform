package org.jeecg.modules.openclaw.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawGatewayConfigService;
import org.jeecg.modules.openclaw.service.IOpenclawGatewayNodeService;
import org.jeecg.modules.openclaw.vo.OpenclawGatewaySyncResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;

@Tag(name = "OpenClaw Gateway Node")
@RestController
@RequestMapping("/openclaw/gateway")
public class OpenclawGatewayNodeController {
    private static final Set<String> GATEWAY_STATUSES = Set.of(
        OpenclawConstants.GATEWAY_STATUS_ONLINE,
        OpenclawConstants.GATEWAY_STATUS_OFFLINE,
        OpenclawConstants.GATEWAY_STATUS_DISABLED
    );

    @Autowired
    private IOpenclawGatewayNodeService gatewayNodeService;
    @Autowired
    private IOpenclawGatewayConfigService gatewayConfigService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private OpenclawAgentMapper agentMapper;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:gateway:list")
    public Result<IPage<OpenclawGatewayNode>> list(OpenclawGatewayNode node,
                                                   @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                   HttpServletRequest req) {
        QueryWrapper<OpenclawGatewayNode> queryWrapper = QueryGenerator.initQueryWrapper(node, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        queryWrapper.orderByDesc("create_time");
        return Result.OK(gatewayNodeService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @PostMapping("/add")
    @RequiresPermissions("openclaw:gateway:add")
    public Result<?> add(@RequestBody OpenclawGatewayNode node) {
        node.setCurrentAgents(0);
        node.setCurrentRunning(0);
        node.setStatus(StringUtils.hasText(node.getStatus()) ? node.getStatus() : OpenclawConstants.GATEWAY_STATUS_OFFLINE);
        fillGatewayDefaults(node);
        validateGatewayNode(node);
        node.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        gatewayNodeService.save(node);
        auditLogService.logSuccess("gateway_node_add", "gateway", node.getId(), gatewayAuditDetail(node));
        return Result.OK("新增成功");
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:gateway:edit")
    public Result<?> edit(@RequestBody OpenclawGatewayNode node) {
        if (node == null || !StringUtils.hasText(node.getId())) {
            throw new JeecgBootException("Gateway id is required");
        }
        OpenclawGatewayNode existing = gatewayNodeService.getById(node.getId());
        if (existing == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(existing.getDelFlag())) {
            throw new JeecgBootException("Gateway node does not exist");
        }
        JSONObject before = gatewayAuditDetail(existing);
        applyEditableGatewayFields(existing, node);
        fillGatewayDefaults(existing);
        validateGatewayNode(existing);
        boolean syncInvalidated = gatewayConfigChanged(before, existing);
        if (syncInvalidated) {
            existing.setLastSyncStatus(OpenclawConstants.RUN_STATUS_FAILED);
            existing.setLastSyncMessage("Gateway node configuration changed; sync required.");
            existing.setLastSyncChecksum(null);
            existing.setRestartRequired(1);
        }
        gatewayNodeService.updateById(existing);
        int clearedAgents = syncInvalidated ? clearGatewayAgentBindings(existing.getId()) : 0;
        auditLogService.logSuccess("gateway_node_edit", "gateway", existing.getId(), gatewayAuditDetail(before, existing, syncInvalidated, clearedAgents));
        return Result.OK("更新成功");
    }

    @DeleteMapping("/delete")
    @RequiresPermissions("openclaw:gateway:disable")
    public Result<?> delete(@RequestParam String id) {
        gatewayNodeService.logicDeleteNode(id);
        JSONObject detail = new JSONObject();
        detail.put("gatewayId", id);
        auditLogService.logSuccess("gateway_node_disable", "gateway", id, detail);
        return Result.OK("删除成功");
    }

    @GetMapping("/{id}/configPreview")
    @RequiresPermissions("openclaw:gateway:preview")
    public Result<OpenclawGatewaySyncResultVO> configPreview(@PathVariable String id) {
        return Result.OK(gatewayConfigService.preview(id));
    }

    @PostMapping("/{id}/sync")
    @RequiresPermissions("openclaw:gateway:sync")
    public Result<OpenclawGatewaySyncResultVO> sync(@PathVariable String id) {
        return Result.OK(gatewayConfigService.sync(id));
    }

    private void fillGatewayDefaults(OpenclawGatewayNode node) {
        if (!StringUtils.hasText(node.getConfigPath())) {
            node.setConfigPath(OpenclawConstants.DEFAULT_GATEWAY_CONFIG_PATH);
        }
        if (!StringUtils.hasText(node.getWorkspaceRoot())) {
            node.setWorkspaceRoot(OpenclawConstants.WORKSPACE_ROOT);
        }
        if (node.getRestartRequired() == null) {
            node.setRestartRequired(1);
        }
    }

    private void validateGatewayNode(OpenclawGatewayNode node) {
        if (!StringUtils.hasText(node.getName())) {
            throw new JeecgBootException("Gateway name is required");
        }
        node.setStatus(normalizeGatewayStatus(node.getStatus()));
        validateGatewayBaseUrl(node.getBaseUrl());
        validateAbsoluteDirectory("Gateway workspace root", node.getWorkspaceRoot());
        validateGeneratedConfigPath(node.getConfigPath());
        validateNonNegative("maxAgents", node.getMaxAgents());
        validateNonNegative("maxConcurrentRuns", node.getMaxConcurrentRuns());
    }

    private String normalizeGatewayStatus(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toLowerCase() : OpenclawConstants.GATEWAY_STATUS_OFFLINE;
        if (!GATEWAY_STATUSES.contains(normalized)) {
            throw new JeecgBootException("Unsupported Gateway status: " + status);
        }
        return normalized;
    }

    private void validateGatewayBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new JeecgBootException("Gateway base URL is required");
        }
        try {
            URI uri = new URI(baseUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new JeecgBootException("Gateway base URL must use http or https");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new JeecgBootException("Gateway base URL must include host");
            }
        } catch (URISyntaxException e) {
            throw new JeecgBootException("Gateway base URL is invalid: " + baseUrl, e);
        }
    }

    private void validateAbsoluteDirectory(String label, String value) {
        if (!StringUtils.hasText(value)) {
            throw new JeecgBootException(label + " is required");
        }
        Path path = Paths.get(value).normalize();
        if (!path.isAbsolute()) {
            throw new JeecgBootException(label + " must be an absolute path");
        }
    }

    private void validateGeneratedConfigPath(String value) {
        if (!StringUtils.hasText(value)) {
            throw new JeecgBootException("Gateway config path is required");
        }
        Path path = Paths.get(value).normalize();
        if (!path.isAbsolute()) {
            throw new JeecgBootException("Gateway config path must be an absolute path");
        }
        if (path.getParent() == null) {
            throw new JeecgBootException("Gateway config path must include parent directory");
        }
        String fileName = path.getFileName().toString();
        if ("openclaw.json".equals(fileName)) {
            throw new JeecgBootException("Gateway config path must point to the generated agents include file, not main openclaw.json");
        }
        if (!fileName.endsWith(".json")) {
            throw new JeecgBootException("Gateway config path must be a json file");
        }
    }

    private void validateNonNegative(String label, Integer value) {
        if (value != null && value < 0) {
            throw new JeecgBootException(label + " must not be negative");
        }
    }

    private void applyEditableGatewayFields(OpenclawGatewayNode target, OpenclawGatewayNode source) {
        target.setName(source.getName());
        target.setBaseUrl(source.getBaseUrl());
        target.setStatus(source.getStatus());
        target.setMaxAgents(source.getMaxAgents());
        target.setMaxConcurrentRuns(source.getMaxConcurrentRuns());
        target.setConfigPath(source.getConfigPath());
        target.setWorkspaceRoot(source.getWorkspaceRoot());
        target.setRemark(source.getRemark());
    }

    private boolean gatewayConfigChanged(JSONObject before, OpenclawGatewayNode after) {
        return !Objects.equals(before.getString("baseUrl"), after.getBaseUrl())
            || !Objects.equals(before.getString("status"), after.getStatus())
            || !Objects.equals(before.getString("configPath"), after.getConfigPath())
            || !Objects.equals(before.getString("workspaceRoot"), after.getWorkspaceRoot());
    }

    private int clearGatewayAgentBindings(String gatewayId) {
        return agentMapper.update(null, new LambdaUpdateWrapper<OpenclawAgent>()
            .eq(OpenclawAgent::getGatewayId, gatewayId)
            .eq(OpenclawAgent::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .set(OpenclawAgent::getGatewayId, null));
    }

    private JSONObject gatewayAuditDetail(OpenclawGatewayNode node) {
        JSONObject detail = new JSONObject();
        detail.put("gatewayId", node.getId());
        detail.put("name", node.getName());
        detail.put("baseUrl", node.getBaseUrl());
        detail.put("status", node.getStatus());
        detail.put("configPath", node.getConfigPath());
        detail.put("workspaceRoot", node.getWorkspaceRoot());
        return detail;
    }

    private JSONObject gatewayAuditDetail(JSONObject before, OpenclawGatewayNode after, boolean syncInvalidated, int clearedAgents) {
        JSONObject detail = new JSONObject(true);
        detail.put("before", before);
        detail.put("after", gatewayAuditDetail(after));
        detail.put("syncInvalidated", syncInvalidated);
        detail.put("agentBindingsCleared", clearedAgents);
        return detail;
    }
}
