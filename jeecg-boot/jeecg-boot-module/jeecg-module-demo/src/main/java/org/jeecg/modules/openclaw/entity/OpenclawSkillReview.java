package org.jeecg.modules.openclaw.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

import java.io.Serializable;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("openclaw_skill_review")
@Schema(description = "OpenClaw skill review")
public class OpenclawSkillReview extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String draftId;
    private Integer versionNo;
    private String skillId;
    private String workspaceId;
    private String submitterId;
    private String submitterUsername;
    private String reviewerId;
    private String reviewerUsername;
    private String status;
    private String fileSnapshotJson;
    private String fileHash;
    private String testRunId;
    private String testReportJson;
    private String aiRecordIdsJson;
    private String submitComment;
    private String reviewComment;
    private Date submittedTime;
    private Date reviewedTime;
    private Integer publishedVersionNo;
    private String publishedSkillId;
    private Integer delFlag;
}
