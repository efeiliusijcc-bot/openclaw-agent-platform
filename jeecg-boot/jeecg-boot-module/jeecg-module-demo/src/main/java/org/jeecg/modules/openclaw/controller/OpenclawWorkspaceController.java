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
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Workspace")
@RestController
@RequestMapping("/openclaw/workspace")
public class OpenclawWorkspaceController {
    @Autowired
    private IOpenclawWorkspaceService workspaceService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:workspace:list")
    public Result<IPage<OpenclawWorkspace>> list(OpenclawWorkspace workspace,
                                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                 HttpServletRequest req) {
        QueryWrapper<OpenclawWorkspace> queryWrapper = QueryGenerator.initQueryWrapper(workspace, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        String userId = permissionService.currentUserIdForQuery();
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(workspaceService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @GetMapping("/queryById")
    @RequiresPermissions("openclaw:workspace:list")
    public Result<OpenclawWorkspace> queryById(@RequestParam String id) {
        OpenclawWorkspace workspace = workspaceService.getById(id);
        if (workspace != null) {
            permissionService.checkOwnerOrAdmin(workspace.getUserId());
        }
        return Result.OK(workspace);
    }
}
