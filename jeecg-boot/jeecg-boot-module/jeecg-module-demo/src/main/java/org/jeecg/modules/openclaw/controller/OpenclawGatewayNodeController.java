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
import org.jeecg.modules.openclaw.service.IOpenclawGatewayNodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Gateway Node")
@RestController
@RequestMapping("/openclaw/gateway")
public class OpenclawGatewayNodeController {
    @Autowired
    private IOpenclawGatewayNodeService gatewayNodeService;

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
        node.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        gatewayNodeService.save(node);
        return Result.OK("新增成功");
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:gateway:edit")
    public Result<?> edit(@RequestBody OpenclawGatewayNode node) {
        gatewayNodeService.updateById(node);
        return Result.OK("更新成功");
    }

    @DeleteMapping("/delete")
    @RequiresPermissions("openclaw:gateway:disable")
    public Result<?> delete(@RequestParam String id) {
        gatewayNodeService.logicDeleteNode(id);
        return Result.OK("删除成功");
    }
}
