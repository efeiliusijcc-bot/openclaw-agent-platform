package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawAgentCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawAgentEditDTO;
import org.jeecg.modules.openclaw.dto.OpenclawAgentSkillBindDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.service.IOpenclawAgentService;
import org.jeecg.modules.openclaw.service.IOpenclawAgentSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "OpenClaw Agent")
@RestController
@RequestMapping("/openclaw/agent")
public class OpenclawAgentController {
    @Autowired
    private IOpenclawAgentService agentService;
    @Autowired
    private IOpenclawAgentSkillService agentSkillService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:agent:list")
    public Result<IPage<OpenclawAgent>> list(OpenclawAgent agent,
                                             @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                             @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                             HttpServletRequest req) {
        QueryWrapper<OpenclawAgent> queryWrapper = QueryGenerator.initQueryWrapper(agent, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        String userId = permissionService.currentUserIdForQuery();
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(agentService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @PostMapping("/add")
    @AutoLog(value = "OpenClaw Agent create")
    @RequiresPermissions("openclaw:agent:add")
    public Result<OpenclawAgent> add(@RequestBody OpenclawAgentCreateDTO dto) {
        return Result.OK(agentService.createAgent(dto));
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @AutoLog(value = "OpenClaw Agent edit")
    @RequiresPermissions("openclaw:agent:edit")
    public Result<?> edit(@RequestBody OpenclawAgentEditDTO dto) {
        agentService.editAgent(dto);
        return Result.OK("更新成功");
    }

    @DeleteMapping("/delete")
    @AutoLog(value = "OpenClaw Agent delete")
    @RequiresPermissions("openclaw:agent:delete")
    public Result<?> delete(@RequestParam String id) {
        agentService.logicDeleteAgent(id);
        return Result.OK("删除成功");
    }

    @PostMapping("/disable")
    @RequiresPermissions("openclaw:agent:disable")
    public Result<?> disable(@RequestParam String id) {
        agentService.disableAgent(id);
        return Result.OK("禁用成功");
    }

    @PostMapping("/bindSkill")
    @RequiresPermissions("openclaw:agent:bindSkill")
    public Result<?> bindSkill(@RequestBody OpenclawAgentSkillBindDTO dto) {
        agentSkillService.bindSkill(dto.getAgentId(), dto.getSkillId());
        return Result.OK("绑定成功");
    }

    @PostMapping("/unbindSkill")
    @RequiresPermissions("openclaw:agent:unbindSkill")
    public Result<?> unbindSkill(@RequestBody OpenclawAgentSkillBindDTO dto) {
        agentSkillService.unbindSkill(dto.getAgentId(), dto.getSkillId());
        return Result.OK("解绑成功");
    }

    @GetMapping("/queryById")
    @RequiresPermissions("openclaw:agent:list")
    public Result<OpenclawAgent> queryById(@RequestParam String id) {
        OpenclawAgent agent = agentService.getById(id);
        if (agent != null) {
            permissionService.checkOwnerOrAdmin(agent.getUserId());
        }
        return Result.OK(agent);
    }
}
