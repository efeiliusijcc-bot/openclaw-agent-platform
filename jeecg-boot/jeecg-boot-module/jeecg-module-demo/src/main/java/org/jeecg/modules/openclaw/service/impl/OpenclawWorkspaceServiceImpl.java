package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawWorkspaceService;
import org.springframework.stereotype.Service;

@Service
public class OpenclawWorkspaceServiceImpl extends ServiceImpl<OpenclawWorkspaceMapper, OpenclawWorkspace> implements IOpenclawWorkspaceService {
    @Override
    public OpenclawWorkspace createForAgent(LoginUser user, String agentName, String agentKey) {
        OpenclawWorkspace workspace = new OpenclawWorkspace();
        workspace.setUserId(user.getId());
        workspace.setUsername(user.getUsername());
        workspace.setName(agentName + " Workspace");
        workspace.setWorkspaceKey("ws_" + agentKey);
        workspace.setPath(OpenclawConstants.WORKSPACE_ROOT + "/" + user.getId() + "/" + agentKey + "/workspace");
        workspace.setQuotaSizeMb(1024);
        workspace.setUsedSizeMb(0);
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_ACTIVE);
        workspace.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(workspace);
        return workspace;
    }

    @Override
    public void markDeleted(String workspaceId) {
        OpenclawWorkspace workspace = getById(workspaceId);
        if (workspace == null) {
            return;
        }
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_DELETED);
        workspace.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(workspace);
    }
}
