package org.jeecg.modules.openclaw.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class OpenclawSkillTestReportVO {
    private String testRunId;
    private String draftId;
    private String agentKey;
    private Integer draftVersionNo;
    private String fileHash;
    private String status;
    private String lintStatus;
    private String gatewayStatus;
    private String input;
    private String output;
    private Error error = new Error();
    private List<String> logs = new ArrayList<>();
    private Date startedAt;
    private Date finishedAt;
    private Long durationMs;

    @Data
    public static class Error {
        private String type;
        private String message;
        private String code;
    }
}
