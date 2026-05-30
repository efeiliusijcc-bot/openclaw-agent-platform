package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawAgentRunTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IOpenclawAgentRunService extends IService<OpenclawAgentRun> {
    OpenclawAgentRunResultVO runTest(String agentId, OpenclawAgentRunTestDTO dto);

    SseEmitter chatStream(String agentId, OpenclawAgentRunTestDTO dto);
}
