package org.jeecg.modules.openclaw.dto;

import lombok.Data;

@Data
public class OpenclawAgentEditDTO {
    private String id;
    private String name;
    private String description;
    private String status;
    private Integer maxSkills;
    private Integer maxDailyRuns;
    private String configJson;
    private String remark;
}
