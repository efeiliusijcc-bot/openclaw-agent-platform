package org.jeecg.modules.openclaw.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.vo.OpenclawSkillRepairVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SkillAiEditValidator {
    private static final int MAX_FILE_COUNT = 20;
    private static final int MAX_CONTENT_BYTES = 200_000;
    private static final Set<String> ALLOWED_ACTIONS = Set.of("upsert", "delete");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".md", ".txt", ".py", ".json", ".yaml", ".yml", ".toml", ".ini"
    );

    @Autowired
    private OpenclawPathSafetyService pathSafetyService;

    public void validateModelResult(OpenclawSkillRepairVO result, Path draftRoot) {
        if (result == null) {
            throw new JeecgBootException("AI edit result is empty.");
        }
        validateFiles(result.getFiles(), draftRoot);
    }

    public void validateFiles(List<OpenclawSkillRepairVO.FileSuggestion> files, Path draftRoot) {
        if (files == null || files.isEmpty()) {
            throw new JeecgBootException("AI edit files are required.");
        }
        if (files.size() > MAX_FILE_COUNT) {
            throw new JeecgBootException("AI edit can change at most " + MAX_FILE_COUNT + " files.");
        }
        for (OpenclawSkillRepairVO.FileSuggestion file : files) {
            validateFile(file, draftRoot);
        }
    }

    public void validateDraftVersion(String expectedVersion, String actualVersion, String expectedHash, String actualHash) {
        if (!StringUtils.hasText(expectedVersion) || !expectedVersion.equals(actualVersion)) {
            throw new JeecgBootException("Draft version changed after AI edit preview. Regenerate suggestions.");
        }
        if (!StringUtils.hasText(expectedHash) || !expectedHash.equals(actualHash)) {
            throw new JeecgBootException("Draft files changed after AI edit preview. Regenerate suggestions.");
        }
    }

    private void validateFile(OpenclawSkillRepairVO.FileSuggestion file, Path draftRoot) {
        if (file == null || !StringUtils.hasText(file.getPath())) {
            throw new JeecgBootException("AI edit file path is required.");
        }
        String action = normalizeAction(file.getAction());
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new JeecgBootException("Unsupported AI edit action: " + file.getAction());
        }
        String path = file.getPath().replace('\\', '/').trim();
        validateAllowedPath(path);
        Path target = pathSafetyService.resolve(draftRoot, path);
        pathSafetyService.rejectIfOutsideRoot(draftRoot, target);
        if (Files.exists(target) && !Files.isRegularFile(target)) {
            throw new JeecgBootException("AI edit can only modify regular files: " + path);
        }
        if ("delete".equals(action) && "SKILL.md".equals(path)) {
            throw new JeecgBootException("SKILL.md cannot be deleted.");
        }
        if ("upsert".equals(action)) {
            if (file.getContent() == null) {
                throw new JeecgBootException("AI edit upsert content is required: " + path);
            }
            int bytes = file.getContent().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_CONTENT_BYTES) {
                throw new JeecgBootException("AI edit content is too large: " + path);
            }
        }
    }

    private void validateAllowedPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        if (!allowed) {
            throw new JeecgBootException("AI edit path is not in the editable text file whitelist: " + path);
        }
    }

    private String normalizeAction(String action) {
        return StringUtils.hasText(action) ? action.trim().toLowerCase(Locale.ROOT) : "upsert";
    }
}
