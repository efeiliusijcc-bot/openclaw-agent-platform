package org.jeecg.modules.openclaw.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("openclaw_skill_draft_version")
@Schema(description = "OpenClaw skill draft version")
public class OpenclawSkillDraftVersion extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String draftId;
    private Integer versionNo;
    private String sourceType;
    private String sourceRecordId;
    private String testRunId;
    private String fileSnapshot;
    private String fileHash;
    private String summary;
    private String lintStatus;
    private String testStatus;
    private Integer delFlag;
}
