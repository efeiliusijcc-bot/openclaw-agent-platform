package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    public OpenclawSkillImportResultVO importSkill(MultipartFile file) {
        LoginUser user = permissionService.currentUser();
        Path tempDir = null;
        try {
            validateUpload(file);
            OpenclawUserQuota quota = quotaService.getOrCreateQuota(user);
            long usedSkills = lambdaQuery()
                .eq(OpenclawSkill::getOwnerUserId, user.getId())
                .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
                .count();
            if (usedSkills >= quota.getMaxSkills()) {
                throw new JeecgBootException("Skill 配额不足，请联系管理员增加配额");
            }
            String checksum = sha256(file);
            tempDir = Files.createTempDirectory("openclaw-skill-");
            unzipSafely(file, tempDir);
            Path skillMd = tempDir.resolve("SKILL.md");
            if (!Files.exists(skillMd) || Files.isDirectory(skillMd)) {
                throw new JeecgBootException("Skill 包必须在根目录包含 SKILL.md");
            }
            SkillMeta meta = parseSkillMeta(skillMd, file.getOriginalFilename());
            String slug = normalizeSlug(meta.slug);
            String version = normalizeVersion(meta.version);
            boolean exists = lambdaQuery()
                .eq(OpenclawSkill::getOwnerUserId, user.getId())
                .eq(OpenclawSkill::getSlug, slug)
                .eq(OpenclawSkill::getVersion, version)
                .eq(OpenclawSkill::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
                .count() > 0;
            if (exists) {
                throw new JeecgBootException("相同版本 Skill 已存在，禁止重复导入");
            }
            Path targetDir = Paths.get(OpenclawConstants.SKILL_ROOT, user.getId(), slug, version).normalize();
            if (Files.exists(targetDir)) {
                throw new JeecgBootException("Skill 目标目录已存在，禁止覆盖");
            }
            Files.createDirectories(targetDir.getParent());
            moveDirectory(tempDir, targetDir);
            tempDir = null;

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
            return result;
        } catch (IOException e) {
            throw new JeecgBootException("Skill 导入失败: " + e.getMessage(), e);
        } finally {
            cleanupQuietly(tempDir);
        }
    }

    @Override
    public void exportSkill(String id, HttpServletResponse response) {
        OpenclawSkill skill = getById(id);
        if (skill == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(skill.getDelFlag())) {
            throw new JeecgBootException("Skill 不存在");
        }
        permissionService.checkOwnerOrAdmin(skill.getOwnerUserId());
        Path skillPath = Paths.get(skill.getPath()).normalize();
        if (!Files.exists(skillPath) || !Files.isDirectory(skillPath)) {
            throw new JeecgBootException("Skill 文件目录不存在");
        }
        String filename = skill.getSlug() + "-" + skill.getVersion() + ".zip";
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(filename, StandardCharsets.UTF_8));
            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
                zipDirectory(skillPath, skillPath, zos);
            }
            auditLogService.log("skill_export", "skill", skill.getId(), skill);
        } catch (IOException e) {
            throw new JeecgBootException("Skill 导出失败: " + e.getMessage(), e);
        }
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
            throw new JeecgBootException("Skill 已绑定 Agent，请先解绑后再删除");
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
            throw new JeecgBootException("只有 OpenClaw 管理员可以禁用 Skill");
        }
        skill.setStatus(OpenclawConstants.SKILL_STATUS_DISABLED);
        updateById(skill);
        auditLogService.log("skill_disable", "skill", skill.getId(), skill);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new JeecgBootException("只允许上传 zip 文件");
        }
        if (file.getSize() > OpenclawConstants.MAX_SKILL_ZIP_SIZE_BYTES) {
            throw new JeecgBootException("Skill zip 文件超过大小限制");
        }
    }

    private void unzipSafely(MultipartFile file, Path targetDir) throws IOException {
        int fileCount = 0;
        long totalSize = 0L;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.getInputStream()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                validateEntryName(entryName);
                if (entry.isDirectory()) {
                    Files.createDirectories(targetDir.resolve(entryName).normalize());
                    continue;
                }
                fileCount++;
                if (fileCount > OpenclawConstants.MAX_SKILL_ZIP_FILE_COUNT) {
                    throw new JeecgBootException("Skill zip 文件数量超过限制");
                }
                Path out = targetDir.resolve(entryName).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new JeecgBootException("Skill zip 存在路径穿越风险");
                }
                Files.createDirectories(out.getParent());
                long written = copyEntryWithLimit(zis, out, totalSize);
                totalSize += written;
                if (totalSize > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
                    throw new JeecgBootException("Skill zip 解压后总大小超过限制");
                }
                if (Files.isSymbolicLink(out)) {
                    throw new JeecgBootException("Skill zip 禁止包含符号链接");
                }
            }
        }
    }

    private void validateEntryName(String entryName) {
        if (!StringUtils.hasText(entryName) || entryName.contains("\0")) {
            throw new JeecgBootException("Skill zip 包含非法文件名");
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.startsWith("\\") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new JeecgBootException("Skill zip 禁止绝对路径");
        }
        if (normalizedName.contains("../") || normalizedName.equals("..") || normalizedName.startsWith("../")) {
            throw new JeecgBootException("Skill zip 存在路径穿越风险");
        }
        String lower = normalizedName.toLowerCase(Locale.ROOT);
        for (String ext : OpenclawConstants.BLOCKED_SKILL_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                throw new JeecgBootException("Skill zip 包含高风险文件类型: " + ext);
            }
        }
    }

    private long copyEntryWithLimit(InputStream input, Path out, long currentTotal) throws IOException {
        long written = 0L;
        byte[] buffer = new byte[8192];
        int len;
        while ((len = input.read(buffer)) != -1) {
            written += len;
            if (currentTotal + written > OpenclawConstants.MAX_SKILL_UNZIP_SIZE_BYTES) {
                throw new JeecgBootException("Skill zip 解压后总大小超过限制");
            }
            Files.write(out, java.util.Arrays.copyOf(buffer, len), Files.exists(out) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
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
            throw new JeecgBootException("当前 JDK 不支持 SHA-256", e);
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
            throw new JeecgBootException("Skill 版本号格式非法");
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
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveError) {
            Files.move(source, target);
        }
    }

    private static class SkillMeta {
        private String name;
        private String slug;
        private String version;
        private String description;
        private String heading;
    }
}
