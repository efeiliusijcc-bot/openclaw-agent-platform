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
@TableName("openclaw_gateway_node")
@Schema(description = "OpenClaw gateway node")
public class OpenclawGatewayNode extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String baseUrl;
    private String status;
    private Integer maxAgents;
    private Integer currentAgents;
    private Integer maxConcurrentRuns;
    private Integer currentRunning;
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastHeartbeat;
    private String remark;
    private Integer delFlag;
}
