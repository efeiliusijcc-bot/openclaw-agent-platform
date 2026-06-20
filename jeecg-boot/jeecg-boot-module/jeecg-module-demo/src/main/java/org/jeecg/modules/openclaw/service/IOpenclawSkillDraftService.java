package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftFileDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileContentVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileNodeVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftLintVO;

import java.util.List;

public interface IOpenclawSkillDraftService extends IService<OpenclawSkillDraft> {
    OpenclawSkillDraft createDraft(OpenclawSkillDraftCreateDTO dto);

    OpenclawSkillDraft createFromSkill(String skillId);

    List<OpenclawSkillDraftFileNodeVO> fileTree(String draftId);

    OpenclawSkillDraftFileContentVO readFile(String draftId, String path);

    OpenclawSkillDraftFileContentVO saveFile(String draftId, OpenclawSkillDraftFileDTO dto);

    void createFile(String draftId, OpenclawSkillDraftFileDTO dto);

    void deleteFile(String draftId, String path);

    OpenclawSkillDraftLintVO lint(String draftId);
}
