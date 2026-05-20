package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.dto.OpenclawQuotaUpdateDTO;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.vo.OpenclawQuotaUsageVO;

public interface IOpenclawUserQuotaService extends IService<OpenclawUserQuota> {
    OpenclawUserQuota getOrCreateQuota(LoginUser user);

    void updateQuota(OpenclawQuotaUpdateDTO dto);

    OpenclawQuotaUsageVO getMyUsage();
}
