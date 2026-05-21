package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
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

@Tag(name = "OpenClaw Gateway Node")
@RestController
@RequestMapping("/openclaw/gateway")
public class OpenclawGatewayNodeController {
    @Autowired
    private IOpenclawGatewayNodeService gatewayNodeService;
    @Autowired
    private IOpenclawGatewayConfigService gatewayConfigService;

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
        node.setStatus(node.getStatus() == null ? "offline" : node.getStatus());
        fillGatewayDefaults(node);
        node.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        gatewayNodeService.save(node);
        return Result.OK("新增成功");
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:gateway:edit")
    public Result<?> edit(@RequestBody OpenclawGatewayNode node) {
        fillGatewayDefaults(node);
        gatewayNodeService.updateById(node);
        return Result.OK("更新成功");
    }

    @DeleteMapping("/delete")
    @RequiresPermissions("openclaw:gateway:disable")
    public Result<?> delete(@RequestParam String id) {
        gatewayNodeService.logicDeleteNode(id);
        return Result.OK("删除成功");
    }

    @GetMapping("/{id}/configPreview")
    @RequiresPermissions("openclaw:gateway:list")
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
}
