package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillImportResultVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillQualityCheckVO;
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
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "OpenClaw Skill")
@RestController
@RequestMapping("/openclaw/skill")
public class OpenclawSkillController {
    @Autowired
    private IOpenclawSkillService skillService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:skill:list")
    public Result<IPage<OpenclawSkill>> list(OpenclawSkill skill,
                                             @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                             @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                             HttpServletRequest req) {
        QueryWrapper<OpenclawSkill> queryWrapper = QueryGenerator.initQueryWrapper(skill, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        if (!permissionService.isSkillReviewer(permissionService.currentUser())) {
            String userId = permissionService.currentUserIdForQuery();
            if (userId != null) {
                queryWrapper.eq("owner_user_id", userId);
            }
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(skillService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @PostMapping("/add")
    @RequiresPermissions("openclaw:skill:add")
    public Result<?> add(@RequestBody OpenclawSkill skill) {
        if (!StringUtils.hasText(skill.getName())) {
            throw new JeecgBootException("Skill name is required");
        }
        skillService.createDraftSkill(skill);
        return Result.OK("Created successfully");
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:skill:edit")
    public Result<?> edit(@RequestBody OpenclawSkill request) {
        skillService.updateSkillMetadata(request);
        return Result.OK("Updated successfully");
    }

    @PostMapping("/import")
    @RequiresPermissions("openclaw:skill:import")
    public Result<OpenclawSkillImportResultVO> importSkill(@RequestParam("file") MultipartFile file) {
        return Result.OK(skillService.importSkill(file));
    }

    @GetMapping("/{id}/export")
    @RequiresPermissions("openclaw:skill:export")
    public void exportSkill(@PathVariable String id, HttpServletResponse response) {
        skillService.exportSkill(id, response);
    }

    @GetMapping("/{id}/quality-check")
    @RequiresPermissions("openclaw:skill:quality")
    public Result<OpenclawSkillQualityCheckVO> qualityCheck(@PathVariable String id) {
        return Result.OK(skillService.checkSkillQuality(id));
    }

    @DeleteMapping("/delete")
    @RequiresPermissions("openclaw:skill:delete")
    public Result<?> delete(@RequestParam String id) {
        skillService.logicDeleteSkill(id);
        return Result.OK("Deleted successfully");
    }

    @PostMapping("/disable")
    @RequiresPermissions("openclaw:skill:disable")
    public Result<?> disable(@RequestParam String id) {
        skillService.disableSkill(id);
        return Result.OK("Disabled successfully");
    }

    @PostMapping("/approve")
    @RequiresPermissions("openclaw:skill:review")
    public Result<?> approve(@RequestParam String id) {
        skillService.approveSkill(id);
        return Result.OK("Approved successfully");
    }

    @PostMapping("/reject")
    @RequiresPermissions("openclaw:skill:review")
    public Result<?> reject(@RequestParam String id, @RequestParam String reason) {
        skillService.rejectSkill(id, reason);
        return Result.OK("Rejected successfully");
    }

    @GetMapping("/queryById")
    @RequiresPermissions("openclaw:skill:list")
    public Result<OpenclawSkill> queryById(@RequestParam String id) {
        OpenclawSkill skill = skillService.getById(id);
        if (skill != null) {
            permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        }
        return Result.OK(skill);
    }
}
