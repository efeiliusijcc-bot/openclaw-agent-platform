package org.jeecg.modules.openclaw.vo;

import lombok.Data;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;

@Data
public class OpenclawQuotaUsageVO {
    private OpenclawUserQuota quota;
    private Integer usedAgents;
    private Integer usedWorkspaces;
    private Integer usedSkills;
    private Integer todayRuns;
    private Integer runningRuns;
}
