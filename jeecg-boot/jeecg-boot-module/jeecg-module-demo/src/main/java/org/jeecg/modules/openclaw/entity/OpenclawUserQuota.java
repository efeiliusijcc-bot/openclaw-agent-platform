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
@TableName("openclaw_user_quota")
@Schema(description = "OpenClaw user quota")
public class OpenclawUserQuota extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private Integer maxAgents;
    private Integer maxWorkspaces;
    private Integer maxSkills;
    private Integer maxStorageMb;
    private Integer maxDailyRuns;
    private Integer maxConcurrentRuns;
    private String status;
    private Integer delFlag;
}
