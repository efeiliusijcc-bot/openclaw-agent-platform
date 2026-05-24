package org.jeecg.modules.openclaw.service.impl;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class OpenclawWorkspaceMaterializer {
    public void materialize(OpenclawAgent agent, OpenclawWorkspace workspace) {
        if (workspace == null || !StringUtils.hasText(workspace.getPath())) {
            throw new JeecgBootException("Workspace path is empty");
        }
        try {
            Path root = Paths.get(workspace.getPath()).normalize();
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("skills"));
            Files.createDirectories(root.resolve("files"));
            Files.createDirectories(root.resolve("logs"));
            Files.createDirectories(root.resolve("output"));
            writeManagedFile(root.resolve("AGENTS.md"), agentInstructions(agent, workspace));
            writeManagedFile(root.resolve("USER.md"), userInstructions(agent, workspace));
            writeManagedFile(root.resolve("IDENTITY.md"), identity(agent, workspace));
        } catch (IOException e) {
            throw new JeecgBootException("Create OpenClaw workspace files failed: " + e.getMessage(), e);
        }
    }

    private void writeManagedFile(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String agentInstructions(OpenclawAgent agent, OpenclawWorkspace workspace) {
        return "# " + safe(agent.getName()) + "\n\n"
            + "- Agent Key: `" + safe(agent.getAgentKey()) + "`\n"
            + "- Workspace Key: `" + safe(workspace.getWorkspaceKey()) + "`\n"
            + "- Owner: `" + safe(agent.getUsername()) + "`\n\n"
            + "This workspace is managed by JeecgBoot OpenClaw Platform.\n";
    }

    private String userInstructions(OpenclawAgent agent, OpenclawWorkspace workspace) {
        return "# User Context\n\n"
            + "- User ID: `" + safe(agent.getUserId()) + "`\n"
            + "- Username: `" + safe(agent.getUsername()) + "`\n"
            + "- Workspace: `" + safe(workspace.getName()) + "`\n";
    }

    private String identity(OpenclawAgent agent, OpenclawWorkspace workspace) {
        return "# Identity\n\n"
            + "name: " + safe(agent.getName()) + "\n"
            + "agentKey: " + safe(agent.getAgentKey()) + "\n"
            + "workspaceKey: " + safe(workspace.getWorkspaceKey()) + "\n"
            + "owner: " + safe(agent.getUsername()) + "\n";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
