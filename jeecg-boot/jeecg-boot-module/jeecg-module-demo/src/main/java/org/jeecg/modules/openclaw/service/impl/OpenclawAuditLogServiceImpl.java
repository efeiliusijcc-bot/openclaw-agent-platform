package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.IpUtils;
import org.jeecg.modules.openclaw.entity.OpenclawAuditLog;
import org.jeecg.modules.openclaw.mapper.OpenclawAuditLogMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@Service
public class OpenclawAuditLogServiceImpl extends ServiceImpl<OpenclawAuditLogMapper, OpenclawAuditLog> implements IOpenclawAuditLogService {
    @Override
    public void log(String action, String targetType, String targetId, Object detail) {
        LoginUser user = null;
        Object principal = SecurityUtils.getSubject().getPrincipal();
        if (principal instanceof LoginUser) {
            user = (LoginUser) principal;
        }
        HttpServletRequest request = currentRequest();
        OpenclawAuditLog log = new OpenclawAuditLog();
        log.setUserId(user == null ? null : user.getId());
        log.setUsername(user == null ? null : user.getUsername());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(request == null ? null : IpUtils.getIpAddr(request));
        log.setUserAgent(request == null ? null : request.getHeader("User-Agent"));
        log.setDetailJson(detail == null ? null : JSON.toJSONString(detail));
        log.setCreateTime(new Date());
        save(log);
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes)) {
            return null;
        }
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }
}
