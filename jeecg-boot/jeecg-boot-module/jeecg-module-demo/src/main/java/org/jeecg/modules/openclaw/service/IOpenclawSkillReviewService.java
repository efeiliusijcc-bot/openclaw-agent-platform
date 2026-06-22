package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawSkillReviewActionDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillReviewSubmitDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkillReview;
import org.jeecg.modules.openclaw.vo.OpenclawSkillReviewVO;

public interface IOpenclawSkillReviewService extends IService<OpenclawSkillReview> {
    OpenclawSkillReview submitReview(String draftId, Integer versionNo, OpenclawSkillReviewSubmitDTO dto);

    OpenclawSkillReviewVO detail(String reviewId);

    OpenclawSkillReview approve(String reviewId, OpenclawSkillReviewActionDTO dto);

    OpenclawSkillReview reject(String reviewId, OpenclawSkillReviewActionDTO dto);

    OpenclawSkillReview cancel(String reviewId, OpenclawSkillReviewActionDTO dto);
}
