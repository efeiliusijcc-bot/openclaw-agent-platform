package org.jeecg.modules.openclaw.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class OpenclawSystemHealthVO {
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date checkedAt;
    private Boolean healthy;
    private Summary summary = new Summary();
    private List<ComponentHealth> components = new ArrayList<>();
    private List<PathHealth> paths = new ArrayList<>();
    private List<GatewayHealth> gateways = new ArrayList<>();
    private OpenclawAgentRun latestFailedRun;

    @Data
    public static class Summary {
        private Long agents;
        private Long workspaces;
        private Long skills;
        private Long runs;
        private Long failedRuns;
        private Long gateways;
        private Long errorAgents;
        private Long errorWorkspaces;
        private Long gatewayAttention;
    }

    @Data
    public static class ComponentHealth {
        private String name;
        private String status;
        private String message;
        private Long latencyMs;
    }

    @Data
    public static class PathHealth {
        private String name;
        private String path;
        private String status;
        private Boolean exists;
        private Boolean directory;
        private Boolean symbolicLink;
        private Boolean readable;
        private Boolean writable;
        private String message;
    }

    @Data
    public static class GatewayHealth {
        private String id;
        private String name;
        private String status;
        private String healthStatus;
        private String healthMessage;
        private String baseUrl;
        private String configPath;
        private String lastSyncStatus;
        private String lastSyncMessage;
        @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
        private Date lastSyncTime;
        private Boolean restartRequired;
        private PathHealth configFile;
    }
}
