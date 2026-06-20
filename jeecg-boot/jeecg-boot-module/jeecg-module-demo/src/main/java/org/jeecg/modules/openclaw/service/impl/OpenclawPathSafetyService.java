package org.jeecg.modules.openclaw.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class OpenclawPathSafetyService {
    private static final long MAX_EDIT_FILE_SIZE = 1024L * 1024L;
    private static final int MAX_RELATIVE_PATH_LENGTH = 240;

    public Path resolve(Path root, String relativePath) {
        if (!StringUtils.hasText(relativePath) || relativePath.contains("\0")) {
            throw new JeecgBootException("Invalid file path.");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new JeecgBootException("Absolute paths are not allowed.");
        }
        if (normalized.length() > MAX_RELATIVE_PATH_LENGTH) {
            throw new JeecgBootException("File path is too long.");
        }
        for (String segment : normalized.split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw new JeecgBootException("Path traversal is not allowed.");
            }
        }
        rejectBlockedExtension(normalized);
        Path safeRoot = root.toAbsolutePath().normalize();
        Path target = safeRoot.resolve(normalized).normalize();
        if (!target.startsWith(safeRoot)) {
            throw new JeecgBootException("Path traversal is not allowed.");
        }
        return target;
    }

    public void validateWritableFile(Path root, String relativePath, long sizeBytes) {
        Path target = resolve(root, relativePath);
        if (Files.isSymbolicLink(target)) {
            throw new JeecgBootException("Symbolic links are not allowed.");
        }
        if (sizeBytes > MAX_EDIT_FILE_SIZE) {
            throw new JeecgBootException("File is larger than the editable size limit.");
        }
    }

    public void rejectSymlink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new JeecgBootException("Symbolic links are not allowed.");
        }
    }

    public void rejectIfOutsideRoot(Path root, Path target) {
        Path safeRoot = root.toAbsolutePath().normalize();
        Path safeTarget = target.toAbsolutePath().normalize();
        if (!safeTarget.startsWith(safeRoot)) {
            throw new JeecgBootException("Path traversal is not allowed.");
        }
    }

    public void rejectBlockedExtension(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        for (String ext : OpenclawConstants.BLOCKED_SKILL_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                throw new JeecgBootException("Blocked file type: " + ext);
            }
        }
    }

    public void rejectOversized(Path file) throws IOException {
        if (Files.isRegularFile(file) && Files.size(file) > MAX_EDIT_FILE_SIZE) {
            throw new JeecgBootException("File is larger than the editable size limit.");
        }
    }
}
