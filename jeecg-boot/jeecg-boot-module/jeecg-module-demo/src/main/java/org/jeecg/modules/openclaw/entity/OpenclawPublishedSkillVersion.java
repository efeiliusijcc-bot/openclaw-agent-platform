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
@TableName("openclaw_published_skill_version")
@Schema(description = "OpenClaw published skill version")
public class OpenclawPublishedSkillVersion extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String skillId;
    private String reviewId;
    private String draftId;
    private Integer draftVersionNo;
    private Integer publishedVersionNo;
    private String fileSnapshotJson;
    private String fileHash;
    private String status;
    private String publishedBy;
    private Date publishedTime;
    private Integer delFlag;
}
