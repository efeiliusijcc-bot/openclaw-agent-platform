package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;

public interface IOpenclawGatewayNodeService extends IService<OpenclawGatewayNode> {
    void logicDeleteNode(String id);
}
