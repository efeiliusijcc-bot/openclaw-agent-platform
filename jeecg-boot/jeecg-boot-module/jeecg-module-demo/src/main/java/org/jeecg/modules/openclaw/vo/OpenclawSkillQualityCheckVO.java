package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillQualityCheckVO {
    private Boolean passed = false;
    private Integer score = 0;
    private List<String> missingFiles = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> checklist = new ArrayList<>();
    private Integer fileCount = 0;
    private Long totalSize = 0L;
}
