package org.jeecg.modules.openclaw.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.openclaw.service.IOpenclawSystemHealthService;
import org.jeecg.modules.openclaw.vo.OpenclawSystemHealthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OpenClaw System Health")
@RestController
@RequestMapping("/openclaw/system")
public class OpenclawSystemHealthController {
    @Autowired
    private IOpenclawSystemHealthService systemHealthService;

    @GetMapping("/health")
    @RequiresPermissions("openclaw:gateway:list")
    public Result<OpenclawSystemHealthVO> health() {
        return Result.OK(systemHealthService.check());
    }
}
