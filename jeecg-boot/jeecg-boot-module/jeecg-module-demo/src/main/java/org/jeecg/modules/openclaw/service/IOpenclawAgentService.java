package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawAgentCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawAgentEditDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;

public interface IOpenclawAgentService extends IService<OpenclawAgent> {
    OpenclawAgent createAgent(OpenclawAgentCreateDTO dto);

    void editAgent(OpenclawAgentEditDTO dto);

    void logicDeleteAgent(String id);

    void disableAgent(String id);
}
