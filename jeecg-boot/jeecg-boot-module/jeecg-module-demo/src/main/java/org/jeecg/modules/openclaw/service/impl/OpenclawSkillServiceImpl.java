package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSkillService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.vo.OpenclawSkillImportResultVO;
import org.jeecg.modules.openclaw.vo.OpenclawSkillQualityCheckVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class OpenclawSkillServiceImpl extends ServiceImpl<OpenclawSkillMapper, OpenclawSkill> implements IOpenclawSkillService {
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawUserQuotaService quotaService;
    @Autowired
    private IOpenclawAgentSkillService agentSkillService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkill createDraftSkill(OpenclawSkill request) {
        LoginUser user = permissionService.currentUser();
        if (!StringUtils.hasText(request.getName())) {
            throw new JeecgBootException("Skill name is required");
        }
        OpenclawUserQuota quota = quotaService.getOrCreateQuota(user);
        long usedSkills = lambdaQuery()
            .eq(OpenclawSkill::getOwnerUserId, user.getId())
            .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count();
        if (usedSkills >= quota.getMaxSkills()) {
            throw new JeecgBootException("Skill quota is not enough");
        }
        String version = normalizeVersion(request.getVersion());
        String slug = normalizeSlug(request.getName()) + "-" + IdWorker.getIdStr();
        Path targetDir = Paths.get(OpenclawConstants.SKILL_ROOT, user.getId(), slug, version).normalize();
        Path userSkillRoot = Paths.get(OpenclawConstants.SKILL_ROOT, user.getId()).normalize();
        if (!targetDir.startsWith(userSkillRoot)) {
            throw new JeecgBootException("Invalid Skill target path.");
        }
        if (Files.exists(targetDir)) {
            throw new JeecgBootException("Skill target directory already exists");
        }
        try {
            Files.createDirectories(targetDir);
            writeStudioFiles(targetDir, request.getName(), slug, version, request.getDescription(), user.getUsername());
            writeSkillManifest(targetDir, request.getName(), slug, version, request.getDescription(), user.getUsername());
        } catch (IOException e) {
            cleanupQuietly(targetDir);
            throw new JeecgBootException("Create Skill draft files failed: " + e.getMessage(), e);
        }

        OpenclawSkill skill = new OpenclawSkill();
        skill.setOwnerUserId(user.getId());
        skill.setOwnerUsername(user.getUsername());
        skill.setName(request.getName());
        skill.setSlug(slug);
        skill.setVersion(version);
        skill.setScope("private");
        skill.setStatus(OpenclawConstants.SKILL_STATUS_DRAFT);
        skill.setDescription(request.getDescription());
        skill.setPath(targetDir.toString());
        skill.setChecksum(sha256Directory(targetDir));
        skill.setFileSize(directorySize(targetDir));
        skill.setRemark(request.getRemark());
        skill.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        save(skill);
        auditLogService.log("skill_studio_create", "skill", skill.getId(), skill);
        return skill;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenclawSkillImportResultVO importSkill(MultipartFile file) {
        LoginUser user = permissionService.currentUser();
        Path tempDir = null;
        Path movedTargetDir = null;
        boolean success = false;
        try {
            validateUpload(file);
            OpenclawUserQuota quota = quotaService.getOrCreateQuota(user);
            long usedSkills = lambdaQuery()
                .eq(OpenclawSkill::getOwnerUserId, user.getId())
                .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
                .count();
            if (usedSkills >= quota.getMaxSkills()) {
                throw new JeecgBootException("Skill quota is not enough. Contact an administrator to increase the quota.");
            }
            String checksum = sha256(file);
            tempDir = Files.createTempDirectory("openclaw-skill-");
            unzipSafely(file, tempDir);
            Path skillMd = tempDir.resolve("SKILL.md");
            if (!Files.exists(skillMd) || Files.isDirectory(skillMd)) {
                throw new JeecgBootException("Skill package must contain SKILL.md at the root directory.");
            }
            SkillMeta meta = parseSkillMeta(skillMd, file.getOriginalFilename());
            String slug = normalizeSlug(meta.slug);
            String version = normalizeVersion(meta.version);
            boolean exists = lambdaQuery()
                .eq(OpenclawSkill::getOwnerUserId, user.getId())
                .eq(OpenclawSkill::getSlug, slug)
                .eq(OpenclawSkill::getVersion, version)
                .count() > 0;
            if (exists) {
                throw new JeecgBootException("A Skill with the same slug and version already exists.");
            }
            Path targetDir = Paths.get(OpenclawConstants.SKILL_ROOT, user.getId(), slug, version).normalize();
            Path userSkillRoot = Paths.get(OpenclawConstants.SKILL_ROOT, user.getId()).normalize();
            if (!targetDir.startsWith(userSkillRoot)) {
                throw new JeecgBootException("Invalid Skill target path.");
            }
            if (Files.exists(targetDir)) {
                throw new JeecgBootException("Skill target directory already exists.");
            }
            Files.createDirectories(targetDir.getParent());
            movedTargetDir = targetDir;
            moveDirectory(tempDir, targetDir);
            tempDir = null;
            writeSkillManifest(targetDir, meta.name, slug, version, meta.description, user.getUsername());

            OpenclawSkill skill = new OpenclawSkill();
            skill.setOwnerUserId(user.getId());
            skill.setOwnerUsername(user.getUsername());
            skill.setName(meta.name);
            skill.setSlug(slug);
            skill.setVersion(version);
            skill.setScope("private");
            skill.setStatus(OpenclawConstants.SKILL_STATUS_PRIVATE);
            skill.setDescription(meta.description);
            skill.setPath(targetDir.toString());
            skill.setChecksum(checksum);
            skill.setFileSize(file.getSize());
            skill.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
            save(skill);
            auditLogService.log("skill_import", "skill", skill.getId(), skill);

            OpenclawSkillImportResultVO result = new OpenclawSkillImportResultVO();
            result.setSkillId(skill.getId());
            result.setName(skill.getName());
            result.setSlug(skill.getSlug());
            result.setVersion(skill.getVersion());
            result.setChecksum(skill.getChecksum());
            result.setFileSize(skill.getFileSize());
            success = true;
            return result;
        } catch (IOException e) {
            throw new JeecgBootException("Skill import failed: " + e.getMessage(), e);
        } finally {
            cleanupQuietly(tempDir);
            if (!success) {
                cleanupQuietly(movedTargetDir);
            }
        }
    }

    @Override
    public void exportSkill(String id, HttpServletResponse response) {
        OpenclawSkill skill = getById(id);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill does not exist.");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        Path skillPath = Paths.get(skill.getPath()).normalize();
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            throw new JeecgBootException("Skill file directory does not exist.");
        }
        validateExportManifest(skill, skillPath);
        String filename = skill.getSlug() + "-" + skill.getVersion() + ".zip";
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
                zipDirectory(skillPath, skillPath, zos);
            }
            auditLogService.log("skill_export", "skill", skill.getId(), skill);
        } catch (IOException e) {
            throw new JeecgBootException("Skill export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public OpenclawSkillQualityCheckVO checkSkillQuality(String id) {
        OpenclawSkill skill = getById(id);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill does not exist");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        Path skillPath = Paths.get(skill.getPath()).normalize();
        OpenclawSkillQualityCheckVO result = new OpenclawSkillQualityCheckVO();
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            result.getMissingFiles().add("skill directory");
            result.getWarnings().add("Skill files are missing on disk.");
            return result;
        }

        requireFile(result, skillPath, "SKILL.md");
        requireFile(result, skillPath, "README.md");
        requireFile(result, skillPath, "manifest.json");
        if (!Files.exists(skillPath.resolve("examples"))) {
            result.getWarnings().add("examples/ is recommended for handoff validation.");
        }
        inspectSkillDirectory(result, skillPath);
        if (!StringUtils.hasText(skill.getDescription())) {
            result.getWarnings().add("Skill description is empty.");
        }

        int score = 100 - result.getMissingFiles().size() * 30 - result.getWarnings().size() * 10;
        result.setScore(Math.max(0, score));
        result.setPassed(result.getMissingFiles().isEmpty() && result.getWarnings().size() <= 2);
        result.getChecklist().add("Root SKILL.md exists");
        result.getChecklist().add("README.md exists for delivery");
        result.getChecklist().add("manifest.json exists for tooling");
        result.getChecklist().add("No blocked executable file types");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logicDeleteSkill(String id) {
        OpenclawSkill skill = getById(id);
        if (skill == null) {
            return;
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        if (agentSkillService.countEnabledBySkill(id) > 0) {
            throw new JeecgBootException("Skill is bound to an Agent. Unbind it before deleting.");
        }
        skill.setStatus(OpenclawConstants.SKILL_STATUS_DISABLED);
        skill.setDelFlag(OpenclawConstants.DEL_FLAG_DELETED);
        updateById(skill);
        auditLogService.log("skill_delete", "skill", skill.getId(), skill);
    }

    @Override
    public void disableSkill(String id) {
        OpenclawSkill skill = getById(id);
        if (skill == null) {
            return;
        }
        if (!permissionService.isAdmin(permissionService.currentUser())) {
            throw new JeecgBootException("Only OpenClaw administrators can disable Skills.");
        }
        skill.setStatus(OpenclawConstants.SKILL_STATUS_DISABLED);
        updateById(skill);
        auditLogService.log("skill_disable", "skill", skill.getId(), skill);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("Uploaded file cannot be empty.");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new JeecgBootException("Only zip files are allowed.");
        }
        if (file.getSize() > OpenclawConstants.MAX_SKILL_ZIP_SIZE_BYTES) {
            throw new JeecgBootException("Skill zip file exceeds the size limit.");
        }
    }

    private void writeStudioFiles(Path targetDir, String name, String slug, String version, String description, String owner) throws IOException {
        String cleanName = name.trim();
        String safeDescription = StringUtils.hasText(description) ? description.trim() : "Describe what this Skill does and when to use it.";
        String skillMd = "# " + cleanName + "\n\n"
            + "name: " + cleanName + "\n"
            + "slug: " + slug + "\n"
            + "version: " + version + "\n"
            + "description: " + safeDescription + "\n\n"
            + "## When to use\n\n"
            + "- Use this Skill when the user needs the workflow described above.\n\n"
            + "## Inputs\n\n"
            + "- Fill in the required business inputs before running.\n\n"
            + "## Workflow\n\n"
            + "1. Confirm the requested outcome.\n"
            + "2. Collect the required inputs.\n"
            + "3. Execute the steps in order and report the result.\n\n"
            + "## Quality bar\n\n"
            + "- The Skill must include clear inputs, expected output, and a test example.\n";
        String readme = "# " + cleanName + "\n\n"
            + safeDescription + "\n\n"
            + "## Delivery checklist\n\n"
            + "- SKILL.md is complete.\n"
            + "- manifest.json matches the Skill metadata.\n"
            + "- examples/input.json can be used for a smoke test.\n";
        Files.writeString(targetDir.resolve("SKILL.md"), skillMd, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.writeString(targetDir.resolve("README.md"), readme, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.createDirectories(targetDir.resolve("examples"));
        Files.writeString(targetDir.resolve("examples").resolve("input.json"), "{\n  \"task\": \"Describe the first smoke test for this Skill.\"\n}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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

    private void validateExportManifest(OpenclawSkill skill, Path skillPath) {
        Path manifestPath = skillPath.resolve("manifest.json").normalize();
        if (!manifestPath.startsWith(skillPath) || !Files.isRegularFile(manifestPath)) {
            throw new JeecgBootException("Skill manifest.json is missing; run Skill quality check or re-import the Skill.");
        }
        try {
            JSONObject manifest = JSONObject.parseObject(Files.readString(manifestPath, StandardCharsets.UTF_8));
            if (manifest == null) {
                throw new JeecgBootException("Skill manifest.json is invalid.");
            }
            if (!skill.getSlug().equals(manifest.getString("slug"))) {
                throw new JeecgBootException("Skill manifest slug does not match database metadata.");
            }
            if (!skill.getVersion().equals(manifest.getString("version"))) {
                throw new JeecgBootException("Skill manifest version does not match database metadata.");
            }
            JSONArray files = manifest.getJSONArray("files");
            if (files == null || files.isEmpty()) {
                throw new JeecgBootException("Skill manifest does not contain file entries.");
            }
        } catch (IOException e) {
            throw new JeecgBootException("Skill manifest validation failed: " + e.getMessage(), e);
        }
    }

    private void requireFile(OpenclawSkillQualityCheckVO result, Path root, String filename) {
        if (!Files.exists(root.resolve(filename)) || Files.isDirectory(root.resolve(filename))) {
            result.getMissingFiles().add(filename);
        }
    }

    private void inspectSkillDirectory(OpenclawSkillQualityCheckVO result, Path root) {
        try (var walk = Files.walk(root)) {
            List<Path> entries = walk.toList();
            List<Path> files = entries.stream().filter(Files::isRegularFile).toList();
            result.setFileCount(files.size());
            long total = 0L;
            boolean hasBlockedFile = false;
            for (Path entry : entries) {
                if (Files.isSymbolicLink(entry)) {
                    result.getWarnings().add("Symbolic link is not allowed: " + root.relativize(entry));
                    hasBlockedFile = true;
                }
            }
            for (Path file : files) {
                String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String ext : OpenclawConstants.BLOCKED_SKILL_EXTENSIONS) {
                    if (lower.endsWith(ext)) {
                        result.getWarnings().add("Blocked file type: " + root.relativize(file));
                        hasBlockedFile = true;
                    }
                }
                total += Files.size(file);
            }
            result.setTotalSize(total);
            if (hasBlockedFile) {
                result.getMissingFiles().add("safe file types");
            }
            if (total > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
                result.getMissingFiles().add("delivery size limit");
                result.getWarnings().add("Skill directory is larger than delivery limit.");
            }
        } catch (IOException e) {
            result.getWarnings().add("Failed to inspect Skill files: " + e.getMessage());
        }
    }

    private Long directorySize(Path root) {
        try (var walk = Files.walk(root)) {
            long total = 0L;
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                total += Files.size(file);
            }
            return total;
        } catch (IOException e) {
            return 0L;
        }
    }

    private String sha256Directory(Path root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> files;
            try (var walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    private void unzipSafely(MultipartFile file, Path targetDir) throws IOException {
        int entryCount = 0;
        long totalSize = 0L;
        Set<Path> extractedPaths = new HashSet<>();
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new BufferedInputStream(file.getInputStream()), StandardCharsets.UTF_8.name(), true, true)) {
            ArchiveEntry archiveEntry;
            while ((archiveEntry = zis.getNextEntry()) != null) {
                if (!(archiveEntry instanceof ZipArchiveEntry entry)) {
                    throw new JeecgBootException("Skill zip contains an unsupported archive entry.");
                }
                entryCount++;
                if (entryCount > OpenclawConstants.MAX_SKILL_ZIP_FILE_COUNT) {
                    throw new JeecgBootException("Skill zip contains too many files.");
                }
                String entryName = entry.getName();
                Path out = resolveZipEntryPath(targetDir, entryName);
                if (!extractedPaths.add(out)) {
                    throw new JeecgBootException("Skill zip contains a duplicate path: " + entryName);
                }
                if (entry.isUnixSymlink()) {
                    throw new JeecgBootException("Skill zip cannot contain symbolic links.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                long written = copyEntryWithLimit(zis, out, totalSize);
                totalSize += written;
                if (totalSize > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
                    throw new JeecgBootException("Skill zip exceeds the uncompressed size limit.");
                }
            }
        }
    }

    private Path resolveZipEntryPath(Path targetDir, String entryName) {
        if (!StringUtils.hasText(entryName) || entryName.contains("\0")) {
            throw new JeecgBootException("Skill zip contains an invalid file name.");
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.startsWith("\\") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new JeecgBootException("Skill zip cannot contain absolute paths.");
        }
        for (String segment : normalizedName.split("/")) {
            if ("..".equals(segment)) {
                throw new JeecgBootException("Skill zip contains a path traversal risk.");
            }
        }
        String lower = normalizedName.toLowerCase(Locale.ROOT);
        for (String ext : OpenclawConstants.BLOCKED_SKILL_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                throw new JeecgBootException("Skill zip contains a blocked file type: " + ext);
            }
        }
        Path out = targetDir.resolve(normalizedName).normalize();
        if (!out.startsWith(targetDir)) {
            throw new JeecgBootException("Skill zip contains a path traversal risk.");
        }
        return out;
    }

    private long copyEntryWithLimit(InputStream input, Path out, long currentTotal) throws IOException {
        long written = 0L;
        byte[] buffer = new byte[8192];
        int len;
        try (OutputStream output = Files.newOutputStream(out, StandardOpenOption.CREATE_NEW)) {
            while ((len = input.read(buffer)) != -1) {
                written += len;
                if (currentTotal + written > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
                    throw new JeecgBootException("Skill zip exceeds the uncompressed size limit.");
                }
                output.write(buffer, 0, len);
            }
        }
        return written;
    }

    private String sha256(MultipartFile file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(file.getInputStream(), digest)) {
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

    private SkillMeta parseSkillMeta(Path skillMd, String filename) throws IOException {
        SkillMeta meta = new SkillMeta();
        meta.version = "1.0.0";
        meta.name = stripZip(filename);
        for (String line : Files.readAllLines(skillMd, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ") && !StringUtils.hasText(meta.heading)) {
                meta.heading = trimmed.substring(2).trim();
                meta.name = meta.heading;
            } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("name:")) {
                meta.name = trimmed.substring(5).trim();
            } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("slug:")) {
                meta.slug = trimmed.substring(5).trim();
            } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("version:")) {
                meta.version = trimmed.substring(8).trim();
            } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("description:")) {
                meta.description = trimmed.substring(12).trim();
            }
        }
        if (!StringUtils.hasText(meta.name)) {
            meta.name = "Imported Skill " + IdWorker.getIdStr();
        }
        if (!StringUtils.hasText(meta.slug)) {
            meta.slug = meta.name;
        }
        return meta;
    }

    private String stripZip(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "imported-skill-" + UUID.randomUUID();
        }
        String clean = Paths.get(filename).getFileName().toString();
        if (clean.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return clean.substring(0, clean.length() - 4);
        }
        return clean;
    }

    private String normalizeSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        if (!StringUtils.hasText(slug)) {
            slug = "skill-" + IdWorker.getIdStr();
        }
        if (slug.length() > 100) {
            slug = slug.substring(0, 100);
        }
        return slug;
    }

    private String normalizeVersion(String raw) {
        String version = StringUtils.hasText(raw) ? raw.trim() : "1.0.0";
        if (!version.matches("[0-9A-Za-z._-]{1,50}")) {
            throw new JeecgBootException("Skill version format is invalid.");
        }
        return version;
    }

    private void zipDirectory(Path root, Path current, ZipOutputStream zos) throws IOException {
        try (var stream = Files.list(current)) {
            for (Path path : stream.toList()) {
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                String entryName = root.relativize(path).toString().replace('\\', '/');
                if (Files.isDirectory(path)) {
                    zipDirectory(root, path, zos);
                } else {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private void cleanupQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // best effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort temp cleanup
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        List<Path> items;
        try (var walk = Files.walk(source)) {
            items = walk.sorted(Comparator.comparingInt(path -> path.getNameCount())).toList();
        }
        for (Path item : items) {
            if (source.equals(item)) {
                continue;
            }
            Path destination = target.resolve(source.relativize(item)).normalize();
            if (!destination.startsWith(target)) {
                throw new IOException("Invalid Skill path: " + destination);
            }
            if (Files.isDirectory(item)) {
                Files.createDirectories(destination);
            } else {
                Files.createDirectories(destination.getParent());
                Files.copy(item, destination);
            }
        }
        cleanupQuietly(source);
    }

    private static class SkillMeta {
        private String name;
        private String slug;
        private String version;
        private String description;
        private String heading;
    }
}
