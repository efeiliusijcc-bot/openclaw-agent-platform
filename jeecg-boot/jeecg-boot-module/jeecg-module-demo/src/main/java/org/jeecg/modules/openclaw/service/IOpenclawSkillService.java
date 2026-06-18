package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletResponse;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.vo.OpenclawSkillImportResultVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillQualityCheckVO;
import org.springframework.web.multipart.MultipartFile;

public interface IOpenclawSkillService extends IService<OpenclawSkill> {
    OpenclawSkill createDraftSkill(OpenclawSkill request);

    OpenclawSkillImportResultVO importSkill(MultipartFile file);

    void exportSkill(String id, HttpServletResponse response);

    OpenclawSkillQualityCheckVO checkSkillQuality(String id);

    void logicDeleteSkill(String id);

    void disableSkill(String id);

    void approveSkill(String id);

    void rejectSkill(String id, String reason);
}
