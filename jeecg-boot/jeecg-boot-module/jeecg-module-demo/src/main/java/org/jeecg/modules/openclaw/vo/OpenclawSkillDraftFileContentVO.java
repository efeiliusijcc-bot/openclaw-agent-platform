package org.jeecg.modules.openclaw.vo;

import lombok.Data;

@Data
public class OpenclawSkillDraftFileContentVO {
    private String path;
    private String content;
    private Long size;
    private String checksum;
}
