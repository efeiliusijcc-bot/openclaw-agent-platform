package org.jeecg.modules.openclaw.constant;

import java.util.Set;

public final class OpenclawConstants {
    private OpenclawConstants() {
    }

    public static final String ROLE_ADMIN = "openclaw_admin";
    public static final String ROLE_EMPLOYEE = "openclaw_employee";
    public static final String ROLE_SKILL_REVIEWER = "openclaw_skill_reviewer";

    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";

    public static final String AGENT_STATUS_DRAFT = "draft";
    public static final String AGENT_STATUS_DISABLED = "disabled";

    public static final String WORKSPACE_STATUS_ACTIVE = "active";
    public static final String WORKSPACE_STATUS_DELETED = "deleted";

    public static final String SKILL_STATUS_PRIVATE = "private";
    public static final String SKILL_STATUS_DISABLED = "disabled";

    public static final int DEL_FLAG_NORMAL = 0;
    public static final int DEL_FLAG_DELETED = 1;

    public static final long MAX_SKILL_ZIP_SIZE_BYTES = 50L * 1024L * 1024L;
    public static final long MAX_SKILL_UNZIP_SIZE_BYTES = 200L * 1024L * 1024L;
    public static final int MAX_SKILL_ZIP_FILE_COUNT = 1000;

    public static final String WORKSPACE_ROOT = "/data/openclaw-platform/workspaces";
    public static final String SKILL_ROOT = "/data/openclaw-platform/skills";

    public static final Set<String> BLOCKED_SKILL_EXTENSIONS = Set.of(
        ".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".ps1", ".sh", ".jar", ".war", ".class"
    );
}
