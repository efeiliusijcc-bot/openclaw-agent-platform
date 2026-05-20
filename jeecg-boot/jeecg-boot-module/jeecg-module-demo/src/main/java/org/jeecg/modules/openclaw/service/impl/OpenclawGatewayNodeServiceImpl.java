package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.mapper.OpenclawGatewayNodeMapper;
import org.jeecg.modules.openclaw.service.IOpenclawGatewayNodeService;
import org.springframework.stereotype.Service;

@Service
public class OpenclawGatewayNodeServiceImpl extends ServiceImpl<OpenclawGatewayNodeMapper, OpenclawGatewayNode> implements IOpenclawGatewayNodeService {
    @Override
    public void logicDeleteNode(String id) {
        OpenclawGatewayNode node = getById(id);
        if (node == null) {
            return;
        }
        node.setStatus(OpenclawConstants.STATUS_DISABLED);
        node.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(node);
    }
}
