package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.entity.OpenclawAuditLog;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Audit Log")
@RestController
@RequestMapping("/openclaw/audit")
public class OpenclawAuditLogController {
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:audit:list")
    public Result<IPage<OpenclawAuditLog>> list(OpenclawAuditLog log,
                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                HttpServletRequest req) {
        QueryWrapper<OpenclawAuditLog> queryWrapper = QueryGenerator.initQueryWrapper(log, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        return Result.OK(auditLogService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }
}
