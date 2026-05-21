package org.jeecg.modules.openclaw.service;

import org.jeecg.modules.openclaw.vo.OpenclawGatewaySyncResultVO;

public interface IOpenclawGatewayConfigService {
    OpenclawGatewaySyncResultVO preview(String gatewayId);

    OpenclawGatewaySyncResultVO sync(String gatewayId);
}
