package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.entity.OpenclawAgentSkill;

public interface IOpenclawAgentSkillService extends IService<OpenclawAgentSkill> {
    void bindSkill(String agentId, String skillId);

    void unbindSkill(String agentId, String skillId);

    void disableByAgent(String agentId);

    int countEnabledBySkill(String skillId);
}
