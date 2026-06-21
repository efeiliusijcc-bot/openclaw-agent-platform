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
@TableName("openclaw_skill_ai_edit_record")
@Schema(description = "OpenClaw skill AI edit preview record")
public class OpenclawSkillAiEditRecord extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String draftId;
    private String skillId;
    private String workspaceId;
    private String userId;
    private String recordType;
    private String testRunId;
    private String userInstruction;
    private String summary;
    private String filesJson;
    private String warningsJson;
    private String baseVersion;
    private String baseHash;
    private String status;
    private String errorMessage;
    private String repairBeforeStatus;
    private String repairAfterStatus;
    private Date appliedTime;
}
