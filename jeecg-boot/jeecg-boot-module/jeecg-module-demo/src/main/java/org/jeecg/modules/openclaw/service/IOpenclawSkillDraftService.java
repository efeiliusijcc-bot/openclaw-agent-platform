package org.jeecg.modules.openclaw.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftBatchTestDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftFileDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftTestDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillGenerateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillRepairApplyDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillRepairDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.entity.OpenclawSkillTestRun;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileContentVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileNodeVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftLintVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillRepairVO;

import java.util.List;

public interface IOpenclawSkillDraftService extends IService<OpenclawSkillDraft> {
    OpenclawSkillDraft createDraft(OpenclawSkillDraftCreateDTO dto);

    OpenclawSkillDraft generateDraft(OpenclawSkillGenerateDTO dto);

    OpenclawSkillDraft createFromSkill(String skillId);

    OpenclawSkillDraft getDraftForAccess(String draftId);

    List<OpenclawSkillDraftFileNodeVO> fileTree(String draftId);

    OpenclawSkillDraftFileContentVO readFile(String draftId, String path);

    OpenclawSkillDraftFileContentVO saveFile(String draftId, OpenclawSkillDraftFileDTO dto);

    void createFile(String draftId, OpenclawSkillDraftFileDTO dto);

    void deleteFile(String draftId, String path);

    OpenclawSkillDraftLintVO lint(String draftId);

    OpenclawSkillTestRun runTest(String draftId, OpenclawSkillDraftTestDTO dto);

    List<OpenclawSkillTestRun> runBatchTests(String draftId, OpenclawSkillDraftBatchTestDTO dto);

    OpenclawSkillRepairVO repairDraft(String draftId, OpenclawSkillRepairDTO dto);

    OpenclawSkillRepairVO applyRepair(String draftId, OpenclawSkillRepairApplyDTO dto);

    OpenclawSkillDraft submitForReview(String draftId);

    OpenclawSkillDraft approveDraft(String draftId);

    OpenclawSkillDraft rejectDraft(String draftId, String reason);

    OpenclawSkill publishDraft(String draftId);
}
