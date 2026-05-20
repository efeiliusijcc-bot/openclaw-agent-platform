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
@TableName("openclaw_agent_skill")
@Schema(description = "OpenClaw agent skill binding")
public class OpenclawAgentSkill extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String agentId;
    private String skillId;
    private Integer enabled;
    private Integer delFlag;
}
