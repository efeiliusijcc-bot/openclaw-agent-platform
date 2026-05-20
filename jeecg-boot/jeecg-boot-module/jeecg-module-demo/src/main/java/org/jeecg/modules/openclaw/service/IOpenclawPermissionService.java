package org.jeecg.modules.openclaw.service;

import org.jeecg.common.system.vo.LoginUser;

public interface IOpenclawPermissionService {
    LoginUser currentUser();

    boolean isAdmin(LoginUser user);

    void checkOwnerOrAdmin(String ownerUserId);

    String currentUserIdForQuery();
}
