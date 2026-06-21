package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawAgentRunTestDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillAiEditApplyDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillAiEditPreviewDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftBatchTestDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftFileDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftTestDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillGenerateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillRepairApplyDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillRepairDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkillAiEditRecord;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraftFile;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraftVersion;
import org.jeecg.modules.openclaw.entity.OpenclawSkillTestRun;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawGatewayNodeMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillAiEditRecordMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftFileMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftVersionMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentRunService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillDraftService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillTestRunService;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillAiEditVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileContentVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileNodeVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftLintVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftVersionVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillRepairVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillTestReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class OpenclawSkillDraftServiceImpl extends ServiceImpl<OpenclawSkillDraftMapper, OpenclawSkillDraft> implements IOpenclawSkillDraftService {
    private static final String DRAFT_ROOT = "/data/openclaw-platform/skill-drafts";
    private static final String TEST_WORKSPACE_ROOT = OpenclawConstants.WORKSPACE_ROOT + "/skill-draft-tests";
    private static final Set<String> EDITABLE_STATUSES = Set.of("editing", "lint_failed", "lint_passed", "test_failed", "test_passed", "rejected");
    private static final Set<String> REVIEW_LOCKED_STATUSES = Set.of("submitted", "approved", "published");
    private static final Set<String> DANGEROUS_CODE_KEYWORDS = Set.of(
        "os.system", "subprocess", "rm -rf", "curl ", "wget ", "chmod", "sudo", "eval(", "exec(", "socket", "requests", "open('/etc"
    );
    private static final Object DRAFT_AGENT_REGISTRY_LOCK = new Object();

    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawSkillService skillService;
    @Autowired
    private OpenclawSkillDraftFileMapper draftFileMapper;
    @Autowired
    private OpenclawPathSafetyService pathSafetyService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private IOpenclawSkillTestRunService testRunService;
    @Autowired
    private IOpenclawAgentRunService agentRunService;
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private OpenclawGatewayNodeMapper gatewayNodeMapper;
    @Autowired
    private OpenclawWorkspaceMaterializer workspaceMaterializer;
    @Autowired
    private OpenclawSkillAiEditRecordMapper aiEditRecordMapper;
    @Autowired
    private SkillAiEditValidator skillAiEditValidator;
    @Autowired
    private OpenclawSkillDraftVersionMapper draftVersionMapper;

    @Value("${openclaw.skill.ai.base-url:${OPENCLAW_SKILL_AI_BASE_URL:}}")
    private String skillAiBaseUrl;

    @Value("${openclaw.skill.ai.api-key:${OPENCLAW_SKILL_AI_API_KEY:}}")
    private String skillAiApiKey;

    @Value("${openclaw.skill.ai.model:${OPENCLAW_SKILL_AI_MODEL:}}")
    private String skillAiModel;

    @Value("${openclaw.gateway.draft-agent-registry-path:${OPENCLAW_GATEWAY_DRAFT_AGENT_REGISTRY_PATH:/root/.openclaw/draft-agents.json}}")
    private String draftAgentRegistryPath;

    @Value("${openclaw.gateway.draft-agent-ttl-seconds:${OPENCLAW_GATEWAY_DRAFT_AGENT_TTL_SECONDS:600}}")
    private Long draftAgentTtlSeconds;

    @Value("${openclaw.skill-draft.test-model:${OPENCLAW_DRAFT_TEST_MODEL:${OPENCLAW_RUN_MODEL_OVERRIDE:}}}")
    private String draftTestModelOverride;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft createDraft(OpenclawSkillDraftCreateDTO dto) {
        LoginUser user = permissionService.currentUser();
        validateDraftRequest(dto);
        String slug = normalizeSlug(dto.getSkillSlug());
        String draftId = IdWorker.getIdStr();
        Path draftPath = draftPath(user.getId(), draftId);
        try {
            Files.createDirectories(draftPath);
            writeDefaultFiles(draftPath, dto.getDraftName(), slug, dto.getDescription());
        } catch (IOException e) {
            cleanupQuietly(draftPath);
            throw new JeecgBootException("Create Skill draft files failed: " + e.getMessage(), e);
        }

        OpenclawSkillDraft draft = new OpenclawSkillDraft();
        draft.setId(draftId);
        draft.setDraftName(dto.getDraftName().trim());
        draft.setSkillSlug(slug);
        draft.setOwnerUserId(user.getId());
        draft.setOwnerUsername(user.getUsername());
        draft.setStatus("editing");
        draft.setDescription(trim(dto.getDescription(), 2000));
        draft.setDraftPath(draftPath.toString());
        draft.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(draft);
        try {
            scanFiles(draft);
        } catch (IOException e) {
            cleanupQuietly(draftPath);
            throw new JeecgBootException("Scan Skill draft files failed: " + e.getMessage(), e);
        }
        auditLogService.log("skill_draft_create", "skill_draft", draft.getId(), draft);
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft generateDraft(OpenclawSkillGenerateDTO dto) {
        GeneratedSkillSpec spec = generateSkillSpec(dto);
        OpenclawSkillDraftCreateDTO createDTO = new OpenclawSkillDraftCreateDTO();
        createDTO.setDraftName(spec.draftName);
        createDTO.setSkillSlug(spec.skillSlug);
        createDTO.setDescription(spec.description);
        OpenclawSkillDraft draft = createDraft(createDTO);
        Path root = draftRoot(draft);
        try {
            cleanupDraftFiles(root);
            writeGeneratedFiles(root, spec.files);
            scanFiles(draft);
            OpenclawSkillDraftLintVO lint = lint(draft.getId());
            draft = getById(draft.getId());
            auditLogService.logSuccess("skill_draft_ai_generate", "skill_draft", draft.getId(), Map.of(
                "draft", draft,
                "lint", lint,
                "requirement", trim(dto == null ? null : dto.getRequirement(), 800)
            ));
            return draft;
        } catch (IOException | RuntimeException e) {
            cleanupQuietly(root);
            throw e instanceof RuntimeException ? (RuntimeException) e : new JeecgBootException("Generate Skill draft files failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft createFromSkill(String skillId) {
        if (!StringUtils.hasText(skillId)) {
            throw new JeecgBootException("Skill id is required.");
        }
        OpenclawSkill skill = skillService.getById(skillId);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill does not exist.");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        LoginUser user = permissionService.currentUser();
        Path source = Paths.get(skill.getPath()).normalize();
        if (!Files.isDirectory(source)) {
            throw new JeecgBootException("Skill file directory does not exist.");
        }
        String draftId = IdWorker.getIdStr();
        Path target = draftPath(user.getId(), draftId);
        try {
            copyDirectory(source, target);
        } catch (IOException e) {
            cleanupQuietly(target);
            throw new JeecgBootException("Copy Skill files to draft failed: " + e.getMessage(), e);
        }

        OpenclawSkillDraft draft = new OpenclawSkillDraft();
        draft.setId(draftId);
        draft.setSkillId(skill.getId());
        draft.setDraftName(skill.getName() + " Draft");
        draft.setSkillSlug(skill.getSlug());
        draft.setOwnerUserId(user.getId());
        draft.setOwnerUsername(user.getUsername());
        draft.setStatus("editing");
        draft.setDescription(skill.getDescription());
        draft.setBaseVersion(skill.getVersion());
        draft.setDraftPath(target.toString());
        draft.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(draft);
        try {
            scanFiles(draft);
        } catch (IOException e) {
            cleanupQuietly(target);
            throw new JeecgBootException("Scan Skill draft files failed: " + e.getMessage(), e);
        }
        auditLogService.log("skill_draft_create_from_skill", "skill_draft", draft.getId(), draft);
        return draft;
    }

    @Override
    public OpenclawSkillDraft getDraftForAccess(String draftId) {
        return requireDraft(draftId, false);
    }

    @Override
    public List<OpenclawSkillDraftFileNodeVO> fileTree(String draftId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        try {
            return buildTree(draftRoot(draft));
        } catch (IOException e) {
            throw new JeecgBootException("Read draft file tree failed: " + e.getMessage(), e);
        }
    }

    @Override
    public OpenclawSkillDraftFileContentVO readFile(String draftId, String path) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        Path root = draftRoot(draft);
        Path file = pathSafetyService.resolve(root, path);
        pathSafetyService.rejectIfOutsideRoot(root, file);
        pathSafetyService.rejectSymlink(file);
        if (!Files.isRegularFile(file)) {
            throw new JeecgBootException("File does not exist.");
        }
        try {
            pathSafetyService.rejectOversized(file);
            OpenclawSkillDraftFileContentVO vo = new OpenclawSkillDraftFileContentVO();
            vo.setPath(toRelative(root, file));
            vo.setContent(Files.readString(file, StandardCharsets.UTF_8));
            vo.setSize(Files.size(file));
            vo.setChecksum(sha256File(file));
            return vo;
        } catch (IOException e) {
            throw new JeecgBootException("Read draft file failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraftFileContentVO saveFile(String draftId, OpenclawSkillDraftFileDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        if (dto == null || dto.getContent() == null) {
            throw new JeecgBootException("File content is required.");
        }
        byte[] data = dto.getContent().getBytes(StandardCharsets.UTF_8);
        Path root = draftRoot(draft);
        pathSafetyService.validateWritableFile(root, dto.getPath(), data.length);
        Path file = pathSafetyService.resolve(root, dto.getPath());
        if (!Files.exists(file)) {
            throw new JeecgBootException("File does not exist.");
        }
        if (!Files.isRegularFile(file)) {
            throw new JeecgBootException("Only regular files can be saved.");
        }
        try {
            Files.write(file, data, StandardOpenOption.TRUNCATE_EXISTING);
            scanFiles(draft);
            createDraftVersion(draft, "manual", null, null, "Manual save: " + dto.getPath(), true);
            auditLogService.log("skill_draft_file_update", "skill_draft", draft.getId(), Map.of("path", dto.getPath()));
            return readFile(draftId, dto.getPath());
        } catch (IOException e) {
            throw new JeecgBootException("Save draft file failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createFile(String draftId, OpenclawSkillDraftFileDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        if (dto == null) {
            throw new JeecgBootException("File path is required.");
        }
        Path root = draftRoot(draft);
        Path target = pathSafetyService.resolve(root, dto.getPath());
        if (Files.exists(target)) {
            throw new JeecgBootException("File already exists.");
        }
        try {
            Files.createDirectories(target.getParent());
            if (Boolean.TRUE.equals(dto.getDirectory())) {
                Files.createDirectories(target);
            } else {
                byte[] data = StringUtils.hasText(dto.getContent()) ? dto.getContent().getBytes(StandardCharsets.UTF_8) : new byte[0];
                pathSafetyService.validateWritableFile(root, dto.getPath(), data.length);
                Files.write(target, data, StandardOpenOption.CREATE_NEW);
            }
            scanFiles(draft);
            createDraftVersion(draft, "manual", null, null, "Manual create: " + dto.getPath(), true);
            auditLogService.log("skill_draft_file_create", "skill_draft", draft.getId(), Map.of("path", dto.getPath(), "directory", Boolean.TRUE.equals(dto.getDirectory())));
        } catch (IOException e) {
            throw new JeecgBootException("Create draft file failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(String draftId, String path) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        if ("SKILL.md".equals(path)) {
            throw new JeecgBootException("SKILL.md cannot be deleted.");
        }
        Path root = draftRoot(draft);
        Path target = pathSafetyService.resolve(root, path);
        pathSafetyService.rejectIfOutsideRoot(root, target);
        if (root.equals(target)) {
            throw new JeecgBootException("Draft root cannot be deleted.");
        }
        try {
            if (Files.isDirectory(target)) {
                try (var walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(item -> deleteQuietly(item, root));
                }
            } else {
                Files.deleteIfExists(target);
            }
            scanFiles(draft);
            createDraftVersion(draft, "manual", null, null, "Manual delete: " + path, true);
            auditLogService.log("skill_draft_file_delete", "skill_draft", draft.getId(), Map.of("path", path));
        } catch (IOException e) {
            throw new JeecgBootException("Delete draft file failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraftLintVO lint(String draftId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if (REVIEW_LOCKED_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow lint.");
        }
        OpenclawSkillDraftLintVO result = new OpenclawSkillDraftLintVO();
        Path root = draftRoot(draft);
        try {
            inspectFiles(root, result);
            inspectSkillMd(root, result);
            result.setPassed(result.getErrors().isEmpty());
            result.setStatus(Boolean.TRUE.equals(result.getPassed()) ? "lint_passed" : "lint_failed");
            draft.setStatus(result.getStatus());
            draft.setLastLintStatus(result.getStatus());
            draft.setLastLintResultJson(JSON.toJSONString(result));
            updateById(draft);
            updateVersionLintStatus(draft, result.getStatus());
            auditLogService.log(Boolean.TRUE.equals(result.getPassed()) ? "skill_draft_lint" : "skill_draft_lint_failed", "skill_draft", draft.getId(), result);
            return result;
        } catch (IOException e) {
            throw new JeecgBootException("Lint draft failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillTestRun runTest(String draftId, OpenclawSkillDraftTestDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getPrompt())) {
            throw new JeecgBootException("Test prompt is required.");
        }
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if (REVIEW_LOCKED_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow test run.");
        }
        LoginUser user = permissionService.currentUser();
        Date start = new Date();
        OpenclawSkillDraftVersion version = ensureCurrentDraftVersion(draft, "manual", "Test snapshot");
        OpenclawSkillTestRun run = new OpenclawSkillTestRun();
        run.setDraftId(draft.getId());
        run.setSkillSlug(draft.getSkillSlug());
        run.setUserId(user.getId());
        run.setUsername(user.getUsername());
        run.setStatus(OpenclawConstants.RUN_STATUS_RUNNING);
        run.setPrompt(trim(dto.getPrompt(), 8000));
        run.setExpectedOutput(trim(dto.getExpectedOutput(), 4000));
        run.setInputJson(testInputJson(dto));
        run.setDraftVersionNo(version.getVersionNo());
        run.setFileHash(version.getFileHash());
        run.setGatewayStatus("PENDING");
        run.setStartTime(start);
        run.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        testRunService.save(run);
        auditLogService.log("skill_draft_test_start", "skill_test_run", run.getId(), run);

        try {
            OpenclawSkillDraftLintVO lint = lint(draftId);
            run.setLintStatus(lint.getStatus());
            if (!Boolean.TRUE.equals(lint.getPassed())) {
                finishTestRun(run, start, OpenclawConstants.RUN_STATUS_FAILED, "Lint failed before test run.", JSON.toJSONString(lint), null, null);
                draft.setLastTestStatus(OpenclawConstants.RUN_STATUS_FAILED);
                draft.setLastTestRunId(run.getId());
                updateById(draft);
                updateVersionTestStatus(draft, run, OpenclawConstants.RUN_STATUS_FAILED);
                updateLatestAppliedRepairStatus(draft, OpenclawConstants.RUN_STATUS_FAILED);
                auditLogService.logFailure("skill_draft_test_failed", "skill_test_run", run.getId(), run);
                return run;
            }

            TestAgentContext context = prepareTestAgent(draft, run);
            run.setAgentKey(context.agent.getAgentKey());
            run.setGatewayStatus("REGISTERED");
            testRunService.updateById(run);
            registerDraftAgent(context, draft, run, user);
            auditLogService.logSuccess("skill_draft_agent_register", "skill_test_run", run.getId(), draftAgentAuditDetail(context, draft, run, user));
            OpenclawAgentRunResultVO agentRun;
            try {
                agentRun = agentRunService.runDraftTest(context.agent, context.workspace, dto.getPrompt(), run.getId(), Boolean.TRUE.equals(dto.getLocalExecution()));
            } finally {
                log.info("skill draft agent retained until ttl agentKey={} draftId={} testRunId={} workspaceId={} registryPath={}",
                    context.agent.getAgentKey(), draft.getId(), run.getId(), context.workspace.getId(), draftAgentRegistryFile());
                auditLogService.logSuccess("skill_draft_agent_retained_until_ttl", "skill_test_run", run.getId(), draftAgentAuditDetail(context, draft, run, user));
            }
            String status = StringUtils.hasText(agentRun.getStatus()) ? agentRun.getStatus() : OpenclawConstants.RUN_STATUS_FAILED;
            finishTestRun(run, start, status, agentRun.getOutputSummary(), agentRun.getErrorMessage(), context.workspace.getPath(), agentRun);
            run.setAgentRunId(agentRun.getRunId());
            testRunService.updateById(run);
            draft.setStatus(OpenclawConstants.RUN_STATUS_SUCCESS.equals(status) ? "test_passed" : "test_failed");
            draft.setLastTestStatus(status);
            draft.setLastTestRunId(run.getId());
            updateById(draft);
            updateVersionTestStatus(draft, run, status);
            updateLatestAppliedRepairStatus(draft, status);
            if (OpenclawConstants.RUN_STATUS_SUCCESS.equals(status)) {
                auditLogService.logSuccess("skill_draft_test_success", "skill_test_run", run.getId(), run);
            } else {
                auditLogService.logFailure("skill_draft_test_failed", "skill_test_run", run.getId(), run);
            }
            return run;
        } catch (Exception e) {
            finishTestRun(run, start, OpenclawConstants.RUN_STATUS_FAILED, null, e.getMessage(), null, null);
            draft.setStatus("test_failed");
            draft.setLastTestStatus(OpenclawConstants.RUN_STATUS_FAILED);
            draft.setLastTestRunId(run.getId());
            updateById(draft);
            updateVersionTestStatus(draft, run, OpenclawConstants.RUN_STATUS_FAILED);
            updateLatestAppliedRepairStatus(draft, OpenclawConstants.RUN_STATUS_FAILED);
            auditLogService.logFailure("skill_draft_test_failed", "skill_test_run", run.getId(), run);
            return run;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OpenclawSkillTestRun> runBatchTests(String draftId, OpenclawSkillDraftBatchTestDTO dto) {
        if (dto == null || dto.getCases() == null || dto.getCases().isEmpty()) {
            throw new JeecgBootException("At least one test case is required.");
        }
        if (dto.getCases().size() > 10) {
            throw new JeecgBootException("Batch test supports at most 10 cases.");
        }
        List<OpenclawSkillTestRun> runs = new ArrayList<>();
        int index = 1;
        for (OpenclawSkillDraftBatchTestDTO.TestCase item : dto.getCases()) {
            if (item == null || !StringUtils.hasText(item.getPrompt())) {
                throw new JeecgBootException("Test case " + index + " prompt is required.");
            }
            OpenclawSkillDraftTestDTO testDTO = new OpenclawSkillDraftTestDTO();
            String namePrefix = StringUtils.hasText(item.getName()) ? "[" + item.getName().trim() + "] " : "[Case " + index + "] ";
            testDTO.setPrompt(namePrefix + item.getPrompt().trim());
            testDTO.setExpectedOutput(item.getExpectedOutput());
            runs.add(runTest(draftId, testDTO));
            index++;
        }
        auditLogService.logSuccess("skill_draft_batch_test", "skill_draft", draftId, Map.of("count", runs.size()));
        return runs;
    }

    @Override
    public OpenclawSkillTestReportVO testReport(String draftId, String testRunId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if (!StringUtils.hasText(testRunId)) {
            throw new JeecgBootException("Test run id is required.");
        }
        OpenclawSkillTestRun run = testRunService.getById(testRunId);
        if (run == null || !draft.getId().equals(run.getDraftId()) || run.getDelFlag() == null || run.getDelFlag() != OpenclawConstants.DEL_FLAG_NORMAL) {
            throw new JeecgBootException("Test run does not belong to this draft.");
        }
        return toTestReport(run);
    }

    @Override
    public List<OpenclawSkillDraftVersionVO> versions(String draftId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        return draftVersionMapper.selectList(new LambdaQueryWrapper<OpenclawSkillDraftVersion>()
                .eq(OpenclawSkillDraftVersion::getDraftId, draft.getId())
                .eq(OpenclawSkillDraftVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
                .orderByDesc(OpenclawSkillDraftVersion::getVersionNo))
            .stream()
            .map(version -> toVersionVO(version, false))
            .toList();
    }

    @Override
    public OpenclawSkillDraftVersionVO versionDetail(String draftId, Integer versionNo) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        OpenclawSkillDraftVersion version = requireDraftVersion(draft, versionNo);
        OpenclawSkillDraftVersionVO vo = toVersionVO(version, true);
        if (StringUtils.hasText(version.getSourceRecordId())) {
            vo.setSourceRecord(aiEditRecordMapper.selectById(version.getSourceRecordId()));
        }
        if (StringUtils.hasText(version.getTestRunId())) {
            OpenclawSkillTestRun run = testRunService.getById(version.getTestRunId());
            if (run != null && draft.getId().equals(run.getDraftId())) {
                vo.setTestReport(toTestReport(run));
            }
        }
        return vo;
    }

    @Override
    public OpenclawSkillDraftVersionVO diffVersion(String draftId, Integer fromVersionNo, Integer toVersionNo) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        Map<String, String> before = fromVersionNo == null ? readCurrentFileSnapshot(draft) : parseFileSnapshot(requireDraftVersion(draft, fromVersionNo).getFileSnapshot());
        Map<String, String> after = toVersionNo == null ? readCurrentFileSnapshot(draft) : parseFileSnapshot(requireDraftVersion(draft, toVersionNo).getFileSnapshot());
        OpenclawSkillDraftVersionVO vo = new OpenclawSkillDraftVersionVO();
        vo.setDraftId(draft.getId());
        vo.setVersionNo(toVersionNo);
        vo.setSummary((fromVersionNo == null ? "current" : "v" + fromVersionNo) + " -> " + (toVersionNo == null ? "current" : "v" + toVersionNo));
        vo.setDiffs(fileDiffs(before, after));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraftVersionVO rollbackVersion(String draftId, Integer versionNo) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        OpenclawSkillDraftVersion source = requireDraftVersion(draft, versionNo);
        Path root = draftRoot(draft);
        try {
            restoreSnapshot(root, parseFileSnapshot(source.getFileSnapshot()));
            scanFiles(draft);
            OpenclawSkillDraftVersion rollback = createDraftVersion(draft, "rollback", source.getId(), source.getTestRunId(), "Rollback to version " + source.getVersionNo(), true);
            auditLogService.logSuccess("skill_draft_version_rollback", "skill_draft", draft.getId(), Map.of(
                "fromVersionNo", source.getVersionNo(),
                "newVersionNo", rollback.getVersionNo()
            ));
            return toVersionVO(rollback, true);
        } catch (IOException e) {
            throw new JeecgBootException("Rollback Skill draft version failed: " + e.getMessage(), e);
        }
    }

    @Override
    public OpenclawSkillRepairVO repairDraft(String draftId, OpenclawSkillRepairDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if (REVIEW_LOCKED_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow repair.");
        }
        OpenclawSkillTestRun run = findRepairTestRun(draft, dto == null ? null : dto.getTestRunId());
        if (run == null || !StringUtils.hasText(run.getId())) {
            throw new JeecgBootException("AI Repair requires a testRunId or an existing failed test run.");
        }
        Path root = draftRoot(draft);
        try {
            String baseVersion = draftVersion(draft);
            String baseHash = sha256Directory(root);
            Map<String, String> files = readRepairableFiles(root);
            OpenclawSkillRepairVO repair = callConfiguredSkillRepair(draft, run, files, dto);
            if (repair == null) {
                repair = fallbackRepair(draft, run, files, dto);
            }
            repair.setDraftId(draft.getId());
            repair.setTestRunId(run.getId());
            skillAiEditValidator.validateModelResult(repair, root);
            OpenclawSkillAiEditRecord record = createSuggestionRecord(draft, run, dto == null ? null : dto.getInstruction(), repair, baseVersion, baseHash, "AI_REPAIR");
            repair.setRecordId(record.getId());
            repair.setBaseVersion(baseVersion);
            repair.setBaseHash(baseHash);
            repair.setStatus(record.getStatus());
            repair.setRepairBeforeStatus(record.getRepairBeforeStatus());
            Map<String, Object> auditDetail = new LinkedHashMap<>();
            auditDetail.put("recordId", record.getId());
            auditDetail.put("testRunId", repair.getTestRunId());
            auditDetail.put("source", repair.getSource());
            auditDetail.put("fileCount", repair.getFiles().size());
            auditLogService.logSuccess("skill_draft_ai_repair_preview", "skill_draft", draft.getId(), auditDetail);
            return repair;
        } catch (IOException e) {
            throw new JeecgBootException("Analyze Skill draft repair failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillAiEditVO previewAiEdit(String draftId, OpenclawSkillAiEditPreviewDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if (REVIEW_LOCKED_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow AI edit.");
        }
        if (dto == null || !StringUtils.hasText(dto.getInstruction())) {
            throw new JeecgBootException("AI edit instruction is required.");
        }
        OpenclawSkillRepairDTO repairDTO = new OpenclawSkillRepairDTO();
        repairDTO.setInstruction(dto.getInstruction());
        repairDTO.setTestRunId(dto.getTestRunId());
        OpenclawSkillTestRun run = findRepairTestRun(draft, dto.getTestRunId());
        Path root = draftRoot(draft);
        String baseVersion = draftVersion(draft);
        String baseHash;
        try {
            baseHash = sha256Directory(root);
            Map<String, String> files = readRepairableFiles(root);
            OpenclawSkillRepairVO repair = callConfiguredSkillRepair(draft, run, files, repairDTO);
            if (repair == null) {
                repair = fallbackRepair(draft, run, files, repairDTO);
            }
            repair.setDraftId(draft.getId());
            repair.setTestRunId(run == null ? null : run.getId());
            skillAiEditValidator.validateModelResult(repair, root);

            OpenclawSkillAiEditRecord record = new OpenclawSkillAiEditRecord();
            record.setId(IdWorker.getIdStr());
            record.setDraftId(draft.getId());
            record.setSkillId(draft.getSkillId());
            record.setWorkspaceId(null); // TODO: persist workspaceId when Skill drafts are bound to workspaces.
            record.setUserId(permissionService.currentUser().getId());
            record.setRecordType("AI_EDIT");
            record.setTestRunId(run == null ? null : run.getId());
            record.setUserInstruction(trim(dto.getInstruction(), 8000));
            record.setSummary(repair.getSummary());
            record.setFilesJson(JSON.toJSONString(repair.getFiles()));
            record.setWarningsJson(JSON.toJSONString(repair.getWarnings()));
            record.setBaseVersion(baseVersion);
            record.setBaseHash(baseHash);
            record.setStatus("PREVIEW");
            aiEditRecordMapper.insert(record);

            OpenclawSkillAiEditVO result = toAiEditVO(record, repair.getFiles(), repair.getWarnings());
            result.setTestRunId(repair.getTestRunId());
            result.setSource(repair.getSource());
            Map<String, Object> auditDetail = new LinkedHashMap<>();
            auditDetail.put("recordId", record.getId());
            if (StringUtils.hasText(result.getTestRunId())) {
                auditDetail.put("testRunId", result.getTestRunId());
            }
            auditDetail.put("source", result.getSource());
            auditDetail.put("fileCount", result.getFiles().size());
            auditLogService.logSuccess("skill_draft_ai_edit_preview", "skill_draft", draft.getId(), auditDetail);
            return result;
        } catch (IOException e) {
            throw new JeecgBootException("Analyze Skill AI edit failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillAiEditVO applyAiEdit(String draftId, OpenclawSkillAiEditApplyDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        if (dto == null || !StringUtils.hasText(dto.getRecordId())) {
            throw new JeecgBootException("AI edit recordId is required.");
        }
        OpenclawSkillAiEditRecord record = aiEditRecordMapper.selectById(dto.getRecordId());
        if (record == null || !draft.getId().equals(record.getDraftId())) {
            throw new JeecgBootException("AI edit record does not belong to this draft.");
        }
        if (!"PREVIEW".equals(record.getStatus())) {
            throw new JeecgBootException("AI edit record is not applicable.");
        }
        if (!permissionService.isSkillReviewer(permissionService.currentUser()) && !permissionService.currentUser().getId().equals(record.getUserId())) {
            throw new JeecgBootException("Only the preview owner can apply this AI edit.");
        }
        Path root = draftRoot(draft);
        try {
            String currentVersion = draftVersion(draft);
            String currentHash = sha256Directory(root);
            skillAiEditValidator.validateDraftVersion(record.getBaseVersion(), currentVersion, record.getBaseHash(), currentHash);
            List<OpenclawSkillRepairVO.FileSuggestion> files = JSON.parseArray(record.getFilesJson(), OpenclawSkillRepairVO.FileSuggestion.class);
            skillAiEditValidator.validateFiles(files, root);
            List<OpenclawSkillRepairVO.FileSuggestion> applied = applySuggestionFiles(root, files);
            scanFiles(draft);
            createDraftVersion(draft, "ai_edit", record.getId(), record.getTestRunId(), firstText(record.getSummary(), "AI Edit apply"), true);
            record.setStatus("APPLIED");
            record.setAppliedTime(new Date());
            record.setErrorMessage(null);
            aiEditRecordMapper.updateById(record);
            auditLogService.logSuccess("skill_draft_ai_edit_apply", "skill_draft", draft.getId(), Map.of(
                "recordId", record.getId(),
                "reason", trim(dto.getReason(), 1000),
                "files", applied
            ));
            OpenclawSkillAiEditVO result = toAiEditVO(record, applied, JSON.parseArray(record.getWarningsJson(), String.class));
            result.setSource("applied");
            result.setSummary("Applied " + applied.size() + " AI edit file change(s). Run Lint and tests next.");
            return result;
        } catch (IOException e) {
            record.setStatus("FAILED");
            record.setErrorMessage(trim(e.getMessage(), 1000));
            aiEditRecordMapper.updateById(record);
            throw new JeecgBootException("Apply Skill AI edit failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            record.setStatus("FAILED");
            record.setErrorMessage(trim(e.getMessage(), 1000));
            aiEditRecordMapper.updateById(record);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillRepairVO applyRepair(String draftId, OpenclawSkillRepairApplyDTO dto) {
        OpenclawSkillDraft draft = requireDraft(draftId, true);
        if (dto == null || !StringUtils.hasText(dto.getRecordId())) {
            throw new JeecgBootException("Repair recordId is required.");
        }
        OpenclawSkillAiEditRecord record = aiEditRecordMapper.selectById(dto.getRecordId());
        if (record == null || !draft.getId().equals(record.getDraftId()) || !"AI_REPAIR".equals(record.getRecordType())) {
            throw new JeecgBootException("Repair record does not belong to this draft.");
        }
        if (!"PREVIEW".equals(record.getStatus())) {
            throw new JeecgBootException("Repair record is not applicable.");
        }
        if (!permissionService.isSkillReviewer(permissionService.currentUser()) && !permissionService.currentUser().getId().equals(record.getUserId())) {
            throw new JeecgBootException("Only the preview owner can apply this repair.");
        }
        Path root = draftRoot(draft);
        try {
            String currentVersion = draftVersion(draft);
            String currentHash = sha256Directory(root);
            skillAiEditValidator.validateDraftVersion(record.getBaseVersion(), currentVersion, record.getBaseHash(), currentHash);
            List<OpenclawSkillRepairVO.FileSuggestion> files = JSON.parseArray(record.getFilesJson(), OpenclawSkillRepairVO.FileSuggestion.class);
            skillAiEditValidator.validateFiles(files, root);
            List<OpenclawSkillRepairVO.FileSuggestion> applied = applySuggestionFiles(root, files);
            if (applied.isEmpty()) {
                throw new JeecgBootException("No repair file can be applied.");
            }
            scanFiles(draft);
            createDraftVersion(draft, "ai_repair", record.getId(), record.getTestRunId(), firstText(record.getSummary(), "AI Repair apply"), true);
            record.setStatus("APPLIED");
            record.setAppliedTime(new Date());
            record.setRepairAfterStatus(draft.getLastTestStatus());
            record.setErrorMessage(null);
            aiEditRecordMapper.updateById(record);
            Map<String, Object> auditDetail = new LinkedHashMap<>();
            auditDetail.put("recordId", record.getId());
            auditDetail.put("testRunId", record.getTestRunId());
            auditDetail.put("reason", trim(dto.getReason(), 1000));
            auditDetail.put("beforeStatus", record.getRepairBeforeStatus());
            auditDetail.put("afterStatus", record.getRepairAfterStatus());
            auditDetail.put("files", applied);
            auditLogService.logSuccess("skill_draft_ai_repair_apply", "skill_draft", draft.getId(), auditDetail);
            OpenclawSkillRepairVO result = new OpenclawSkillRepairVO();
            result.setRecordId(record.getId());
            result.setDraftId(draft.getId());
            result.setTestRunId(record.getTestRunId());
            result.setSource("applied");
            result.setSummary("Applied " + applied.size() + " repair file change(s). Run Lint and tests again.");
            result.setFiles(applied);
            result.setStatus(record.getStatus());
            result.setRepairBeforeStatus(record.getRepairBeforeStatus());
            result.setRepairAfterStatus(record.getRepairAfterStatus());
            return result;
        } catch (IOException e) {
            record.setStatus("FAILED");
            record.setErrorMessage(trim(e.getMessage(), 1000));
            aiEditRecordMapper.updateById(record);
            throw new JeecgBootException("Apply Skill repair failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            record.setStatus("FAILED");
            record.setErrorMessage(trim(e.getMessage(), 1000));
            aiEditRecordMapper.updateById(record);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft submitForReview(String draftId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
        if ("submitted".equals(draft.getStatus()) || "approved".equals(draft.getStatus()) || "published".equals(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow submit.");
        }

        OpenclawSkillDraftLintVO lint = lint(draftId);
        if (!Boolean.TRUE.equals(lint.getPassed())) {
            throw new JeecgBootException("Lint must pass before submit.");
        }

        Long successTestCount = testRunService.count(new LambdaQueryWrapper<OpenclawSkillTestRun>()
            .eq(OpenclawSkillTestRun::getDraftId, draft.getId())
            .eq(OpenclawSkillTestRun::getStatus, OpenclawConstants.RUN_STATUS_SUCCESS)
            .eq(OpenclawSkillTestRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL));
        if (successTestCount == null || successTestCount < 1 || !OpenclawConstants.RUN_STATUS_SUCCESS.equals(draft.getLastTestStatus())) {
            throw new JeecgBootException("At least one successful test run is required before submit.");
        }
        OpenclawSkillDraftVersion latestVersion = latestDraftVersion(draft);
        if (latestVersion == null
            || !"lint_passed".equals(latestVersion.getLintStatus())
            || !OpenclawConstants.RUN_STATUS_SUCCESS.equals(latestVersion.getTestStatus())) {
            throw new JeecgBootException("Only a version with lint passed and test passed can be submitted.");
        }

        draft.setStatus("submitted");
        draft.setSubmitTime(new Date());
        draft.setReviewStatus("pending");
        draft.setReviewComment(null);
        draft.setReviewedBy(null);
        draft.setReviewedTime(null);
        updateById(draft);
        auditLogService.log("skill_draft_submit", "skill_draft", draft.getId(), draft);
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft approveDraft(String draftId) {
        OpenclawSkillDraft draft = requireReviewableDraft(draftId);
        LoginUser reviewer = permissionService.currentUser();
        draft.setStatus("approved");
        draft.setReviewStatus("approved");
        draft.setReviewComment(null);
        draft.setReviewedBy(reviewer.getUsername());
        draft.setReviewedTime(new Date());
        updateById(draft);
        auditLogService.logSuccess("skill_draft_approve", "skill_draft", draft.getId(), draft);
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraft rejectDraft(String draftId, String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new JeecgBootException("Reject reason is required.");
        }
        OpenclawSkillDraft draft = requireReviewableDraft(draftId);
        LoginUser reviewer = permissionService.currentUser();
        draft.setStatus("rejected");
        draft.setReviewStatus("rejected");
        draft.setReviewComment(trim(reason, 1000));
        draft.setReviewedBy(reviewer.getUsername());
        draft.setReviewedTime(new Date());
        updateById(draft);
        auditLogService.logFailure("skill_draft_reject", "skill_draft", draft.getId(), draft);
        return draft;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkill publishDraft(String draftId) {
        OpenclawSkillDraft draft = requirePublishableDraft(draftId);
        String version = nextPublishVersion(draft);
        Path targetDir = officialSkillPath(draft, version);
        if (Files.exists(targetDir)) {
            throw new JeecgBootException("Skill target directory already exists.");
        }
        try {
            Files.createDirectories(targetDir.getParent());
            copyDirectory(draftRoot(draft), targetDir);
            writeSkillManifest(targetDir, draft.getDraftName(), draft.getSkillSlug(), version, draft.getDescription(), draft.getOwnerUsername());

            OpenclawSkill skill = new OpenclawSkill();
            skill.setOwnerUserId(draft.getOwnerUserId());
            skill.setOwnerUsername(draft.getOwnerUsername());
            skill.setName(draft.getDraftName());
            skill.setSlug(draft.getSkillSlug());
            skill.setVersion(version);
            skill.setScope("public");
            skill.setStatus(OpenclawConstants.SKILL_STATUS_APPROVED);
            skill.setDescription(draft.getDescription());
            skill.setPath(targetDir.toString());
            skill.setChecksum(sha256Directory(targetDir));
            skill.setFileSize(directorySize(targetDir));
            skill.setRemark("Published from Skill Draft " + draft.getId());
            skill.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
            skillService.save(skill);

            draft.setStatus("published");
            draft.setReviewStatus("published");
            draft.setSkillId(skill.getId());
            updateById(draft);
            auditLogService.logSuccess("skill_draft_publish", "skill_draft", draft.getId(), Map.of("draft", draft, "skill", skill));
            return skill;
        } catch (IOException e) {
            cleanupQuietly(targetDir);
            throw new JeecgBootException("Publish Skill draft failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            cleanupQuietly(targetDir);
            throw e;
        }
    }

    private OpenclawSkillDraft requireDraft(String draftId, boolean editable) {
        OpenclawSkillDraft draft = getById(draftId);
        if (draft == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(draft.getDelFlag())) {
            throw new JeecgBootException("Skill draft does not exist.");
        }
        LoginUser user = permissionService.currentUser();
        if (editable || !permissionService.isSkillReviewer(user)) {
            permissionService.checkOwnerOrAdmin(draft.getOwnerUserId());
        }
        if (editable && !EDITABLE_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow editing.");
        }
        return draft;
    }

    private OpenclawSkillDraftVersion createDraftVersion(OpenclawSkillDraft draft, String sourceType, String sourceRecordId, String testRunId, String summary, boolean resetValidation) {
        try {
            LoginUser user = permissionService.currentUser();
            OpenclawSkillDraftVersion latest = latestDraftVersion(draft);
            OpenclawSkillDraftVersion version = new OpenclawSkillDraftVersion();
            version.setId(IdWorker.getIdStr());
            version.setDraftId(draft.getId());
            version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
            version.setSourceType(sourceType);
            version.setSourceRecordId(sourceRecordId);
            version.setTestRunId(testRunId);
            version.setFileSnapshot(JSON.toJSONString(readCurrentFileSnapshot(draft)));
            version.setFileHash(sha256Directory(draftRoot(draft)));
            version.setSummary(trim(summary, 1000));
            version.setLintStatus(resetValidation ? null : draft.getLastLintStatus());
            version.setTestStatus(resetValidation ? null : draft.getLastTestStatus());
            version.setCreateBy(user == null ? null : user.getUsername());
            version.setCreateTime(new Date());
            version.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
            draftVersionMapper.insert(version);
            if (resetValidation) {
                draft.setStatus("editing");
                draft.setLastLintStatus(null);
                draft.setLastLintResultJson(null);
                draft.setLastTestStatus(null);
                draft.setLastTestRunId(null);
                updateById(draft);
            }
            return version;
        } catch (IOException e) {
            throw new JeecgBootException("Create Skill draft version failed: " + e.getMessage(), e);
        }
    }

    private OpenclawSkillDraftVersion ensureCurrentDraftVersion(OpenclawSkillDraft draft, String sourceType, String summary) {
        try {
            OpenclawSkillDraftVersion latest = latestDraftVersion(draft);
            String currentHash = sha256Directory(draftRoot(draft));
            if (latest != null && currentHash.equals(latest.getFileHash())) {
                return latest;
            }
            return createDraftVersion(draft, sourceType, null, null, summary, false);
        } catch (IOException e) {
            throw new JeecgBootException("Ensure Skill draft version failed: " + e.getMessage(), e);
        }
    }

    private OpenclawSkillDraftVersion latestDraftVersion(OpenclawSkillDraft draft) {
        return draftVersionMapper.selectOne(new LambdaQueryWrapper<OpenclawSkillDraftVersion>()
            .eq(OpenclawSkillDraftVersion::getDraftId, draft.getId())
            .eq(OpenclawSkillDraftVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .orderByDesc(OpenclawSkillDraftVersion::getVersionNo)
            .last("limit 1"));
    }

    private OpenclawSkillDraftVersion requireDraftVersion(OpenclawSkillDraft draft, Integer versionNo) {
        if (versionNo == null || versionNo < 1) {
            throw new JeecgBootException("Version number is required.");
        }
        OpenclawSkillDraftVersion version = draftVersionMapper.selectOne(new LambdaQueryWrapper<OpenclawSkillDraftVersion>()
            .eq(OpenclawSkillDraftVersion::getDraftId, draft.getId())
            .eq(OpenclawSkillDraftVersion::getVersionNo, versionNo)
            .eq(OpenclawSkillDraftVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1"));
        if (version == null) {
            throw new JeecgBootException("Skill draft version does not exist.");
        }
        return version;
    }

    private void updateVersionLintStatus(OpenclawSkillDraft draft, String lintStatus) {
        try {
            OpenclawSkillDraftVersion version = latestDraftVersion(draft);
            if (version == null) {
                version = createDraftVersion(draft, "manual", null, null, "Lint snapshot", false);
            }
            String currentHash = sha256Directory(draftRoot(draft));
            if (!currentHash.equals(version.getFileHash())) {
                version = createDraftVersion(draft, "manual", null, null, "Lint snapshot", false);
            }
            version.setLintStatus(lintStatus);
            draftVersionMapper.updateById(version);
        } catch (IOException e) {
            throw new JeecgBootException("Update Skill draft version lint status failed: " + e.getMessage(), e);
        }
    }

    private void updateVersionTestStatus(OpenclawSkillDraft draft, OpenclawSkillTestRun run, String status) {
        if (run == null || run.getDraftVersionNo() == null) {
            return;
        }
        OpenclawSkillDraftVersion version = requireDraftVersion(draft, run.getDraftVersionNo());
        if (StringUtils.hasText(run.getFileHash()) && !run.getFileHash().equals(version.getFileHash())) {
            return;
        }
        version.setTestRunId(run.getId());
        version.setTestStatus(status);
        if (StringUtils.hasText(run.getLintStatus())) {
            version.setLintStatus(run.getLintStatus());
        }
        draftVersionMapper.updateById(version);
    }

    private OpenclawSkillDraftVersionVO toVersionVO(OpenclawSkillDraftVersion version, boolean includeFiles) {
        OpenclawSkillDraftVersionVO vo = new OpenclawSkillDraftVersionVO();
        vo.setId(version.getId());
        vo.setDraftId(version.getDraftId());
        vo.setVersionNo(version.getVersionNo());
        vo.setSourceType(version.getSourceType());
        vo.setSourceRecordId(version.getSourceRecordId());
        vo.setTestRunId(version.getTestRunId());
        vo.setFileHash(version.getFileHash());
        vo.setSummary(version.getSummary());
        vo.setLintStatus(version.getLintStatus());
        vo.setTestStatus(version.getTestStatus());
        vo.setCreatedBy(version.getCreateBy());
        vo.setCreatedTime(version.getCreateTime());
        if (includeFiles) {
            vo.setFiles(parseFileSnapshot(version.getFileSnapshot()));
        }
        return vo;
    }

    private Map<String, String> readCurrentFileSnapshot(OpenclawSkillDraft draft) {
        try {
            Map<String, String> files = new LinkedHashMap<>();
            Path root = draftRoot(draft);
            try (var walk = Files.walk(root)) {
                for (Path item : walk.sorted().toList()) {
                    pathSafetyService.rejectIfOutsideRoot(root, item);
                    if (!Files.isRegularFile(item)) {
                        continue;
                    }
                    String relative = toRelative(root, item);
                    pathSafetyService.rejectBlockedExtension(relative);
                    pathSafetyService.rejectOversized(item);
                    files.put(relative, Files.readString(item, StandardCharsets.UTF_8));
                }
            }
            return files;
        } catch (IOException e) {
            throw new JeecgBootException("Read Skill draft snapshot failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> parseFileSnapshot(String snapshot) {
        Map<String, String> files = new LinkedHashMap<>();
        if (!StringUtils.hasText(snapshot)) {
            return files;
        }
        JSONObject json = JSON.parseObject(snapshot);
        for (String key : json.keySet()) {
            if (StringUtils.hasText(key)) {
                files.put(key, json.getString(key));
            }
        }
        return files;
    }

    private List<OpenclawSkillDraftVersionVO.FileDiff> fileDiffs(Map<String, String> before, Map<String, String> after) {
        List<OpenclawSkillDraftVersionVO.FileDiff> diffs = new ArrayList<>();
        Set<String> paths = new java.util.TreeSet<>();
        paths.addAll(before.keySet());
        paths.addAll(after.keySet());
        for (String path : paths) {
            String oldContent = before.get(path);
            String newContent = after.get(path);
            if (oldContent == null) {
                diffs.add(fileDiff(path, "added", null, sha256Text(newContent)));
            } else if (newContent == null) {
                diffs.add(fileDiff(path, "deleted", sha256Text(oldContent), null));
            } else if (!oldContent.equals(newContent)) {
                diffs.add(fileDiff(path, "modified", sha256Text(oldContent), sha256Text(newContent)));
            }
        }
        return diffs;
    }

    private OpenclawSkillDraftVersionVO.FileDiff fileDiff(String path, String changeType, String beforeHash, String afterHash) {
        OpenclawSkillDraftVersionVO.FileDiff diff = new OpenclawSkillDraftVersionVO.FileDiff();
        diff.setPath(path);
        diff.setChangeType(changeType);
        diff.setBeforeHash(beforeHash);
        diff.setAfterHash(afterHash);
        return diff;
    }

    private void restoreSnapshot(Path root, Map<String, String> files) throws IOException {
        cleanupDraftFiles(root);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            byte[] data = (entry.getValue() == null ? "" : entry.getValue()).getBytes(StandardCharsets.UTF_8);
            pathSafetyService.validateWritableFile(root, entry.getKey(), data.length);
            Path target = pathSafetyService.resolve(root, entry.getKey());
            pathSafetyService.rejectIfOutsideRoot(root, target);
            Files.createDirectories(target.getParent());
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        }
    }

    private OpenclawSkillDraft requireReviewableDraft(String draftId) {
        LoginUser reviewer = permissionService.currentUser();
        if (!permissionService.isSkillReviewer(reviewer)) {
            throw new JeecgBootException("Only OpenClaw Skill reviewers can review Skill drafts.");
        }
        OpenclawSkillDraft draft = getById(draftId);
        if (draft == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(draft.getDelFlag())) {
            throw new JeecgBootException("Skill draft does not exist.");
        }
        if (!"submitted".equals(draft.getStatus())) {
            throw new JeecgBootException("Only submitted Skill drafts can be reviewed.");
        }
        return draft;
    }

    private OpenclawSkillDraft requirePublishableDraft(String draftId) {
        LoginUser reviewer = permissionService.currentUser();
        if (!permissionService.isSkillReviewer(reviewer)) {
            throw new JeecgBootException("Only OpenClaw Skill reviewers can publish Skill drafts.");
        }
        OpenclawSkillDraft draft = getById(draftId);
        if (draft == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(draft.getDelFlag())) {
            throw new JeecgBootException("Skill draft does not exist.");
        }
        if (!"approved".equals(draft.getStatus())) {
            throw new JeecgBootException("Only approved Skill drafts can be published.");
        }
        return draft;
    }

    private String nextPublishVersion(OpenclawSkillDraft draft) {
        String version = incrementPatchVersion(draft.getBaseVersion());
        int guard = 0;
        while (skillService.lambdaQuery()
            .eq(OpenclawSkill::getOwnerUserId, draft.getOwnerUserId())
            .eq(OpenclawSkill::getSlug, draft.getSkillSlug())
            .eq(OpenclawSkill::getVersion, version)
            .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count() > 0) {
            version = incrementPatchVersion(version);
            guard++;
            if (guard > 1000) {
                throw new JeecgBootException("Cannot allocate next Skill version.");
            }
        }
        return version;
    }

    private String incrementPatchVersion(String baseVersion) {
        if (!StringUtils.hasText(baseVersion)) {
            return "1.0.0";
        }
        String version = baseVersion.trim();
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            return version + ".1";
        }
        try {
            int patch = Integer.parseInt(parts[2]);
            return parts[0] + "." + parts[1] + "." + (patch + 1);
        } catch (NumberFormatException e) {
            return version + ".1";
        }
    }

    private Path officialSkillPath(OpenclawSkillDraft draft, String version) {
        Path ownerRoot = Paths.get(OpenclawConstants.SKILL_ROOT, draft.getOwnerUserId()).toAbsolutePath().normalize();
        Path target = ownerRoot.resolve(draft.getSkillSlug()).resolve(version).normalize();
        if (!target.startsWith(ownerRoot)) {
            throw new JeecgBootException("Invalid Skill target path.");
        }
        return target;
    }

    private void validateDraftRequest(OpenclawSkillDraftCreateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getDraftName())) {
            throw new JeecgBootException("Draft name is required.");
        }
        if (!StringUtils.hasText(dto.getSkillSlug())) {
            throw new JeecgBootException("Skill slug is required.");
        }
    }

    private Path draftPath(String userId, String draftId) {
        Path root = Paths.get(DRAFT_ROOT, userId).normalize();
        Path path = root.resolve(draftId).normalize();
        if (!path.startsWith(root)) {
            throw new JeecgBootException("Invalid draft path.");
        }
        return path;
    }

    private Path draftRoot(OpenclawSkillDraft draft) {
        Path root = Paths.get(draft.getDraftPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new JeecgBootException("Draft directory does not exist.");
        }
        return root;
    }

    private void writeDefaultFiles(Path root, String name, String slug, String description) throws IOException {
        String safeDescription = StringUtils.hasText(description) ? description.trim() : "Describe what this Skill does.";
        Files.writeString(root.resolve("SKILL.md"),
            "# " + name.trim() + "\n\n"
                + "## Purpose\n\n" + safeDescription + "\n\n"
                + "## When to use\n\nUse this Skill when the Agent needs this workflow.\n\n"
                + "## Inputs\n\nDescribe required inputs.\n\n"
                + "## Outputs\n\nDescribe expected outputs.\n\n"
                + "## Usage\n\nCall this Skill from an Agent run.\n\n"
                + "## Examples\n\n- Test prompt: run the first smoke test.\n\n"
                + "## Safety\n\nDocument file, network, and command execution behavior.\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve("main.py"), "def run(input_text: str) -> str:\n    return input_text\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve("requirements.txt"), "# Add Python dependencies here.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.createDirectories(root.resolve("examples"));
        Files.writeString(root.resolve("examples").resolve("test_prompt.md"), "Run a smoke test for " + slug + ".\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(root.resolve("README.md"), "# " + name.trim() + "\n\n" + safeDescription + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private void scanFiles(OpenclawSkillDraft draft) throws IOException {
        draftFileMapper.delete(new LambdaQueryWrapper<OpenclawSkillDraftFile>().eq(OpenclawSkillDraftFile::getDraftId, draft.getId()));
        Path root = draftRoot(draft);
        try (var walk = Files.walk(root)) {
            for (Path item : walk.sorted().toList()) {
                if (root.equals(item)) {
                    continue;
                }
                pathSafetyService.rejectIfOutsideRoot(root, item);
                String relative = toRelative(root, item);
                OpenclawSkillDraftFile file = new OpenclawSkillDraftFile();
                file.setDraftId(draft.getId());
                file.setFilePath(relative);
                file.setFileType(Files.isDirectory(item) ? "directory" : "file");
                file.setSizeBytes(Files.isRegularFile(item) ? Files.size(item) : 0L);
                file.setChecksum(Files.isRegularFile(item) ? sha256File(item) : null);
                file.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
                draftFileMapper.insert(file);
            }
        }
    }

    private List<OpenclawSkillDraftFileNodeVO> buildTree(Path root) throws IOException {
        try (var list = Files.list(root)) {
            return list.sorted(Comparator.comparing(path -> path.getFileName().toString())).map(path -> {
                OpenclawSkillDraftFileNodeVO node = new OpenclawSkillDraftFileNodeVO();
                node.setName(path.getFileName().toString());
                node.setPath(toRelative(root, path));
                node.setType(Files.isDirectory(path) ? "directory" : "file");
                try {
                    node.setSize(Files.isRegularFile(path) ? Files.size(path) : 0L);
                    if (Files.isDirectory(path)) {
                        node.setChildren(buildTree(root, path));
                    }
                } catch (IOException e) {
                    throw new JeecgBootException("Read draft file tree failed: " + e.getMessage(), e);
                }
                return node;
            }).toList();
        }
    }

    private List<OpenclawSkillDraftFileNodeVO> buildTree(Path root, Path current) throws IOException {
        try (var list = Files.list(current)) {
            return list.sorted(Comparator.comparing(path -> path.getFileName().toString())).map(path -> {
                OpenclawSkillDraftFileNodeVO node = new OpenclawSkillDraftFileNodeVO();
                node.setName(path.getFileName().toString());
                node.setPath(toRelative(root, path));
                node.setType(Files.isDirectory(path) ? "directory" : "file");
                try {
                    node.setSize(Files.isRegularFile(path) ? Files.size(path) : 0L);
                    if (Files.isDirectory(path)) {
                        node.setChildren(buildTree(root, path));
                    }
                } catch (IOException e) {
                    throw new JeecgBootException("Read draft file tree failed: " + e.getMessage(), e);
                }
                return node;
            }).toList();
        }
    }

    private void inspectFiles(Path root, OpenclawSkillDraftLintVO result) throws IOException {
        int fileCount = 0;
        long totalSize = 0L;
        try (var walk = Files.walk(root)) {
            for (Path item : walk.toList()) {
                pathSafetyService.rejectIfOutsideRoot(root, item);
                if (Files.isSymbolicLink(item)) {
                    result.getErrors().add("Symbolic link is not allowed: " + toRelative(root, item));
                    continue;
                }
                if (Files.isRegularFile(item)) {
                    fileCount++;
                    totalSize += Files.size(item);
                    String relative = toRelative(root, item);
                    try {
                        pathSafetyService.rejectBlockedExtension(relative);
                    } catch (JeecgBootException e) {
                        result.getErrors().add(e.getMessage() + " " + relative);
                    }
                    inspectCodeRisk(item, relative, result);
                }
            }
        }
        result.setFileCount(fileCount);
        result.setTotalSize(totalSize);
        if (fileCount > OpenclawConstants.MAX_SKILL_ZIP_FILE_COUNT) {
            result.getErrors().add("Too many files.");
        }
        if (totalSize > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
            result.getErrors().add("Draft size exceeds the limit.");
        }
    }

    private void inspectSkillMd(Path root, OpenclawSkillDraftLintVO result) throws IOException {
        Path skillMd = root.resolve("SKILL.md");
        if (!Files.isRegularFile(skillMd)) {
            result.getErrors().add("SKILL.md is required.");
            return;
        }
        String content = Files.readString(skillMd, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(content)) {
            result.getErrors().add("SKILL.md cannot be empty.");
            return;
        }
        warnMissing(content, "# ", "SKILL.md title is missing.", result);
        warnMissing(content, "## Purpose", "Purpose section is missing.", result);
        warnMissing(content, "## When to use", "When to use section is missing.", result);
        warnMissing(content, "## Inputs", "Inputs section is missing.", result);
        warnMissing(content, "## Outputs", "Outputs section is missing.", result);
        warnMissing(content, "## Examples", "Examples section is missing.", result);
        warnMissing(content, "## Safety", "Safety section is missing.", result);
    }

    private void inspectCodeRisk(Path file, String relative, OpenclawSkillDraftLintVO result) throws IOException {
        if (!relative.endsWith(".py") && !relative.endsWith(".md") && !relative.endsWith(".txt")) {
            return;
        }
        String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String keyword : DANGEROUS_CODE_KEYWORDS) {
            if (content.contains(keyword)) {
                if ("sudo".equals(keyword) || keyword.startsWith("rm -rf")) {
                    result.getErrors().add("Dangerous code keyword in " + relative + ": " + keyword);
                } else {
                    result.getWarnings().add("Code risk keyword in " + relative + ": " + keyword);
                }
            }
        }
    }

    private void warnMissing(String content, String token, String warning, OpenclawSkillDraftLintVO result) {
        if (!content.contains(token)) {
            result.getWarnings().add(warning);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (var walk = Files.walk(source)) {
            for (Path item : walk.sorted().toList()) {
                Path destination = target.resolve(source.relativize(item)).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Invalid copied path.");
                }
                if (Files.isDirectory(item)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(item, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private GeneratedSkillSpec generateSkillSpec(OpenclawSkillGenerateDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getRequirement())) {
            throw new JeecgBootException("Skill generation requirement is required.");
        }
        try {
            GeneratedSkillSpec aiSpec = callConfiguredSkillGenerator(dto);
            if (aiSpec != null) {
                return aiSpec;
            }
        } catch (Exception e) {
            auditLogService.logFailure("skill_draft_ai_generate_model", "skill_draft", "new", Map.of("message", trim(e.getMessage(), 1000)));
        }
        return fallbackGeneratedSpec(dto);
    }

    private GeneratedSkillSpec callConfiguredSkillGenerator(OpenclawSkillGenerateDTO dto) throws IOException, InterruptedException {
        if (!StringUtils.hasText(skillAiBaseUrl) || !StringUtils.hasText(skillAiModel)) {
            return null;
        }
        String baseUrl = skillAiBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        JSONObject body = new JSONObject(true);
        body.put("model", skillAiModel.trim());
        body.put("temperature", 0.2);
        JSONArray messages = new JSONArray();
        messages.add(message("system", "You generate OpenClaw Skill draft files. Return only strict JSON with fields draftName, skillSlug, description, files. files is an array of {path, content}. Include SKILL.md with Purpose, When to use, Inputs, Outputs, Examples, Safety sections. Do not include unsafe commands or binary files."));
        messages.add(message("user", "Requirement:\n" + dto.getRequirement() + "\n\nPreferred draftName: " + safeText(dto.getDraftName()) + "\nPreferred skillSlug: " + safeText(dto.getSkillSlug()) + "\nDescription: " + safeText(dto.getDescription())));
        body.put("messages", messages);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (StringUtils.hasText(skillAiApiKey)) {
            builder.header("Authorization", "Bearer " + skillAiApiKey.trim());
        }
        HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new JeecgBootException("Skill AI generator returned HTTP " + response.statusCode() + ": " + trim(response.body(), 1000));
        }
        JSONObject root = JSON.parseObject(response.body());
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new JeecgBootException("Skill AI generator returned no choices.");
        }
        JSONObject first = choices.getJSONObject(0);
        JSONObject responseMessage = first == null ? null : first.getJSONObject("message");
        String content = responseMessage == null ? null : responseMessage.getString("content");
        if (!StringUtils.hasText(content)) {
            throw new JeecgBootException("Skill AI generator returned empty content.");
        }
        return parseGeneratedSpec(dto, JSON.parseObject(extractJsonObject(content)));
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject(true);
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String extractJsonObject(String content) {
        String value = content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new JeecgBootException("Skill AI generator response does not contain a JSON object.");
        }
        return value.substring(start, end + 1);
    }

    private GeneratedSkillSpec parseGeneratedSpec(OpenclawSkillGenerateDTO dto, JSONObject json) {
        GeneratedSkillSpec spec = new GeneratedSkillSpec();
        spec.draftName = firstText(json.getString("draftName"), dto.getDraftName(), "Generated OpenClaw Skill");
        spec.skillSlug = normalizeSlug(firstText(json.getString("skillSlug"), dto.getSkillSlug(), spec.draftName));
        spec.description = trim(firstText(json.getString("description"), dto.getDescription(), dto.getRequirement()), 2000);
        spec.files = new LinkedHashMap<>();
        JSONArray files = json.getJSONArray("files");
        if (files != null) {
            for (Object item : files) {
                if (item instanceof JSONObject file) {
                    addGeneratedFile(spec, file.getString("path"), file.getString("content"));
                }
            }
        }
        if (!spec.files.containsKey("SKILL.md")) {
            addGeneratedFile(spec, "SKILL.md", skillMdContent(spec.draftName, spec.description, dto.getRequirement()));
        }
        return spec;
    }

    private GeneratedSkillSpec fallbackGeneratedSpec(OpenclawSkillGenerateDTO dto) {
        GeneratedSkillSpec spec = new GeneratedSkillSpec();
        spec.draftName = firstText(dto.getDraftName(), "Generated OpenClaw Skill");
        spec.skillSlug = normalizeSlug(firstText(dto.getSkillSlug(), spec.draftName));
        spec.description = trim(firstText(dto.getDescription(), dto.getRequirement()), 2000);
        spec.files = new LinkedHashMap<>();
        addGeneratedFile(spec, "SKILL.md", skillMdContent(spec.draftName, spec.description, dto.getRequirement()));
        addGeneratedFile(spec, "README.md", "# " + spec.draftName + "\n\n" + spec.description + "\n\nGenerated as an editable OpenClaw Skill draft. Run Lint and tests before submitting for review.\n");
        addGeneratedFile(spec, "examples/test_prompt.md", "Use this Skill for the following requirement:\n\n" + dto.getRequirement() + "\n");
        return spec;
    }

    private String skillMdContent(String name, String description, String requirement) {
        return "# " + safeText(name) + "\n\n"
            + "## Purpose\n\n" + safeText(description) + "\n\n"
            + "## When to use\n\nUse this Skill when the task matches this requirement:\n\n" + safeText(requirement) + "\n\n"
            + "## Inputs\n\n- A user request related to the requirement.\n- Any files or context provided by the user.\n\n"
            + "## Outputs\n\n- A clear response or generated artifact that satisfies the request.\n- Notes about assumptions, limits, and follow-up checks when needed.\n\n"
            + "## Examples\n\nUser asks for help with the requirement. The agent applies this Skill, checks the available context, and returns the requested result.\n\n"
            + "## Safety\n\nDo not run destructive commands, access files outside the workspace, or send sensitive data to external services unless the user explicitly requests it and policy allows it.\n";
    }

    private void addGeneratedFile(GeneratedSkillSpec spec, String path, String content) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..") || normalized.startsWith("../")) {
            throw new JeecgBootException("Generated file path is invalid: " + path);
        }
        pathSafetyService.rejectBlockedExtension(normalized);
        spec.files.put(normalized, content == null ? "" : trim(content, 200_000));
    }

    private void cleanupDraftFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!item.equals(root)) {
                    pathSafetyService.rejectIfOutsideRoot(root, item);
                    Files.deleteIfExists(item);
                }
            }
        }
    }

    private void writeGeneratedFiles(Path root, Map<String, String> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new JeecgBootException("Generated Skill must contain at least one file.");
        }
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path target = root.resolve(entry.getKey()).normalize();
            pathSafetyService.rejectIfOutsideRoot(root, target);
            Files.createDirectories(target.getParent());
            Files.writeString(target, entry.getValue(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }

    private OpenclawSkillTestRun findRepairTestRun(OpenclawSkillDraft draft, String testRunId) {
        if (StringUtils.hasText(testRunId)) {
            OpenclawSkillTestRun run = testRunService.getById(testRunId);
            if (run == null || !draft.getId().equals(run.getDraftId())) {
                throw new JeecgBootException("Test run does not belong to this draft.");
            }
            return run;
        }
        if (StringUtils.hasText(draft.getLastTestRunId())) {
            OpenclawSkillTestRun run = testRunService.getById(draft.getLastTestRunId());
            if (run != null && draft.getId().equals(run.getDraftId())) {
                return run;
            }
        }
        return testRunService.getOne(new LambdaQueryWrapper<OpenclawSkillTestRun>()
            .eq(OpenclawSkillTestRun::getDraftId, draft.getId())
            .eq(OpenclawSkillTestRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .orderByDesc(OpenclawSkillTestRun::getCreateTime)
            .last("limit 1"), false);
    }

    private Map<String, String> readRepairableFiles(Path root) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (var walk = Files.walk(root)) {
            for (Path item : walk.sorted().toList()) {
                pathSafetyService.rejectIfOutsideRoot(root, item);
                if (!Files.isRegularFile(item)) {
                    continue;
                }
                String relative = toRelative(root, item);
                pathSafetyService.rejectBlockedExtension(relative);
                long size = Files.size(item);
                if (size > 200_000L) {
                    continue;
                }
                if (isTextRepairFile(relative)) {
                    files.put(relative, Files.readString(item, StandardCharsets.UTF_8));
                }
            }
        }
        return files;
    }

    private boolean isTextRepairFile(String path) {
        String value = path.toLowerCase(Locale.ROOT);
        return value.endsWith(".md") || value.endsWith(".txt") || value.endsWith(".py") || value.endsWith(".json")
            || value.endsWith(".yaml") || value.endsWith(".yml") || value.endsWith(".toml") || value.endsWith(".ini");
    }

    private OpenclawSkillRepairVO callConfiguredSkillRepair(OpenclawSkillDraft draft, OpenclawSkillTestRun run, Map<String, String> files, OpenclawSkillRepairDTO dto) {
        if (!StringUtils.hasText(skillAiBaseUrl) || !StringUtils.hasText(skillAiModel)) {
            return null;
        }
        try {
            String baseUrl = skillAiBaseUrl.trim();
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            JSONObject body = new JSONObject(true);
            body.put("model", skillAiModel.trim());
            body.put("temperature", 0.1);
            JSONArray messages = new JSONArray();
            messages.add(message("system", "You edit or repair OpenClaw Skill draft files from a natural-language instruction and optional test failure context. Return only strict JSON with fields summary, files, warnings. files is an array of {path, action, explanation, content}. Only suggest safe text file upserts. Preserve existing behavior unless the user instruction, lint result, or test failure requires a change."));
            messages.add(message("user", buildRepairPrompt(draft, run, files, dto)));
            body.put("messages", messages);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
            if (StringUtils.hasText(skillAiApiKey)) {
                builder.header("Authorization", "Bearer " + skillAiApiKey.trim());
            }
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new JeecgBootException("Skill AI repair returned HTTP " + response.statusCode() + ": " + trim(response.body(), 1000));
            }
            JSONObject root = JSON.parseObject(response.body());
            JSONArray choices = root.getJSONArray("choices");
            JSONObject first = choices == null || choices.isEmpty() ? null : choices.getJSONObject(0);
            JSONObject responseMessage = first == null ? null : first.getJSONObject("message");
            String content = responseMessage == null ? null : responseMessage.getString("content");
            if (!StringUtils.hasText(content)) {
                throw new JeecgBootException("Skill AI repair returned empty content.");
            }
            return parseRepairResult(draft, run, files, JSON.parseObject(extractJsonObject(content)), "ai");
        } catch (Exception e) {
            auditLogService.logFailure("skill_draft_ai_repair_model", "skill_draft", draft.getId(), Map.of("message", trim(e.getMessage(), 1000)));
            return null;
        }
    }

    private String buildRepairPrompt(OpenclawSkillDraft draft, OpenclawSkillTestRun run, Map<String, String> files, OpenclawSkillRepairDTO dto) {
        StringBuilder builder = new StringBuilder();
        builder.append("Draft: ").append(draft.getDraftName()).append(" / ").append(draft.getSkillSlug()).append('\n');
        builder.append("Status: ").append(draft.getStatus()).append('\n');
        builder.append("Natural-language edit instruction: ").append(safeText(dto == null ? null : dto.getInstruction())).append("\n\n");
        if (run != null) {
            builder.append("Test run status: ").append(run.getStatus()).append('\n');
            builder.append("Prompt:\n").append(safeText(run.getPrompt())).append("\n\n");
            builder.append("Expected output:\n").append(safeText(run.getExpectedOutput())).append("\n\n");
            builder.append("Output summary:\n").append(safeText(run.getOutputSummary())).append("\n\n");
            builder.append("Error message:\n").append(safeText(run.getErrorMessage())).append("\n\n");
        }
        builder.append("Files:\n");
        for (Map.Entry<String, String> entry : files.entrySet()) {
            builder.append("\n--- ").append(entry.getKey()).append(" ---\n");
            builder.append(trim(entry.getValue(), 12_000)).append('\n');
        }
        return builder.toString();
    }

    private OpenclawSkillRepairVO parseRepairResult(OpenclawSkillDraft draft, OpenclawSkillTestRun run, Map<String, String> currentFiles, JSONObject json, String source) {
        OpenclawSkillRepairVO result = new OpenclawSkillRepairVO();
        result.setDraftId(draft.getId());
        result.setTestRunId(run == null ? null : run.getId());
        result.setSource(source);
        result.setSummary(trim(firstText(json.getString("summary"), "Repair suggestions generated."), 4000));
        JSONArray warnings = json.getJSONArray("warnings");
        if (warnings != null) {
            for (Object item : warnings) {
                if (item != null) {
                    result.getWarnings().add(trim(String.valueOf(item), 1000));
                }
            }
        }
        JSONArray files = json.getJSONArray("files");
        if (files != null) {
            for (Object item : files) {
                if (item instanceof JSONObject file) {
                    addRepairSuggestion(result, currentFiles, file.getString("path"), file.getString("action"), file.getString("explanation"), file.getString("content"));
                }
            }
        }
        if (result.getFiles().isEmpty()) {
            result.getWarnings().add("The repair model did not return any applicable file changes.");
        }
        return result;
    }

    private OpenclawSkillRepairVO fallbackRepair(OpenclawSkillDraft draft, OpenclawSkillTestRun run, Map<String, String> files, OpenclawSkillRepairDTO dto) {
        OpenclawSkillRepairVO result = new OpenclawSkillRepairVO();
        result.setDraftId(draft.getId());
        result.setTestRunId(run == null ? null : run.getId());
        result.setSource("fallback");
        result.setSummary("AI edit model is not configured or unavailable. Generated safe notes and lint-oriented suggestions.");
        result.getWarnings().add("Review every suggested change before applying it, then rerun Lint and tests.");
        String skillMd = files.get("SKILL.md");
        if (StringUtils.hasText(skillMd)) {
            String improved = ensureSkillMdSections(skillMd, draft, run);
            if (!improved.equals(skillMd)) {
                addRepairSuggestion(result, files, "SKILL.md", "upsert", "Add missing standard Skill sections required by lint.", improved);
            }
        }
        String notes = repairNotesContent(draft, run, dto);
        addRepairSuggestion(result, files, "EDIT_NOTES.md", "upsert", "Record the natural-language change request and manual follow-up checks for this edit round.", notes);
        return result;
    }

    private String ensureSkillMdSections(String content, OpenclawSkillDraft draft, OpenclawSkillTestRun run) {
        String value = content;
        if (!value.contains("## Purpose")) {
            value += "\n\n## Purpose\n\n" + safeText(firstText(draft.getDescription(), "Describe the Skill purpose.")) + "\n";
        }
        if (!value.contains("## When to use")) {
            value += "\n\n## When to use\n\nUse this Skill when the task matches " + safeText(draft.getSkillSlug()) + ".\n";
        }
        if (!value.contains("## Inputs")) {
            value += "\n\n## Inputs\n\n- User request\n- Relevant workspace files or context\n";
        }
        if (!value.contains("## Outputs")) {
            value += "\n\n## Outputs\n\n- A response or artifact that satisfies the request\n- Notes about assumptions and follow-up checks\n";
        }
        if (!value.contains("## Examples")) {
            value += "\n\n## Examples\n\n" + safeText(run == null ? "Run a smoke test for this Skill." : run.getPrompt()) + "\n";
        }
        if (!value.contains("## Safety")) {
            value += "\n\n## Safety\n\nDo not run destructive commands or access files outside the workspace. Keep external calls explicit and auditable.\n";
        }
        return value;
    }

    private String repairNotesContent(OpenclawSkillDraft draft, OpenclawSkillTestRun run, OpenclawSkillRepairDTO dto) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Edit Notes\n\n");
        builder.append("- Draft: ").append(safeText(draft.getDraftName())).append(" (`").append(safeText(draft.getSkillSlug())).append("`)\n");
        builder.append("- Status: ").append(safeText(draft.getStatus())).append("\n");
        if (run != null) {
            builder.append("- Test run: ").append(run.getId()).append("\n");
            builder.append("- Test status: ").append(safeText(run.getStatus())).append("\n\n");
            builder.append("## Prompt\n\n").append(safeText(run.getPrompt())).append("\n\n");
            builder.append("## Output Summary\n\n").append(safeText(run.getOutputSummary())).append("\n\n");
            builder.append("## Error Message\n\n").append(safeText(run.getErrorMessage())).append("\n\n");
        }
        if (dto != null && StringUtils.hasText(dto.getInstruction())) {
            builder.append("## Natural-Language Instruction\n\n").append(safeText(dto.getInstruction())).append("\n\n");
        }
        builder.append("## Next Checks\n\n- Run Lint.\n- Rerun the failed test prompt.\n- Remove this note before publishing if it is not useful to reviewers.\n");
        return builder.toString();
    }

    private void addRepairSuggestion(OpenclawSkillRepairVO result, Map<String, String> currentFiles, String path, String action, String explanation, String content) {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.equals("..") || normalized.startsWith("../")) {
            result.getWarnings().add("Skipped unsafe repair path: " + path);
            return;
        }
        pathSafetyService.rejectBlockedExtension(normalized);
        OpenclawSkillRepairVO.FileSuggestion file = new OpenclawSkillRepairVO.FileSuggestion();
        file.setPath(normalized);
        file.setAction(StringUtils.hasText(action) ? action.trim().toLowerCase(Locale.ROOT) : "upsert");
        file.setExplanation(trim(explanation, 2000));
        file.setContent(content == null ? "" : trim(content, 200_000));
        file.setDiff(simpleDiff(currentFiles.get(normalized), file.getContent()));
        result.getFiles().add(file);
    }

    private OpenclawSkillAiEditVO toAiEditVO(OpenclawSkillAiEditRecord record, List<OpenclawSkillRepairVO.FileSuggestion> files, List<String> warnings) {
        OpenclawSkillAiEditVO vo = new OpenclawSkillAiEditVO();
        vo.setRecordId(record.getId());
        vo.setDraftId(record.getDraftId());
        vo.setTestRunId(record.getTestRunId());
        vo.setSummary(record.getSummary());
        vo.setBaseVersion(record.getBaseVersion());
        vo.setBaseHash(record.getBaseHash());
        vo.setStatus(record.getStatus());
        vo.setFiles(files == null ? new ArrayList<>() : files);
        vo.setWarnings(warnings == null ? new ArrayList<>() : warnings);
        return vo;
    }

    private OpenclawSkillAiEditRecord createSuggestionRecord(OpenclawSkillDraft draft, OpenclawSkillTestRun run, String instruction,
                                                             OpenclawSkillRepairVO repair, String baseVersion, String baseHash, String recordType) {
        OpenclawSkillAiEditRecord record = new OpenclawSkillAiEditRecord();
        record.setId(IdWorker.getIdStr());
        record.setDraftId(draft.getId());
        record.setSkillId(draft.getSkillId());
        record.setWorkspaceId(null); // TODO: persist workspaceId when Skill drafts are bound to workspaces.
        record.setUserId(permissionService.currentUser().getId());
        record.setRecordType(recordType);
        record.setTestRunId(run == null ? null : run.getId());
        record.setUserInstruction(trim(instruction, 8000));
        record.setSummary(repair.getSummary());
        record.setFilesJson(JSON.toJSONString(repair.getFiles()));
        record.setWarningsJson(JSON.toJSONString(repair.getWarnings()));
        record.setBaseVersion(baseVersion);
        record.setBaseHash(baseHash);
        record.setStatus("PREVIEW");
        if ("AI_REPAIR".equals(recordType)) {
            record.setRepairBeforeStatus(run == null ? null : run.getStatus());
        }
        aiEditRecordMapper.insert(record);
        return record;
    }

    private List<OpenclawSkillRepairVO.FileSuggestion> applySuggestionFiles(Path root, List<OpenclawSkillRepairVO.FileSuggestion> files) throws IOException {
        List<OpenclawSkillRepairVO.FileSuggestion> applied = new ArrayList<>();
        if (files == null) {
            return applied;
        }
        for (OpenclawSkillRepairVO.FileSuggestion file : files) {
            if (file == null || !StringUtils.hasText(file.getPath())) {
                continue;
            }
            String action = normalizeAiEditAction(file.getAction());
            Path target = pathSafetyService.resolve(root, file.getPath());
            if ("delete".equals(action)) {
                Files.deleteIfExists(target);
            } else {
                byte[] data = file.getContent().getBytes(StandardCharsets.UTF_8);
                pathSafetyService.validateWritableFile(root, file.getPath(), data.length);
                Files.createDirectories(target.getParent());
                if (Files.exists(target)) {
                    Files.write(target, data, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.write(target, data, StandardOpenOption.CREATE_NEW);
                }
            }
            OpenclawSkillRepairVO.FileSuggestion item = new OpenclawSkillRepairVO.FileSuggestion();
            item.setPath(file.getPath());
            item.setAction(action);
            item.setExplanation(file.getExplanation());
            applied.add(item);
        }
        return applied;
    }

    private void updateLatestAppliedRepairStatus(OpenclawSkillDraft draft, String status) {
        OpenclawSkillAiEditRecord record = aiEditRecordMapper.selectOne(new LambdaQueryWrapper<OpenclawSkillAiEditRecord>()
            .eq(OpenclawSkillAiEditRecord::getDraftId, draft.getId())
            .eq(OpenclawSkillAiEditRecord::getRecordType, "AI_REPAIR")
            .eq(OpenclawSkillAiEditRecord::getStatus, "APPLIED")
            .orderByDesc(OpenclawSkillAiEditRecord::getAppliedTime)
            .last("limit 1"));
        if (record == null) {
            return;
        }
        record.setRepairAfterStatus(status);
        aiEditRecordMapper.updateById(record);
    }

    private String draftVersion(OpenclawSkillDraft draft) {
        Date date = draft.getUpdateTime() == null ? draft.getCreateTime() : draft.getUpdateTime();
        return (date == null ? "draft" : String.valueOf(date.getTime())) + ":" + draft.getId();
    }

    private String normalizeAiEditAction(String action) {
        if (StringUtils.hasText(action) && "delete".equalsIgnoreCase(action.trim())) {
            return "delete";
        }
        return "upsert";
    }

    private String simpleDiff(String before, String after) {
        String previous = before == null ? "" : before;
        String next = after == null ? "" : after;
        if (previous.equals(next)) {
            return "No content changes.";
        }
        String[] oldLines = previous.split("\\R", -1);
        String[] newLines = next.split("\\R", -1);
        StringBuilder diff = new StringBuilder();
        diff.append("--- current\n+++ suggested\n");
        int max = Math.max(oldLines.length, newLines.length);
        int emitted = 0;
        for (int i = 0; i < max && emitted < 80; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;
            if (oldLine == null) {
                diff.append("+").append(newLine).append('\n');
                emitted++;
            } else if (newLine == null) {
                diff.append("-").append(oldLine).append('\n');
                emitted++;
            } else if (!oldLine.equals(newLine)) {
                diff.append("-").append(oldLine).append('\n');
                diff.append("+").append(newLine).append('\n');
                emitted += 2;
            }
        }
        if (max > 80) {
            diff.append("... diff truncated ...\n");
        }
        return diff.toString();
    }

    private TestAgentContext prepareTestAgent(OpenclawSkillDraft draft, OpenclawSkillTestRun run) throws IOException {
        OpenclawWorkspace workspace = ensureTestWorkspace(draft);
        OpenclawGatewayNode gateway = requireOnlineGatewayForDraftTest();
        OpenclawAgent agent = buildDraftTestAgent(draft, workspace, gateway, run);
        workspaceMaterializer.materialize(agent, workspace);
        Path skillPath = materializeDraftSkill(draft, workspace, run);
        log.info("skill draft test prepared agentKey={} draftId={} testRunId={} workspaceId={} workspacePath={} skillPath={} gatewayId={}",
            agent.getAgentKey(), draft.getId(), run.getId(), workspace.getId(), workspace.getPath(), skillPath, gateway.getId());
        TestAgentContext context = new TestAgentContext();
        context.workspace = workspace;
        context.agent = agent;
        context.gateway = gateway;
        return context;
    }

    private OpenclawWorkspace ensureTestWorkspace(OpenclawSkillDraft draft) {
        String workspaceKey = "skill-draft-test-" + draft.getId();
        OpenclawWorkspace workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<OpenclawWorkspace>()
            .eq(OpenclawWorkspace::getWorkspaceKey, workspaceKey)
            .last("limit 1"));
        Path workspacePath = Paths.get(TEST_WORKSPACE_ROOT, draft.getId()).toAbsolutePath().normalize();
        Path root = Paths.get(OpenclawConstants.WORKSPACE_ROOT).toAbsolutePath().normalize();
        if (!workspacePath.startsWith(root)) {
            throw new JeecgBootException("Invalid test workspace path.");
        }
        if (workspace == null) {
            workspace = new OpenclawWorkspace();
            workspace.setUserId(draft.getOwnerUserId());
            workspace.setUsername(draft.getOwnerUsername());
            workspace.setName("Skill Draft Test " + draft.getSkillSlug());
            workspace.setWorkspaceKey(workspaceKey);
            workspace.setQuotaSizeMb(512);
            workspace.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        }
        workspace.setPath(workspacePath.toString());
        workspace.setStatus(OpenclawConstants.WORKSPACE_STATUS_READY);
        workspace.setRemark("Isolated Skill Draft test workspace for " + draft.getId());
        workspaceMapper.insertOrUpdate(workspace);
        return workspace;
    }

    private OpenclawAgent buildDraftTestAgent(OpenclawSkillDraft draft, OpenclawWorkspace workspace, OpenclawGatewayNode gateway, OpenclawSkillTestRun run) {
        String agentKey = "skill_draft_test_" + draft.getId() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        OpenclawAgent agent = new OpenclawAgent();
        agent.setId(IdWorker.getIdStr());
        agent.setUserId(draft.getOwnerUserId());
        agent.setUsername(draft.getOwnerUsername());
        agent.setAgentKey(agentKey);
        agent.setMaxSkills(1);
        agent.setMaxDailyRuns(1000);
        agent.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        agent.setWorkspaceId(workspace.getId());
        agent.setName("Skill Draft Test Agent " + draft.getSkillSlug());
        agent.setDescription("Temporary test Agent for Skill Draft " + draft.getId() + ", testRunId=" + run.getId());
        agent.setStatus(OpenclawConstants.AGENT_STATUS_ENABLED);
        agent.setGatewayId(gateway.getId());
        agent.setConfigJson(null);
        agent.setRemark("Temporary Skill Draft test Agent; not persisted to formal Agent table.");
        return agent;
    }

    private Path materializeDraftSkill(OpenclawSkillDraft draft, OpenclawWorkspace workspace, OpenclawSkillTestRun run) throws IOException {
        Path skillsRoot = workspaceMaterializer.safeWorkspacePath(workspace.getPath()).resolve("skills").normalize();
        Path target = skillsRoot.resolve(draft.getSkillSlug()).normalize();
        if (!target.startsWith(skillsRoot)) {
            throw new JeecgBootException("Invalid draft Skill slug path.");
        }
        Path temp = skillsRoot.resolve("." + draft.getSkillSlug() + ".tmp-" + run.getId()).normalize();
        Path backup = skillsRoot.resolve("." + draft.getSkillSlug() + ".bak-" + System.currentTimeMillis()).normalize();
        try {
            Files.createDirectories(skillsRoot);
            cleanupQuietly(temp);
            copyDirectory(draftRoot(draft), temp);
            writeSkillManifest(temp, draft.getDraftName(), draft.getSkillSlug(), "draft-test-" + run.getId(), draft.getDescription(), draft.getOwnerUsername());
            if (Files.exists(target)) {
                Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveError) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            cleanupQuietly(backup);
            return target;
        } catch (IOException e) {
            if (Files.exists(backup)) {
                cleanupQuietly(target);
                Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            }
            cleanupQuietly(temp);
            throw e;
        }
    }

    private OpenclawGatewayNode requireOnlineGatewayForDraftTest() {
        OpenclawGatewayNode gateway = gatewayNodeMapper.selectOne(new LambdaQueryWrapper<OpenclawGatewayNode>()
            .eq(OpenclawGatewayNode::getStatus, OpenclawConstants.GATEWAY_STATUS_ONLINE)
            .eq(OpenclawGatewayNode::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1"));
        if (gateway == null) {
            throw new JeecgBootException("No online OpenClaw Gateway node is available for Skill Draft test.");
        }
        return gateway;
    }

    private void registerDraftAgent(TestAgentContext context, OpenclawSkillDraft draft, OpenclawSkillTestRun run, LoginUser user) throws IOException {
        JSONObject entry = new JSONObject(true);
        long now = System.currentTimeMillis();
        long ttlMillis = Math.max(60L, Math.min(draftAgentTtlSeconds == null ? 600L : draftAgentTtlSeconds, 3600L)) * 1000L;
        entry.put("id", context.agent.getAgentKey());
        entry.put("draftId", draft.getId());
        entry.put("workspaceId", context.workspace.getId());
        entry.put("workspace", context.workspace.getPath());
        entry.put("userId", user.getId());
        entry.put("username", user.getUsername());
        entry.put("skillSlug", draft.getSkillSlug());
        entry.put("skills", List.of(draft.getSkillSlug()));
        entry.put("testRunId", run.getId());
        entry.put("createdAt", now);
        entry.put("expiresAt", now + ttlMillis);
        if (StringUtils.hasText(draftTestModelOverride)) {
            JSONObject model = new JSONObject(true);
            model.put("primary", draftTestModelOverride.trim());
            entry.put("model", model);
        }
        JSONObject identity = new JSONObject(true);
        identity.put("name", context.agent.getName());
        entry.put("identity", identity);

        synchronized (DRAFT_AGENT_REGISTRY_LOCK) {
            JSONObject registry = readDraftAgentRegistry();
            JSONArray agents = compactDraftAgentRegistry(registry.getJSONArray("agents"), now);
            for (int i = agents.size() - 1; i >= 0; i--) {
                JSONObject item = agents.getJSONObject(i);
                if (context.agent.getAgentKey().equals(item.getString("id"))) {
                    agents.remove(i);
                }
            }
            agents.add(entry);
            registry.put("version", 1);
            registry.put("agents", agents);
            writeDraftAgentRegistry(registry);
        }
        log.info("skill draft agent registered agentKey={} draftId={} testRunId={} workspaceId={} registryPath={} workspacePath={} skillPath={} expiresAt={}",
            context.agent.getAgentKey(), draft.getId(), run.getId(), context.workspace.getId(), draftAgentRegistryFile(),
            context.workspace.getPath(), Paths.get(context.workspace.getPath()).resolve("skills").resolve(draft.getSkillSlug()).normalize(), now + ttlMillis);
    }

    private void cleanupDraftAgent(String agentKey) throws IOException {
        if (!StringUtils.hasText(agentKey)) {
            return;
        }
        synchronized (DRAFT_AGENT_REGISTRY_LOCK) {
            long now = System.currentTimeMillis();
            JSONObject registry = readDraftAgentRegistry();
            JSONArray source = compactDraftAgentRegistry(registry.getJSONArray("agents"), now);
            JSONArray agents = new JSONArray();
            for (int i = 0; i < source.size(); i++) {
                JSONObject item = source.getJSONObject(i);
                if (!agentKey.equals(item.getString("id"))) {
                    agents.add(item);
                }
            }
            registry.put("version", 1);
            registry.put("agents", agents);
            writeDraftAgentRegistry(registry);
        }
    }

    private JSONObject readDraftAgentRegistry() throws IOException {
        Path path = draftAgentRegistryFile();
        if (!Files.isRegularFile(path)) {
            JSONObject empty = new JSONObject(true);
            empty.put("version", 1);
            empty.put("agents", new JSONArray());
            return empty;
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        JSONObject registry = JSON.parseObject(content);
        if (registry == null) {
            registry = new JSONObject(true);
        }
        if (registry.getJSONArray("agents") == null) {
            registry.put("agents", new JSONArray());
        }
        return registry;
    }

    private JSONArray compactDraftAgentRegistry(JSONArray source, long now) {
        JSONArray agents = new JSONArray();
        if (source == null) {
            return agents;
        }
        for (int i = 0; i < source.size(); i++) {
            JSONObject item = source.getJSONObject(i);
            if (item == null || !StringUtils.hasText(item.getString("id"))) {
                continue;
            }
            Long expiresAt = item.getLong("expiresAt");
            if (expiresAt != null && expiresAt <= now) {
                log.info("skill draft agent registry ttl expired agentKey={} expiresAt={} now={} registryPath={}",
                    item.getString("id"), expiresAt, now, draftAgentRegistryFile());
                continue;
            }
            agents.add(item);
        }
        return agents;
    }

    private void writeDraftAgentRegistry(JSONObject registry) throws IOException {
        Path path = draftAgentRegistryFile();
        Path parent = path.getParent();
        if (parent == null) {
            throw new JeecgBootException("Draft Agent registry path must include parent directory.");
        }
        Files.createDirectories(parent);
        workspaceMaterializer.ensureNoSymbolicLink(parent);
        Path temp = parent.resolve(path.getFileName().toString() + ".tmp-" + IdWorker.getIdStr()).normalize();
        Files.writeString(temp, JSON.toJSONString(registry), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicMoveError) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path draftAgentRegistryFile() {
        if (!StringUtils.hasText(draftAgentRegistryPath)) {
            throw new JeecgBootException("Draft Agent registry path is empty.");
        }
        return Paths.get(draftAgentRegistryPath).toAbsolutePath().normalize();
    }

    private Map<String, Object> draftAgentAuditDetail(TestAgentContext context, OpenclawSkillDraft draft, OpenclawSkillTestRun run, LoginUser user) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("testRunId", run.getId());
        detail.put("draftId", draft.getId());
        detail.put("agentId", context.agent.getAgentKey());
        detail.put("workspaceId", context.workspace.getId());
        detail.put("userId", user.getId());
        return detail;
    }

    private void writeSkillManifest(Path root, String name, String slug, String version, String description, String owner) throws IOException {
        JSONObject manifest = new JSONObject(true);
        manifest.put("name", name);
        manifest.put("slug", slug);
        manifest.put("version", version);
        manifest.put("description", description);
        manifest.put("owner", owner);
        manifest.put("generatedAt", LocalDate.now().toString());
        manifest.put("entry", "SKILL.md");

        JSONArray files = new JSONArray();
        try (var walk = Files.walk(root)) {
            List<Path> regularFiles = walk
                .filter(Files::isRegularFile)
                .filter(path -> !"manifest.json".equals(path.getFileName().toString()))
                .sorted()
                .toList();
            for (Path file : regularFiles) {
                JSONObject item = new JSONObject(true);
                item.put("path", root.relativize(file).toString().replace('\\', '/'));
                item.put("size", Files.size(file));
                item.put("checksum", sha256File(file));
                files.add(item);
            }
        }
        manifest.put("files", files);
        Files.writeString(root.resolve("manifest.json"), manifest.toJSONString() + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void finishTestRun(OpenclawSkillTestRun run, Date start, String status, String output, String error, String workspacePath, OpenclawAgentRunResultVO agentRun) {
        Date finish = new Date();
        run.setStatus(status);
        run.setOutputSummary(trim(output, 4000));
        run.setOutputJson(testOutputJson(output, agentRun));
        run.setErrorType(agentRun == null ? inferTestErrorType(status, error) : agentRun.getErrorType());
        run.setErrorCode(errorCode(run.getErrorType(), status));
        run.setErrorMessage(trim(error, 4000));
        if (agentRun != null) {
            run.setAgentKey(agentRun.getAgentKey());
            run.setAgentRunId(agentRun.getRunId());
            run.setGatewayStatus(OpenclawConstants.RUN_STATUS_SUCCESS.equals(status) ? "OK" : "ERROR");
            run.setLogsJson(testLogsJson(agentRun));
        } else if (!StringUtils.hasText(run.getGatewayStatus()) || "PENDING".equals(run.getGatewayStatus())) {
            run.setGatewayStatus("NOT_STARTED");
        }
        run.setWorkspacePath(workspacePath);
        run.setFinishTime(finish);
        run.setDurationMs(finish.getTime() - start.getTime());
        run.setReportJson(JSON.toJSONString(toTestReport(run)));
        testRunService.updateById(run);
    }

    private OpenclawSkillTestReportVO toTestReport(OpenclawSkillTestRun run) {
        OpenclawSkillTestReportVO report = new OpenclawSkillTestReportVO();
        report.setTestRunId(run.getId());
        report.setDraftId(run.getDraftId());
        report.setAgentKey(run.getAgentKey());
        report.setDraftVersionNo(run.getDraftVersionNo());
        report.setFileHash(run.getFileHash());
        report.setStatus(standardTestStatus(run.getStatus()));
        report.setLintStatus(run.getLintStatus());
        report.setGatewayStatus(run.getGatewayStatus());
        report.setInput(firstText(run.getInputJson(), run.getPrompt()));
        report.setOutput(firstText(run.getOutputJson(), run.getOutputSummary()));
        OpenclawSkillTestReportVO.Error error = new OpenclawSkillTestReportVO.Error();
        error.setType(run.getErrorType());
        error.setMessage(run.getErrorMessage());
        error.setCode(run.getErrorCode());
        report.setError(error);
        report.setLogs(parseStringList(run.getLogsJson()));
        report.setStartedAt(run.getStartTime());
        report.setFinishedAt(run.getFinishTime());
        report.setDurationMs(run.getDurationMs());
        return report;
    }

    private String testInputJson(OpenclawSkillDraftTestDTO dto) {
        JSONObject input = new JSONObject(true);
        input.put("prompt", trim(dto == null ? null : dto.getPrompt(), 8000));
        input.put("expectedOutput", trim(dto == null ? null : dto.getExpectedOutput(), 4000));
        input.put("localExecution", dto != null && Boolean.TRUE.equals(dto.getLocalExecution()));
        return input.toJSONString();
    }

    private String testOutputJson(String output, OpenclawAgentRunResultVO agentRun) {
        JSONObject value = new JSONObject(true);
        value.put("summary", trim(output, 4000));
        if (agentRun != null) {
            value.put("agentRunId", agentRun.getRunId());
            value.put("fullOutputPath", agentRun.getFullOutputPath());
            value.put("logPath", agentRun.getLogPath());
            value.put("model", agentRun.getModel());
        }
        return value.toJSONString();
    }

    private String testLogsJson(OpenclawAgentRunResultVO agentRun) {
        JSONArray logs = new JSONArray();
        if (agentRun == null) {
            return logs.toJSONString();
        }
        if (StringUtils.hasText(agentRun.getLogPath())) {
            logs.add("logPath=" + agentRun.getLogPath());
        }
        if (StringUtils.hasText(agentRun.getFullOutputPath())) {
            logs.add("fullOutputPath=" + agentRun.getFullOutputPath());
        }
        if (StringUtils.hasText(agentRun.getErrorType())) {
            logs.add("errorType=" + agentRun.getErrorType());
        }
        return logs.toJSONString();
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> values = JSON.parseArray(json, String.class);
            return values == null ? new ArrayList<>() : values;
        } catch (Exception e) {
            return List.of(trim(json, 1000));
        }
    }

    private String standardTestStatus(String status) {
        return OpenclawConstants.RUN_STATUS_SUCCESS.equals(status) ? "PASSED" : "FAILED";
    }

    private String inferTestErrorType(String status, String error) {
        if (OpenclawConstants.RUN_STATUS_SUCCESS.equals(status)) {
            return null;
        }
        if (StringUtils.hasText(error) && error.contains("lint_failed")) {
            return "lint_failed";
        }
        return OpenclawConstants.RUN_ERROR_UNKNOWN;
    }

    private String errorCode(String errorType, String status) {
        if (OpenclawConstants.RUN_STATUS_SUCCESS.equals(status)) {
            return null;
        }
        return StringUtils.hasText(errorType) ? errorType.toUpperCase(Locale.ROOT) : "TEST_FAILED";
    }

    private void cleanupQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> deleteQuietly(item, path));
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private void deleteQuietly(Path item, Path root) {
        pathSafetyService.rejectIfOutsideRoot(root, item);
        try {
            Files.deleteIfExists(item);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private String normalizeSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        if (!StringUtils.hasText(slug)) {
            throw new JeecgBootException("Skill slug is invalid.");
        }
        if (slug.length() > 100) {
            throw new JeecgBootException("Skill slug is too long.");
        }
        return slug;
    }

    private String toRelative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String sha256Text(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new JeecgBootException("Current JDK does not support SHA-256.", e);
        }
    }

    private String sha256Directory(Path root) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var walk = Files.walk(root)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    digest.update(sha256File(file).getBytes(StandardCharsets.UTF_8));
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new JeecgBootException("Current JDK does not support SHA-256.", e);
        }
    }

    private long directorySize(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    throw new JeecgBootException("Read file size failed: " + e.getMessage(), e);
                }
            }).sum();
        }
    }

    private String sha256File(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file);
                 DigestInputStream dis = new DigestInputStream(input, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // consume stream
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new JeecgBootException("Current JDK does not support SHA-256.", e);
        }
    }

    private static class TestAgentContext {
        private OpenclawWorkspace workspace;
        private OpenclawAgent agent;
        private OpenclawGatewayNode gateway;
    }

    private static class GeneratedSkillSpec {
        private String draftName;
        private String skillSlug;
        private String description;
        private Map<String, String> files;
    }
}
