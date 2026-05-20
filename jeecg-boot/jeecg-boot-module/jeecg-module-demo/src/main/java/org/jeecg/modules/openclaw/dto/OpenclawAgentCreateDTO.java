package org.jeecg.modules.openclaw.dto;

import lombok.Data;

@Data
public class OpenclawAgentCreateDTO {
    private String name;
    private String description;
    private Integer maxSkills;
    private Integer maxDailyRuns;
    private String configJson;
    private String remark;
}
