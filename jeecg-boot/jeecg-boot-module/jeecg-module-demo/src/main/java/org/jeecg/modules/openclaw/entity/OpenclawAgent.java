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
@TableName("openclaw_agent")
@Schema(description = "OpenClaw agent")
public class OpenclawAgent extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String workspaceId;
    private String agentKey;
    private String name;
    private String description;
    private String status;
    private Integer maxSkills;
    private Integer maxDailyRuns;
    private String configJson;
    private String gatewayId;
    private String remark;
    private Integer delFlag;
}
