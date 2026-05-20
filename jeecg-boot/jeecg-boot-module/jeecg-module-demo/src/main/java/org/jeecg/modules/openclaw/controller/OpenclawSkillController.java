package org.jeecg.modules.openclaw.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillImportResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

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
        String userId = permissionService.currentUserIdForQuery();
        if (userId != null) {
            queryWrapper.eq("owner_user_id", userId);
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(skillService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @PostMapping("/add")
    @RequiresPermissions("openclaw:skill:add")
    public Result<?> add(@RequestBody OpenclawSkill skill) {
        LoginUser user = permissionService.currentUser();
        if (!StringUtils.hasText(skill.getName())) {
            throw new JeecgBootException("Skill 名称不能为空");
        }
        String version = normalizeVersion(skill.getVersion());
        String slug = normalizeSlug(skill.getName()) + "-" + IdWorker.getIdStr();
        skill.setOwnerUserId(user.getId());
        skill.setOwnerUsername(user.getUsername());
        skill.setSlug(slug);
        skill.setVersion(version);
        skill.setPath(OpenclawConstants.SKILL_ROOT + "/" + user.getId() + "/" + slug + "/" + version);
        skill.setScope("private");
        skill.setStatus("draft");
        skill.setChecksum(null);
        skill.setFileSize(0L);
        skill.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        skillService.save(skill);
        return Result.OK("新增成功");
    }

    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("openclaw:skill:edit")
    public Result<?> edit(@RequestBody OpenclawSkill request) {
        OpenclawSkill skill = skillService.getById(request.getId());
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill 不存在");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        skill.setName(request.getName());
        skill.setDescription(request.getDescription());
        skill.setRemark(request.getRemark());
        if (permissionService.isAdmin(permissionService.currentUser())) {
            skill.setScope(request.getScope());
            skill.setStatus(request.getStatus());
        }
        skillService.updateById(skill);
        return Result.OK("更新成功");
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

    @DeleteMapping("/delete")
    @RequiresPermissions("openclaw:skill:delete")
    public Result<?> delete(@RequestParam String id) {
        skillService.logicDeleteSkill(id);
        return Result.OK("删除成功");
    }

    @PostMapping("/disable")
    @RequiresPermissions("openclaw:skill:disable")
    public Result<?> disable(@RequestParam String id) {
        skillService.disableSkill(id);
        return Result.OK("禁用成功");
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

    private String normalizeSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(slug) ? slug : "skill";
    }

    private String normalizeVersion(String raw) {
        String version = StringUtils.hasText(raw) ? raw.trim() : "1.0.0";
        if (!version.matches("[0-9A-Za-z._-]{1,50}")) {
            throw new JeecgBootException("Skill 版本号格式非法");
        }
        return version;
    }
}
