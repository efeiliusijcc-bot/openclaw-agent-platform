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
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftFileDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.entity.OpenclawSkillTestRun;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillDraftService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillTestRunService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileContentVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileNodeVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftLintVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OpenClaw Skill Draft")
@RestController
@RequestMapping("/openclaw/skill/draft")
public class OpenclawSkillDraftController {
    @Autowired
    private IOpenclawSkillDraftService draftService;
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawSkillTestRunService testRunService;

    @GetMapping("/list")
    @RequiresPermissions("openclaw:skill:draft:list")
    public Result<IPage<OpenclawSkillDraft>> list(OpenclawSkillDraft draft,
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                  HttpServletRequest req) {
        QueryWrapper<OpenclawSkillDraft> queryWrapper = QueryGenerator.initQueryWrapper(draft, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        if (!permissionService.isSkillReviewer(permissionService.currentUser())) {
            String userId = permissionService.currentUserIdForQuery();
            if (userId != null) {
                queryWrapper.eq("owner_user_id", userId);
            }
        }
        queryWrapper.orderByDesc("create_time");
        return Result.OK(draftService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @PostMapping("/add")
    @RequiresPermissions("openclaw:skill:draft:add")
    public Result<OpenclawSkillDraft> add(@RequestBody OpenclawSkillDraftCreateDTO dto) {
        return Result.OK(draftService.createDraft(dto));
    }

    @PostMapping("/fromSkill")
    @RequiresPermissions("openclaw:skill:draft:add")
    public Result<OpenclawSkillDraft> fromSkill(@RequestParam String skillId) {
        return Result.OK(draftService.createFromSkill(skillId));
    }

    @GetMapping("/{id}/tree")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<List<OpenclawSkillDraftFileNodeVO>> tree(@PathVariable String id) {
        return Result.OK(draftService.fileTree(id));
    }

    @GetMapping("/{id}/file")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<OpenclawSkillDraftFileContentVO> readFile(@PathVariable String id, @RequestParam String path) {
        return Result.OK(draftService.readFile(id, path));
    }

    @PostMapping("/{id}/file")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<OpenclawSkillDraftFileContentVO> saveFile(@PathVariable String id, @RequestBody OpenclawSkillDraftFileDTO dto) {
        return Result.OK(draftService.saveFile(id, dto));
    }

    @PostMapping("/{id}/file/create")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<?> createFile(@PathVariable String id, @RequestBody OpenclawSkillDraftFileDTO dto) {
        draftService.createFile(id, dto);
        return Result.OK("Created successfully");
    }

    @DeleteMapping("/{id}/file")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<?> deleteFile(@PathVariable String id, @RequestParam String path) {
        draftService.deleteFile(id, path);
        return Result.OK("Deleted successfully");
    }

    @PostMapping("/{id}/lint")
    @RequiresPermissions("openclaw:skill:draft:lint")
    public Result<OpenclawSkillDraftLintVO> lint(@PathVariable String id) {
        return Result.OK(draftService.lint(id));
    }

    @PostMapping("/{id}/test")
    @RequiresPermissions("openclaw:skill:draft:test")
    public Result<OpenclawSkillTestRun> runTest(@PathVariable String id, @RequestBody OpenclawSkillDraftTestDTO dto) {
        return Result.OK(draftService.runTest(id, dto));
    }

    @GetMapping("/{id}/tests")
    @RequiresPermissions("openclaw:skill:draft:edit")
    public Result<IPage<OpenclawSkillTestRun>> testRuns(@PathVariable String id,
                                                        @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                        @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        OpenclawSkillDraft draft = draftService.getById(id);
        if (draft != null) {
            permissionService.checkOwnerOrAdmin(draft.getOwnerUserId());
        }
        QueryWrapper<OpenclawSkillTestRun> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("draft_id", id);
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        queryWrapper.orderByDesc("create_time");
        return Result.OK(testRunService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }
}
