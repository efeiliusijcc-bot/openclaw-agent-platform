package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillRepairVO {
    private String recordId;
    private String draftId;
    private String testRunId;
    private String source;
    private String summary;
    private String baseVersion;
    private String baseHash;
    private String status;
    private String repairBeforeStatus;
    private String repairAfterStatus;
    private List<String> warnings = new ArrayList<>();
    private List<FileSuggestion> files = new ArrayList<>();

    @Data
    public static class FileSuggestion {
        private String path;
        private String action;
        private String explanation;
        private String content;
        private String diff;
    }
}
