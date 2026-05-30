package org.jeecg.modules.openclaw.vo;

import lombok.Data;

@Data
public class OpenclawAgentRunResultVO {
    private String runId;
    private String agentId;
    private String agentKey;
    private String agentName;
    private String conversationId;
    private String runType;
    private Integer streaming;
    private String model;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private String errorMessage;
    private Long durationMs;
}
