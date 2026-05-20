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
@TableName("openclaw_workspace")
@Schema(description = "OpenClaw workspace")
public class OpenclawWorkspace extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String name;
    private String workspaceKey;
    private String path;
    private Integer quotaSizeMb;
    private Integer usedSizeMb;
    private String status;
    private String remark;
    private Integer delFlag;
}
