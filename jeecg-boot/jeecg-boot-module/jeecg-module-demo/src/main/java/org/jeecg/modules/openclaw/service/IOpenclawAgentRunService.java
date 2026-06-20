package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawAgentRunTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IOpenclawAgentRunService extends IService<OpenclawAgentRun> {
    OpenclawAgentRunResultVO runTest(String agentId, OpenclawAgentRunTestDTO dto);

    OpenclawAgentRunResultVO runDraftTest(OpenclawAgent draftAgent, OpenclawWorkspace workspace, String prompt, String testRunId, boolean localExecution);

    SseEmitter chatStream(String agentId, OpenclawAgentRunTestDTO dto);
}
