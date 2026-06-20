package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillDraftLintVO {
    private Boolean passed;
    private String status;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Integer fileCount;
    private Long totalSize;
}
