package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawAgentRunTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;

public interface IOpenclawAgentRunService extends IService<OpenclawAgentRun> {
    OpenclawAgentRunResultVO runTest(String agentId, OpenclawAgentRunTestDTO dto);
}
