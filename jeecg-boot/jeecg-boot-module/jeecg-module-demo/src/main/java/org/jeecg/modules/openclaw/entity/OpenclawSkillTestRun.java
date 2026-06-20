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
@TableName("openclaw_skill_test_run")
@Schema(description = "OpenClaw skill draft test run")
public class OpenclawSkillTestRun extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String draftId;
    private String skillSlug;
    private String userId;
    private String username;
    private String status;
    private String prompt;
    private String expectedOutput;
    private String outputSummary;
    private String errorMessage;
    private String workspacePath;
    private Date startTime;
    private Date finishTime;
    private Long durationMs;
    private String agentRunId;
    private Integer delFlag;
}
