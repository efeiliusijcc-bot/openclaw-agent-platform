package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.entity.OpenclawAuditLog;

public interface IOpenclawAuditLogService extends IService<OpenclawAuditLog> {
    void log(String action, String targetType, String targetId, Object detail);
}
