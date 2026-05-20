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
@TableName("openclaw_skill")
@Schema(description = "OpenClaw skill")
public class OpenclawSkill extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String ownerUserId;
    private String ownerUsername;
    private String name;
    private String slug;
    private String version;
    private String scope;
    private String status;
    private String description;
    private String path;
    private String checksum;
    private Long fileSize;
    private String remark;
    private Integer delFlag;
}
