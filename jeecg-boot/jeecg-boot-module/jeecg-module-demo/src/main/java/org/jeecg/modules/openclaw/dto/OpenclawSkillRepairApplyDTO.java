package org.jeecg.modules.openclaw.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OpenclawSkillRepairApplyDTO {
    private String recordId;
    private String reason;
    private List<FilePatch> files = new ArrayList<>();

    @Data
    public static class FilePatch {
        private String path;
        private String action;
        private String content;
        private String explanation;
    }
}
