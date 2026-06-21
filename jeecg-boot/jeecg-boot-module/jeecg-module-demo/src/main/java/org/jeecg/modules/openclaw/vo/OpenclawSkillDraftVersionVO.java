package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class OpenclawSkillDraftVersionVO {
    private String id;
    private String draftId;
    private Integer versionNo;
    private String sourceType;
    private String sourceRecordId;
    private String testRunId;
    private String fileHash;
    private String summary;
    private String lintStatus;
    private String testStatus;
    private String createdBy;
    private Date createdTime;
    private Map<String, String> files;
    private Object sourceRecord;
    private OpenclawSkillTestReportVO testReport;
    private List<FileDiff> diffs;

    @Data
    public static class FileDiff {
        private String path;
        private String changeType;
        private String beforeHash;
        private String afterHash;
    }
}
