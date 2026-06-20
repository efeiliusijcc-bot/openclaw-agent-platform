package org.jeecg.modules.openclaw.dto;

import lombok.Data;

@Data
public class OpenclawSkillDraftCreateDTO {
    private String draftName;
    private String skillSlug;
    private String description;
}
