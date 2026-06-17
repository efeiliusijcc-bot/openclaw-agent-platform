package org.jeecg.modules.openclaw.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "OpenClaw workspace health check result")
public class OpenclawWorkspaceHealthCheckVO {
    private String workspaceId;
    private String workspaceKey;
    private String path;
    private String status;
    private boolean healthy;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private List<String> checkedItems = new ArrayList<>();
}
