package org.jeecg.modules.openclaw.vo;

import lombok.Data;

@Data
public class OpenclawGatewaySyncResultVO {
    private String gatewayId;
    private String configPath;
    private String workspaceRoot;
    private Integer agentCount;
    private Integer skillCount;
    private String checksum;
    private Boolean restartRequired;
    private String message;
    private String content;
}
