package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftCreateDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftFileDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillDraftTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraftFile;
import org.jeecg.modules.openclaw.entity.OpenclawSkillTestRun;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftFileMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillDraftService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillTestRunService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileContentVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftFileNodeVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftLintVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class OpenclawSkillDraftServiceImpl extends ServiceImpl<OpenclawSkillDraftMapper, OpenclawSkillDraft> implements IOpenclawSkillDraftService {
    private static final String DRAFT_ROOT = "/data/openclaw-platform/skill-drafts";
    private static final String TEST_WORKSPACE_ROOT = "/data/openclaw-platform/skill-test-workspaces";
    private static final Set<String> EDITABLE_STATUSES = Set.of("editing", "lint_failed", "lint_passed", "test_failed", "rejected");
    private static final Set<String> DANGEROUS_CODE_KEYWORDS = Set.of(
        "os.system", "subprocess", "rm -rf", "curl ", "wget ", "chmod", "sudo", "eval(", "exec(", "socket", "requests", "open('/etc"
    );

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
            auditLogService.log("skill_draft_file_delete", "skill_draft", draft.getId(), Map.of("path", path));
        } catch (IOException e) {
            throw new JeecgBootException("Delete draft file failed: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillDraftLintVO lint(String draftId) {
        OpenclawSkillDraft draft = requireDraft(draftId, false);
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
        LoginUser user = permissionService.currentUser();
        Date start = new Date();
        OpenclawSkillTestRun run = new OpenclawSkillTestRun();
        run.setDraftId(draft.getId());
        run.setSkillSlug(draft.getSkillSlug());
        run.setUserId(user.getId());
        run.setUsername(user.getUsername());
        run.setStatus(OpenclawConstants.RUN_STATUS_RUNNING);
        run.setPrompt(trim(dto.getPrompt(), 8000));
        run.setExpectedOutput(trim(dto.getExpectedOutput(), 4000));
        run.setStartTime(start);
        run.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        testRunService.save(run);
        auditLogService.log("skill_draft_test_start", "skill_test_run", run.getId(), run);

        try {
            OpenclawSkillDraftLintVO lint = lint(draftId);
            if (!Boolean.TRUE.equals(lint.getPassed())) {
                finishTestRun(run, start, OpenclawConstants.RUN_STATUS_FAILED, "Lint failed before test run.", JSON.toJSONString(lint), null);
                draft.setLastTestStatus(OpenclawConstants.RUN_STATUS_FAILED);
                draft.setLastTestRunId(run.getId());
                updateById(draft);
                auditLogService.logFailure("skill_draft_test_failed", "skill_test_run", run.getId(), run);
                return run;
            }

            Path workspace = materializeTestWorkspace(draft);
            String output = "Test workspace prepared at " + workspace
                + ". Prompt saved for manual OpenClaw execution. Full Agent Run integration is the next phase.";
            finishTestRun(run, start, OpenclawConstants.RUN_STATUS_SUCCESS, output, null, workspace.toString());
            draft.setStatus("test_passed");
            draft.setLastTestStatus(OpenclawConstants.RUN_STATUS_SUCCESS);
            draft.setLastTestRunId(run.getId());
            updateById(draft);
            auditLogService.logSuccess("skill_draft_test_success", "skill_test_run", run.getId(), run);
            return run;
        } catch (Exception e) {
            finishTestRun(run, start, OpenclawConstants.RUN_STATUS_FAILED, null, e.getMessage(), null);
            draft.setStatus("test_failed");
            draft.setLastTestStatus(OpenclawConstants.RUN_STATUS_FAILED);
            draft.setLastTestRunId(run.getId());
            updateById(draft);
            auditLogService.logFailure("skill_draft_test_failed", "skill_test_run", run.getId(), run);
            return run;
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

    private OpenclawSkillDraft requireDraft(String draftId, boolean editable) {
        OpenclawSkillDraft draft = getById(draftId);
        if (draft == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(draft.getDelFlag())) {
            throw new JeecgBootException("Skill draft does not exist.");
        }
        permissionService.checkOwnerOrAdmin(draft.getOwnerUserId());
        if (editable && !EDITABLE_STATUSES.contains(draft.getStatus())) {
            throw new JeecgBootException("Current draft status does not allow editing.");
        }
        return draft;
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

    private Path materializeTestWorkspace(OpenclawSkillDraft draft) throws IOException {
        Path workspaceRoot = Paths.get(TEST_WORKSPACE_ROOT, "skill-draft-" + draft.getId()).toAbsolutePath().normalize();
        Path root = Paths.get(TEST_WORKSPACE_ROOT).toAbsolutePath().normalize();
        if (!workspaceRoot.startsWith(root)) {
            throw new JeecgBootException("Invalid test workspace path.");
        }
        cleanupQuietly(workspaceRoot);
        Files.createDirectories(workspaceRoot.resolve("skills"));
        Path skillTarget = workspaceRoot.resolve("skills").resolve(draft.getSkillSlug()).normalize();
        pathSafetyService.rejectIfOutsideRoot(workspaceRoot, skillTarget);
        copyDirectory(draftRoot(draft), skillTarget);
        Files.writeString(workspaceRoot.resolve("AGENTS.md"),
            "# Skill Draft Test Agent\n\nUse the Skill under `skills/" + draft.getSkillSlug() + "` for this isolated test workspace.\n",
            StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(workspaceRoot.resolve("USER.md"), "Skill draft owner: " + draft.getOwnerUsername() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(workspaceRoot.resolve("IDENTITY.md"), "Temporary Skill Draft test workspace.\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return workspaceRoot;
    }

    private void finishTestRun(OpenclawSkillTestRun run, Date start, String status, String output, String error, String workspacePath) {
        Date finish = new Date();
        run.setStatus(status);
        run.setOutputSummary(trim(output, 4000));
        run.setErrorMessage(trim(error, 4000));
        run.setWorkspacePath(workspacePath);
        run.setFinishTime(finish);
        run.setDurationMs(finish.getTime() - start.getTime());
        testRunService.updateById(run);
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
}
