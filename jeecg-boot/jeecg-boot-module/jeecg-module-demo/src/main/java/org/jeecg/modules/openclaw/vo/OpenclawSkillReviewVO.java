package org.jeecg.modules.openclaw.vo;

import lombok.Data;
import org.jeecg.modules.openclaw.entity.OpenclawPublishedSkillVersion;
import org.jeecg.modules.openclaw.entity.OpenclawSkillAiEditRecord;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraftVersion;
import org.jeecg.modules.openclaw.entity.OpenclawSkillReview;

import java.util.List;
import java.util.Map;

@Data
public class OpenclawSkillReviewVO {
    private OpenclawSkillReview review;
    private OpenclawSkillDraftVersion version;
    private Map<String, String> files;
    private Object testReport;
    private List<OpenclawSkillAiEditRecord> aiRecords;
    private List<OpenclawSkillDraftVersionVO.FileDiff> publishedDiffs;
    private OpenclawPublishedSkillVersion publishedVersion;
}
