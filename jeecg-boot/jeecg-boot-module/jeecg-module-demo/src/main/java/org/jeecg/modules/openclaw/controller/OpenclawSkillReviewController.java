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
import org.jeecg.modules.openclaw.dto.OpenclawSkillReviewActionDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkillReview;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillReviewService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "OpenClaw Skill Review")
@RestController
@RequestMapping("/openclaw/skill/reviews")
public class OpenclawSkillReviewController {
    @Autowired
    private IOpenclawSkillReviewService reviewService;
    @Autowired
    private IOpenclawPermissionService permissionService;

    @GetMapping
    @RequiresPermissions("openclaw:skill:review")
    public Result<IPage<OpenclawSkillReview>> list(OpenclawSkillReview review,
                                                   @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                   @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                   HttpServletRequest req) {
        QueryWrapper<OpenclawSkillReview> queryWrapper = QueryGenerator.initQueryWrapper(review, req.getParameterMap());
        queryWrapper.eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
        queryWrapper.orderByDesc("submitted_time");
        return Result.OK(reviewService.page(new Page<>(pageNo, pageSize), queryWrapper));
    }

    @GetMapping("/{reviewId}")
    @RequiresPermissions("openclaw:skill:review")
    public Result<OpenclawSkillReviewVO> detail(@PathVariable String reviewId) {
        return Result.OK(reviewService.detail(reviewId));
    }

    @PostMapping("/{reviewId}/approve")
    @RequiresPermissions("openclaw:skill:review")
    public Result<OpenclawSkillReview> approve(@PathVariable String reviewId, @RequestBody(required = false) OpenclawSkillReviewActionDTO dto) {
        return Result.OK(reviewService.approve(reviewId, dto));
    }

    @PostMapping("/{reviewId}/reject")
    @RequiresPermissions("openclaw:skill:review")
    public Result<OpenclawSkillReview> reject(@PathVariable String reviewId, @RequestBody(required = false) OpenclawSkillReviewActionDTO dto) {
        return Result.OK(reviewService.reject(reviewId, dto));
    }

    @PostMapping("/{reviewId}/cancel")
    @RequiresPermissions("openclaw:skill:draft:submit")
    public Result<OpenclawSkillReview> cancel(@PathVariable String reviewId, @RequestBody(required = false) OpenclawSkillReviewActionDTO dto) {
        return Result.OK(reviewService.cancel(reviewId, dto));
    }
}
