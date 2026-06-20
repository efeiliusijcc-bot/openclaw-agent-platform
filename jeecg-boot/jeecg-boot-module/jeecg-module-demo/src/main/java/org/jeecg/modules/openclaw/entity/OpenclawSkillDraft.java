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
@TableName("openclaw_skill_draft")
@Schema(description = "OpenClaw skill draft")
public class OpenclawSkillDraft extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String skillId;
    private String draftName;
    private String skillSlug;
    private String ownerUserId;
    private String ownerUsername;
    private String status;
    private String description;
    private String baseVersion;
    private String draftPath;
    private String lastLintStatus;
    private String lastLintResultJson;
    private String lastTestStatus;
    private String lastTestRunId;
    private Date submitTime;
    private String reviewStatus;
    private String reviewComment;
    private String reviewedBy;
    private Date reviewedTime;
    private Integer delFlag;
}
