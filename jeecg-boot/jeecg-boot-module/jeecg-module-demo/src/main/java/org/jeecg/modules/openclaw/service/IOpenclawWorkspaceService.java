package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;

public interface IOpenclawWorkspaceService extends IService<OpenclawWorkspace> {
    OpenclawWorkspace createForAgent(LoginUser user, String agentName, String agentKey);

    void markDeleted(String workspaceId);
}
