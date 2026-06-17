package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.vo.OpenclawWorkspaceHealthCheckVO;

public interface IOpenclawWorkspaceService extends IService<OpenclawWorkspace> {
    OpenclawWorkspace createForAgent(LoginUser user, String agentName, String agentKey);

    void markDeleted(String workspaceId);

    OpenclawWorkspaceHealthCheckVO healthCheck(String workspaceId);

    OpenclawWorkspaceHealthCheckVO rematerialize(String workspaceId);
}
