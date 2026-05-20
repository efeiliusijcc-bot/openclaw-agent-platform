package org.jeecg.modules.openclaw.vo;

import lombok.Data;

@Data
public class OpenclawSkillImportResultVO {
    private String skillId;
    private String name;
    private String slug;
    private String version;
    private String checksum;
    private Long fileSize;
}
