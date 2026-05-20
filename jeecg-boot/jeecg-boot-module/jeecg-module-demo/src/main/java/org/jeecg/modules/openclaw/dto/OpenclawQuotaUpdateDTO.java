package org.jeecg.modules.openclaw.dto;

import lombok.Data;

@Data
public class OpenclawQuotaUpdateDTO {
    private String id;
    private String userId;
    private String username;
    private Integer maxAgents;
    private Integer maxWorkspaces;
    private Integer maxSkills;
    private Integer maxStorageMb;
    private Integer maxDailyRuns;
    private Integer maxConcurrentRuns;
    private String status;
}
