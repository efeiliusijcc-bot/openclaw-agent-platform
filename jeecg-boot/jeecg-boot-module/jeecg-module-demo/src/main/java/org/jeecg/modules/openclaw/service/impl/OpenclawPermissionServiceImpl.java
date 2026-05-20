package org.jeecg.modules.openclaw.service.impl;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.springframework.stereotype.Service;

@Service
public class OpenclawPermissionServiceImpl implements IOpenclawPermissionService {
    @Override
    public LoginUser currentUser() {
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (!(principal instanceof LoginUser)) {
            throw new JeecgBootException("用户未登录");
        }
        return (LoginUser) principal;
    }

    @Override
    public boolean isAdmin(LoginUser user) {
        return SecurityUtils.getSubject().hasRole("admin")
            || SecurityUtils.getSubject().hasRole(OpenclawConstants.ROLE_ADMIN);
    }

    @Override
    public void checkOwnerOrAdmin(String ownerUserId) {
        LoginUser user = currentUser();
        if (isAdmin(user)) {
            return;
        }
        if (ownerUserId == null || !ownerUserId.equals(user.getId())) {
            throw new JeecgBootException("无权操作该 OpenClaw 资源");
        }
    }

    @Override
    public String currentUserIdForQuery() {
        LoginUser user = currentUser();
        return isAdmin(user) ? null : user.getId();
    }
}
