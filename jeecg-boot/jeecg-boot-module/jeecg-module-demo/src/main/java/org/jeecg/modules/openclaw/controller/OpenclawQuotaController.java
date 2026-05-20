package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawQuotaUpdateDTO;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.vo.OpenclawQuotaUsageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Quota")
@RestController
@RequestMapping("/openclaw/quota")
public class OpenclawQuotaController {
    @Autowired
    private IOpenclawUserQuotaService quotaService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:quota:list")
    public Result<IPage<OpenclawUserQuota>> list(OpenclawUserQuota quota,
                                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                 @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                 HttpServletRequest req) {
        if (!permissionService.isAdmin(permissionService.currentUser())) {
            throw new JeecgBootException("只有 OpenClaw 管理员可以查看全部配额");
        }
        QueryWrapper<OpenclawUserQuota> queryWrapper = QueryGenerator.initQueryWrapper(quota, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        queryWrapper.orderByDesc("create_time");
        return Result.OK(quotaService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @GetMapping("/my")
    @RequiresPermissions("openclaw:quota:my")
    public Result<OpenclawUserQuota> my() {
        return Result.OK(quotaService.getOrCreateQuota(permissionService.currentUser()));
    }

    @GetMapping("/myUsage")
    @RequiresPermissions("openclaw:quota:my")
    public Result<OpenclawQuotaUsageVO> myUsage() {
        return Result.OK(quotaService.getMyUsage());
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:quota:edit")
    public Result<?> edit(@RequestBody OpenclawQuotaUpdateDTO dto) {
        quotaService.updateQuota(dto);
        return Result.OK("配额已更新");
    }
}
