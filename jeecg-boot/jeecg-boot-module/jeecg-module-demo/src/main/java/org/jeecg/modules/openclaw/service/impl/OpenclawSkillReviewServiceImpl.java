package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawSkillReviewActionDTO;
import org.jeecg.modules.openclaw.dto.OpenclawSkillReviewSubmitDTO;
import org.jeecg.modules.openclaw.entity.OpenclawPublishedSkillVersion;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawSkillAiEditRecord;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraft;
import org.jeecg.modules.openclaw.entity.OpenclawSkillDraftVersion;
import org.jeecg.modules.openclaw.entity.OpenclawSkillReview;
import org.jeecg.modules.openclaw.entity.OpenclawSkillTestRun;
import org.jeecg.modules.openclaw.mapper.OpenclawPublishedSkillVersionMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillAiEditRecordMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillDraftVersionMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillReviewMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillDraftService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillReviewService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillTestRunService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillDraftVersionVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillReviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OpenclawSkillReviewServiceImpl extends ServiceImpl<OpenclawSkillReviewMapper, OpenclawSkillReview> implements IOpenclawSkillReviewService {
    private static final Set<String> OPEN_REVIEW_STATUSES = Set.of("SUBMITTED");
    private static final Set<String> TERMINAL_REVIEW_STATUSES = Set.of("APPROVED", "REJECTED", "CANCELLED");

    @Autowired
    private IOpenclawSkillDraftService draftService;
    @Autowired
    private IOpenclawSkillService skillService;
    @Autowired
    private IOpenclawSkillTestRunService testRunService;
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;
    @Autowired
    private OpenclawSkillDraftVersionMapper draftVersionMapper;
    @Autowired
    private OpenclawSkillAiEditRecordMapper aiEditRecordMapper;
    @Autowired
    private OpenclawPublishedSkillVersionMapper publishedVersionMapper;
    @Autowired
    private OpenclawPathSafetyService pathSafetyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillReview submitReview(String draftId, Integer versionNo, OpenclawSkillReviewSubmitDTO dto) {
        OpenclawSkillDraft draft = requireDraftForOwner(draftId);
        OpenclawSkillDraftVersion version = requireVersion(draft, versionNo);
        if (!"lint_passed".equals(version.getLintStatus())) {
            throw new JeecgBootException("Only lint passed versions can be submitted for review.");
        }
        if (!OpenclawConstants.RUN_STATUS_SUCCESS.equals(version.getTestStatus())) {
            throw new JeecgBootException("Only test passed versions can be submitted for review.");
        }
        OpenclawSkillTestRun run = requireBoundSuccessfulTest(draft, version);
        Long openCount = count(new LambdaQueryWrapper<OpenclawSkillReview>()
            .eq(OpenclawSkillReview::getDraftId, draft.getId())
            .eq(OpenclawSkillReview::getVersionNo, version.getVersionNo())
            .in(OpenclawSkillReview::getStatus, OPEN_REVIEW_STATUSES)
            .eq(OpenclawSkillReview::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL));
        if (openCount != null && openCount > 0) {
            throw new JeecgBootException("This draft version already has an unfinished review.");
        }

        LoginUser submitter = permissionService.currentUser();
        OpenclawSkillReview review = new OpenclawSkillReview();
        review.setId(IdWorker.getIdStr());
        review.setDraftId(draft.getId());
        review.setVersionNo(version.getVersionNo());
        review.setSkillId(draft.getSkillId());
        review.setWorkspaceId(null); // TODO: persist workspaceId when Skill drafts are bound to workspaces.
        review.setSubmitterId(submitter.getId());
        review.setSubmitterUsername(submitter.getUsername());
        review.setStatus("SUBMITTED");
        review.setFileSnapshotJson(version.getFileSnapshot());
        review.setFileHash(version.getFileHash());
        review.setTestRunId(run.getId());
        review.setTestReportJson(JSON.toJSONString(testReportMap(run)));
        review.setAiRecordIdsJson(JSON.toJSONString(collectAiRecordIds(draft, version)));
        review.setSubmitComment(trim(dto == null ? null : dto.getSubmitComment(), 1000));
        review.setSubmittedTime(new Date());
        review.setCreateBy(submitter.getUsername());
        review.setCreateTime(new Date());
        review.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(review);
        auditLogService.logSuccess("skill_review_submit", "skill_review", review.getId(), Map.of(
            "draftId", draft.getId(),
            "versionNo", version.getVersionNo(),
            "testRunId", run.getId()
        ));
        return review;
    }

    @Override
    public OpenclawSkillReviewVO detail(String reviewId) {
        OpenclawSkillReview review = requireReview(reviewId);
        OpenclawSkillDraft draft = draftService.getById(review.getDraftId());
        if (draft == null) {
            throw new JeecgBootException("Review draft does not exist.");
        }
        LoginUser user = permissionService.currentUser();
        if (!permissionService.isSkillReviewer(user)) {
            permissionService.checkOwnerOrAdmin(draft.getOwnerUserId());
        }

        OpenclawSkillReviewVO vo = new OpenclawSkillReviewVO();
        vo.setReview(review);
        vo.setVersion(draftVersionMapper.selectOne(new LambdaQueryWrapper<OpenclawSkillDraftVersion>()
            .eq(OpenclawSkillDraftVersion::getDraftId, review.getDraftId())
            .eq(OpenclawSkillDraftVersion::getVersionNo, review.getVersionNo())
            .eq(OpenclawSkillDraftVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1")));
        vo.setFiles(parseSnapshot(review.getFileSnapshotJson()));
        vo.setTestReport(StringUtils.hasText(review.getTestReportJson()) ? JSON.parse(review.getTestReportJson()) : null);
        vo.setAiRecords(loadAiRecords(review.getAiRecordIdsJson()));
        OpenclawPublishedSkillVersion previous = latestPublishedVersion(review.getSkillId());
        if (previous != null) {
            vo.setPublishedVersion(previous);
            vo.setPublishedDiffs(fileDiffs(parseSnapshot(previous.getFileSnapshotJson()), parseSnapshot(review.getFileSnapshotJson())));
        }
        OpenclawPublishedSkillVersion published = publishedVersionMapper.selectOne(new LambdaQueryWrapper<OpenclawPublishedSkillVersion>()
            .eq(OpenclawPublishedSkillVersion::getReviewId, review.getId())
            .eq(OpenclawPublishedSkillVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .last("limit 1"));
        if (published != null) {
            vo.setPublishedVersion(published);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillReview approve(String reviewId, OpenclawSkillReviewActionDTO dto) {
        OpenclawSkillReview review = requireActionableReview(reviewId);
        LoginUser reviewer = requireReviewer();
        PublishedResult published = publishReviewSnapshot(review, reviewer);
        review.setStatus("APPROVED");
        review.setReviewerId(reviewer.getId());
        review.setReviewerUsername(reviewer.getUsername());
        review.setReviewComment(trim(dto == null ? null : dto.getComment(), 1000));
        review.setReviewedTime(new Date());
        review.setSkillId(published.skill.getId());
        review.setPublishedSkillId(published.skill.getId());
        review.setPublishedVersionNo(published.version.getPublishedVersionNo());
        review.setUpdateBy(reviewer.getUsername());
        review.setUpdateTime(new Date());
        updateById(review);
        auditLogService.logSuccess("skill_review_approve_publish", "skill_review", review.getId(), Map.of(
            "publishedSkillId", published.skill.getId(),
            "publishedVersionNo", published.version.getPublishedVersionNo()
        ));
        return review;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillReview reject(String reviewId, OpenclawSkillReviewActionDTO dto) {
        OpenclawSkillReview review = requireActionableReview(reviewId);
        LoginUser reviewer = requireReviewer();
        review.setStatus("REJECTED");
        review.setReviewerId(reviewer.getId());
        review.setReviewerUsername(reviewer.getUsername());
        review.setReviewComment(trim(dto == null ? null : dto.getComment(), 1000));
        review.setReviewedTime(new Date());
        review.setUpdateBy(reviewer.getUsername());
        review.setUpdateTime(new Date());
        updateById(review);
        auditLogService.logFailure("skill_review_reject", "skill_review", review.getId(), review);
        return review;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillReview cancel(String reviewId, OpenclawSkillReviewActionDTO dto) {
        OpenclawSkillReview review = requireActionableReview(reviewId);
        LoginUser user = permissionService.currentUser();
        if (!permissionService.isAdmin(user) && !user.getId().equals(review.getSubmitterId())) {
            throw new JeecgBootException("Only the submitter or an administrator can cancel this review.");
        }
        review.setStatus("CANCELLED");
        review.setReviewComment(trim(dto == null ? null : dto.getComment(), 1000));
        review.setReviewedTime(new Date());
        review.setUpdateBy(user.getUsername());
        review.setUpdateTime(new Date());
        updateById(review);
        auditLogService.logSuccess("skill_review_cancel", "skill_review", review.getId(), review);
        return review;
    }

    private OpenclawSkillDraft requireDraftForOwner(String draftId) {
        OpenclawSkillDraft draft = draftService.getById(draftId);
        if (draft == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(draft.getDelFlag())) {
            throw new JeecgBootException("Skill draft does not exist.");
        }
        permissionService.checkOwnerOrAdmin(draft.getOwnerUserId());
        return draft;
    }

    private OpenclawSkillDraftVersion requireVersion(OpenclawSkillDraft draft, Integer versionNo) {
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

    private OpenclawSkillTestRun requireBoundSuccessfulTest(OpenclawSkillDraft draft, OpenclawSkillDraftVersion version) {
        if (!StringUtils.hasText(version.getTestRunId())) {
            throw new JeecgBootException("Submitted version must be bound to a successful Test Report.");
        }
        OpenclawSkillTestRun run = testRunService.getById(version.getTestRunId());
        if (run == null || !draft.getId().equals(run.getDraftId()) || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(run.getDelFlag())) {
            throw new JeecgBootException("Bound Test Report does not exist.");
        }
        if (!OpenclawConstants.RUN_STATUS_SUCCESS.equals(run.getStatus())) {
            throw new JeecgBootException("Bound Test Report is not successful.");
        }
        if (!version.getVersionNo().equals(run.getDraftVersionNo()) || !version.getFileHash().equals(run.getFileHash())) {
            throw new JeecgBootException("Test Report file hash does not match the submitted version.");
        }
        return run;
    }

    private OpenclawSkillReview requireReview(String reviewId) {
        if (!StringUtils.hasText(reviewId)) {
            throw new JeecgBootException("Review id is required.");
        }
        OpenclawSkillReview review = getById(reviewId);
        if (review == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(review.getDelFlag())) {
            throw new JeecgBootException("Skill review does not exist.");
        }
        return review;
    }

    private OpenclawSkillReview requireActionableReview(String reviewId) {
        OpenclawSkillReview review = requireReview(reviewId);
        if (TERMINAL_REVIEW_STATUSES.contains(review.getStatus())) {
            throw new JeecgBootException("Skill review is already finished.");
        }
        if (!"SUBMITTED".equals(review.getStatus())) {
            throw new JeecgBootException("Only submitted reviews can be operated.");
        }
        return review;
    }

    private LoginUser requireReviewer() {
        LoginUser reviewer = permissionService.currentUser();
        if (!permissionService.isSkillReviewer(reviewer)) {
            throw new JeecgBootException("Only OpenClaw Skill reviewers can operate reviews.");
        }
        return reviewer;
    }

    private PublishedResult publishReviewSnapshot(OpenclawSkillReview review, LoginUser reviewer) {
        OpenclawSkillDraft draft = draftService.getById(review.getDraftId());
        if (draft == null) {
            throw new JeecgBootException("Review draft does not exist.");
        }
        Map<String, String> files = parseSnapshot(review.getFileSnapshotJson());
        if (files.isEmpty()) {
            throw new JeecgBootException("Review snapshot is empty.");
        }
        OpenclawSkill skill = StringUtils.hasText(review.getSkillId()) ? skillService.getById(review.getSkillId()) : null;
        boolean createSkill = skill == null;
        if (skill == null) {
            skill = new OpenclawSkill();
            skill.setId(IdWorker.getIdStr());
            skill.setOwnerUserId(draft.getOwnerUserId());
            skill.setOwnerUsername(draft.getOwnerUsername());
            skill.setSlug(draft.getSkillSlug());
            skill.setScope("public");
            skill.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        }
        Integer publishedVersionNo = nextPublishedVersionNo(skill.getId());
        String version = createSkill ? "1.0.0" : incrementPatchVersion(skill.getVersion());
        Path targetDir = officialSkillPath(skill.getOwnerUserId(), draft.getSkillSlug(), version);
        if (Files.exists(targetDir)) {
            throw new JeecgBootException("Published Skill target directory already exists.");
        }
        try {
            Files.createDirectories(targetDir);
            writeSnapshot(targetDir, files);
            writeSkillManifest(targetDir, draft.getDraftName(), draft.getSkillSlug(), version, draft.getDescription(), draft.getOwnerUsername());
            skill.setName(draft.getDraftName());
            skill.setVersion(version);
            skill.setStatus(OpenclawConstants.SKILL_STATUS_APPROVED);
            skill.setDescription(draft.getDescription());
            skill.setPath(targetDir.toString());
            skill.setChecksum(sha256Directory(targetDir));
            skill.setFileSize(directorySize(targetDir));
            skill.setRemark("Published from Skill Review " + review.getId());
            if (createSkill) {
                skill.setCreateBy(reviewer.getUsername());
                skill.setCreateTime(new Date());
                skillService.save(skill);
            } else {
                skill.setUpdateBy(reviewer.getUsername());
                skill.setUpdateTime(new Date());
                skillService.updateById(skill);
            }

            OpenclawPublishedSkillVersion versionRecord = new OpenclawPublishedSkillVersion();
            versionRecord.setId(IdWorker.getIdStr());
            versionRecord.setSkillId(skill.getId());
            versionRecord.setReviewId(review.getId());
            versionRecord.setDraftId(review.getDraftId());
            versionRecord.setDraftVersionNo(review.getVersionNo());
            versionRecord.setPublishedVersionNo(publishedVersionNo);
            versionRecord.setFileSnapshotJson(review.getFileSnapshotJson());
            versionRecord.setFileHash(review.getFileHash());
            versionRecord.setStatus("PUBLISHED");
            versionRecord.setPublishedBy(reviewer.getUsername());
            versionRecord.setPublishedTime(new Date());
            versionRecord.setCreateBy(reviewer.getUsername());
            versionRecord.setCreateTime(new Date());
            versionRecord.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
            publishedVersionMapper.insert(versionRecord);
            return new PublishedResult(skill, versionRecord);
        } catch (IOException | RuntimeException e) {
            cleanupQuietly(targetDir);
            throw e instanceof RuntimeException ? (RuntimeException) e : new JeecgBootException("Publish review snapshot failed: " + e.getMessage(), e);
        }
    }

    private Integer nextPublishedVersionNo(String skillId) {
        OpenclawPublishedSkillVersion latest = latestPublishedVersion(skillId);
        return latest == null ? 1 : latest.getPublishedVersionNo() + 1;
    }

    private OpenclawPublishedSkillVersion latestPublishedVersion(String skillId) {
        if (!StringUtils.hasText(skillId)) {
            return null;
        }
        return publishedVersionMapper.selectOne(new LambdaQueryWrapper<OpenclawPublishedSkillVersion>()
            .eq(OpenclawPublishedSkillVersion::getSkillId, skillId)
            .eq(OpenclawPublishedSkillVersion::getStatus, "PUBLISHED")
            .eq(OpenclawPublishedSkillVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .orderByDesc(OpenclawPublishedSkillVersion::getPublishedVersionNo)
            .last("limit 1"));
    }

    private List<String> collectAiRecordIds(OpenclawSkillDraft draft, OpenclawSkillDraftVersion version) {
        List<OpenclawSkillDraftVersion> versions = draftVersionMapper.selectList(new LambdaQueryWrapper<OpenclawSkillDraftVersion>()
            .eq(OpenclawSkillDraftVersion::getDraftId, draft.getId())
            .le(OpenclawSkillDraftVersion::getVersionNo, version.getVersionNo())
            .eq(OpenclawSkillDraftVersion::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .orderByAsc(OpenclawSkillDraftVersion::getVersionNo));
        List<String> ids = new ArrayList<>();
        for (OpenclawSkillDraftVersion item : versions) {
            if (StringUtils.hasText(item.getSourceRecordId())
                && ("ai_edit".equals(item.getSourceType()) || "ai_repair".equals(item.getSourceType()))) {
                ids.add(item.getSourceRecordId());
            }
        }
        return ids;
    }

    private List<OpenclawSkillAiEditRecord> loadAiRecords(String idsJson) {
        List<OpenclawSkillAiEditRecord> records = new ArrayList<>();
        if (!StringUtils.hasText(idsJson)) {
            return records;
        }
        JSONArray ids = JSON.parseArray(idsJson);
        for (Object id : ids) {
            if (id != null && StringUtils.hasText(String.valueOf(id))) {
                OpenclawSkillAiEditRecord record = aiEditRecordMapper.selectById(String.valueOf(id));
                if (record != null) {
                    records.add(record);
                }
            }
        }
        return records;
    }

    private Map<String, Object> testReportMap(OpenclawSkillTestRun run) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("testRunId", run.getId());
        report.put("draftId", run.getDraftId());
        report.put("draftVersionNo", run.getDraftVersionNo());
        report.put("fileHash", run.getFileHash());
        report.put("status", run.getStatus());
        report.put("lintStatus", run.getLintStatus());
        report.put("gatewayStatus", run.getGatewayStatus());
        report.put("agentKey", run.getAgentKey());
        report.put("input", parseMaybeJson(run.getInputJson()));
        report.put("output", parseMaybeJson(run.getOutputJson()));
        report.put("errorType", run.getErrorType());
        report.put("errorCode", run.getErrorCode());
        report.put("errorMessage", run.getErrorMessage());
        report.put("logs", parseMaybeJson(run.getLogsJson()));
        report.put("report", parseMaybeJson(run.getReportJson()));
        report.put("durationMs", run.getDurationMs());
        return report;
    }

    private Object parseMaybeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return JSON.parse(value);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private Map<String, String> parseSnapshot(String snapshotJson) {
        Map<String, String> files = new LinkedHashMap<>();
        if (!StringUtils.hasText(snapshotJson)) {
            return files;
        }
        JSONObject json = JSON.parseObject(snapshotJson);
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

    private void writeSnapshot(Path root, Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            byte[] data = (entry.getValue() == null ? "" : entry.getValue()).getBytes(StandardCharsets.UTF_8);
            pathSafetyService.validateWritableFile(root, entry.getKey(), data.length);
            Path target = pathSafetyService.resolve(root, entry.getKey());
            pathSafetyService.rejectIfOutsideRoot(root, target);
            Files.createDirectories(target.getParent());
            Files.write(target, data, StandardOpenOption.CREATE_NEW);
        }
    }

    private void writeSkillManifest(Path root, String name, String slug, String version, String description, String author) throws IOException {
        JSONObject manifest = new JSONObject(true);
        manifest.put("name", name);
        manifest.put("slug", slug);
        manifest.put("version", version);
        manifest.put("description", description);
        manifest.put("author", author);
        manifest.put("publishedBy", "jeecg-openclaw-review");
        Files.writeString(root.resolve("skill.json"), JSON.toJSONString(manifest, true), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private Path officialSkillPath(String ownerUserId, String slug, String version) {
        Path ownerRoot = Paths.get(OpenclawConstants.SKILL_ROOT, ownerUserId).toAbsolutePath().normalize();
        Path target = ownerRoot.resolve(slug).resolve(version).normalize();
        if (!target.startsWith(ownerRoot)) {
            throw new JeecgBootException("Invalid Skill target path.");
        }
        return target;
    }

    private String incrementPatchVersion(String baseVersion) {
        if (!StringUtils.hasText(baseVersion)) {
            return "1.0.0";
        }
        String[] parts = baseVersion.trim().split("\\.");
        if (parts.length != 3) {
            return baseVersion.trim() + ".1";
        }
        try {
            int patch = Integer.parseInt(parts[2]);
            return parts[0] + "." + parts[1] + "." + (patch + 1);
        } catch (NumberFormatException e) {
            return baseVersion.trim() + ".1";
        }
    }

    private String sha256Directory(Path root) throws IOException {
        MessageDigest digest = sha256Digest();
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted().toList()) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                digest.update(root.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                try (DigestInputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                    byte[] buffer = new byte[8192];
                    while (input.read(buffer) != -1) {
                        // DigestInputStream updates the digest.
                    }
                }
            }
        }
        return hex(digest.digest());
    }

    private String sha256Text(String text) {
        MessageDigest digest = sha256Digest();
        return hex(digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new JeecgBootException("SHA-256 is not available.", e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }

    private long directorySize(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    private void cleanupQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record PublishedResult(OpenclawSkill skill, OpenclawPublishedSkillVersion version) {
    }
}
