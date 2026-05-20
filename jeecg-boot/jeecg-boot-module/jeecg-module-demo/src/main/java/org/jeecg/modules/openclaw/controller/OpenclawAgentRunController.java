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
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.service.IOpenclawAgentRunService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Agent Run")
@RestController
@RequestMapping("/openclaw/run")
public class OpenclawAgentRunController {
    @Autowired
    private IOpenclawAgentRunService runService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:run:list")
    public Result<IPage<OpenclawAgentRun>> list(OpenclawAgentRun run,
                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                HttpServletRequest req) {
        QueryWrapper<OpenclawAgentRun> queryWrapper = QueryGenerator.initQueryWrapper(run, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        String userId = permissionService.currentUserIdForQuery();
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(runService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }
}
