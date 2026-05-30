package org.jeecg.modules.openclaw.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("openclaw_agent_run")
@Schema(description = "OpenClaw agent run")
public class OpenclawAgentRun extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String agentId;
    private String agentName;
    private String conversationId;
    private String runType;
    private Integer streaming;
    private String model;
    private String status;
    private String inputSummary;
    private String outputSummary;
    private String errorMessage;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;
    private Long durationMs;
    private Integer delFlag;
}
