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
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;
import org.jeecg.modules.openclaw.service.IOpenclawAgentService;
import org.jeecg.modules.openclaw.service.IOpenclawAgentSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Agent Skill")
@RestController
@RequestMapping("/openclaw/agentSkill")
public class OpenclawAgentSkillController {
    @Autowired
    private IOpenclawAgentSkillService agentSkillService;
    @Autowired
    private IOpenclawAgentService agentService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:agent:bindSkill")
    public Result<IPage<OpenclawAgentSkill>> list(OpenclawAgentSkill binding,
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                  HttpServletRequest req) {
        QueryWrapper<OpenclawAgentSkill> queryWrapper = QueryGenerator.initQueryWrapper(binding, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        String userId = permissionService.currentUserIdForQuery();
        if (userId != null) {
            if (binding.getAgentId() == null) {
                throw new JeecgBootException("普通用户查询绑定关系必须指定 agentId");
            }
            OpenclawAgent agent = agentService.getById(binding.getAgentId());
            permissionService.checkOwnerOrAdmin(agent == null ? null : agent.getUserId());
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(agentSkillService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }
}
