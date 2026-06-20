package org.jeecg.modules.openclaw.dto;

import lombok.Data;

@Data
public class OpenclawSkillDraftFileDTO {
    private String path;
    private String content;
    private Boolean directory;
}
