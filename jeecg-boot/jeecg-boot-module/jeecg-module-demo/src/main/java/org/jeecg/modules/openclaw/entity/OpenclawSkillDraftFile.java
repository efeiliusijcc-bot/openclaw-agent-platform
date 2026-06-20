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
@TableName("openclaw_skill_draft_file")
@Schema(description = "OpenClaw skill draft file")
public class OpenclawSkillDraftFile extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String draftId;
    private String filePath;
    private String fileType;
    private Long sizeBytes;
    private String checksum;
    private Integer delFlag;
}
