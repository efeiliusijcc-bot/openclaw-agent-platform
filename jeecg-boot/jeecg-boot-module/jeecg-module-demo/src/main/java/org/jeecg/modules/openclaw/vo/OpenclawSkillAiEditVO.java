package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillAiEditVO {
    private String recordId;
    private String draftId;
    private String testRunId;
    private String source;
    private String summary;
    private String baseVersion;
    private String baseHash;
    private String status;
    private List<String> warnings = new ArrayList<>();
    private List<OpenclawSkillRepairVO.FileSuggestion> files = new ArrayList<>();
}
