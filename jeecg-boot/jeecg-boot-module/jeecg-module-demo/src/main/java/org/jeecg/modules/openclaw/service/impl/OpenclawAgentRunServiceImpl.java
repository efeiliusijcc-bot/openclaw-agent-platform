package org.jeecg.modules.openclaw.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.dto.OpenclawAgentRunTestDTO;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawGatewayNodeMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentRunMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentRunService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OpenclawAgentRunServiceImpl extends ServiceImpl<OpenclawAgentRunMapper, OpenclawAgentRun> implements IOpenclawAgentRunService {
    private static final int MAX_PROMPT_LENGTH = 8000;
    private static final int MAX_SUMMARY_LENGTH = 12000;
    private static final int MAX_ERROR_LENGTH = 8000;

    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawGatewayNodeMapper gatewayNodeMapper;
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawUserQuotaService quotaService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @Value("${openclaw.run.cli-path:${OPENCLAW_CLI_PATH:openclaw}}")
    private String openclawCliPath;

    @Value("${openclaw.run.timeout-seconds:${OPENCLAW_RUN_TIMEOUT_SECONDS:600}}")
    private Long runTimeoutSeconds;

    @Value("${openclaw.gateway.base-url:${OPENCLAW_GATEWAY_BASE_URL:${OPENCLAW_GATEWAY_URL:http://172.17.0.1:18089}}}")
    private String defaultGatewayBaseUrl;

    @Value("${openclaw.gateway.token:${OPENCLAW_GATEWAY_TOKEN:}}")
    private String gatewayToken;

    @Override
    public OpenclawAgentRunResultVO runTest(String agentId, OpenclawAgentRunTestDTO dto) {
        LoginUser user = permissionService.currentUser();
        OpenclawAgent agent = requireAgent(agentId);
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        String prompt = normalizePrompt(dto);
        precheckRun(agent);
        checkRunQuota(user, agent);

        Date startTime = new Date();
        OpenclawAgentRun run = createRunningRun(user, agent, prompt, startTime);
        save(run);

        try {
            CliResult result = executeCli(agent.getAgentKey(), prompt);
            Date finishTime = new Date();
            run.setFinishTime(finishTime);
            run.setDurationMs(finishTime.getTime() - startTime.getTime());
            applyCliResult(run, result);
            persistRunArtifacts(run, agent, result, run.getOutputSummary(), null);
            updateById(run);
            auditRun("agent_run_test", run, agent);
            return toResult(run, agent);
        } catch (RunTimeoutException e) {
            Date finishTime = new Date();
            run.setFinishTime(finishTime);
            run.setDurationMs(finishTime.getTime() - startTime.getTime());
            run.setStatus(OpenclawConstants.RUN_STATUS_TIMEOUT);
            run.setErrorType(OpenclawConstants.RUN_ERROR_GATEWAY_TIMEOUT);
            run.setErrorMessage(trim(e.getMessage(), MAX_ERROR_LENGTH));
            persistRunArtifacts(run, agent, null, null, e);
            updateById(run);
            auditLogService.logFailure("agent_run_test", "agent_run", run.getId(), toResult(run, agent));
            return toResult(run, agent);
        } catch (Exception e) {
            Date finishTime = new Date();
            run.setFinishTime(finishTime);
            run.setDurationMs(finishTime.getTime() - startTime.getTime());
            run.setStatus(OpenclawConstants.RUN_STATUS_FAILED);
            run.setErrorType(classifyError(e));
            run.setErrorMessage(trim(e.getMessage(), MAX_ERROR_LENGTH));
            persistRunArtifacts(run, agent, null, null, e);
            updateById(run);
            auditLogService.logFailure("agent_run_test", "agent_run", run.getId(), toResult(run, agent));
            return toResult(run, agent);
        }
    }

    @Override
    public SseEmitter chatStream(String agentId, OpenclawAgentRunTestDTO dto) {
        LoginUser user = permissionService.currentUser();
        OpenclawAgent agent = requireAgent(agentId);
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        String prompt = normalizePrompt(dto);
        precheckRun(agent);
        checkRunQuota(user, agent);
        String conversationId = normalizeConversationId(dto);

        SseEmitter emitter = new SseEmitter((timeoutSeconds() + 30) * 1000L);
        CompletableFuture.runAsync(() -> doChatStream(user, agent, prompt, conversationId, emitter));
        return emitter;
    }

    private void doChatStream(LoginUser user, OpenclawAgent agent, String prompt, String conversationId, SseEmitter emitter) {
        OpenclawAgentRun run = null;
        Date startTime = new Date();
        String output = "";
        try {
            String model = "openclaw/" + agent.getAgentKey();
            run = createRunningRun(user, agent, prompt, startTime);
            run.setConversationId(conversationId);
            run.setRunType(OpenclawConstants.RUN_TYPE_CHAT);
            run.setStreaming(1);
            run.setModel(model);
            save(run);

            sendEvent(emitter, "run_created", streamPayload(run, agent, null, null));
            output = executeGatewayStream(agent, model, prompt, conversationId, emitter);
            finishRun(run, startTime, OpenclawConstants.RUN_STATUS_SUCCESS, output, null);
            persistRunArtifacts(run, agent, null, output, null);
            updateById(run);
            safeAuditLog("agent_chat_stream", run, agent);
            try {
                sendEvent(emitter, "done", streamPayload(run, agent, null, null));
            } catch (IOException ignored) {
                // The run has already succeeded and been persisted; a client-side SSE close must not mark it failed.
            }
            emitter.complete();
        } catch (RunTimeoutException e) {
            finishFailedStreamRun(run, startTime, OpenclawConstants.RUN_STATUS_TIMEOUT, e.getMessage(), agent, emitter, "timeout");
        } catch (Exception e) {
            finishFailedStreamRun(run, startTime, OpenclawConstants.RUN_STATUS_FAILED, e.getMessage(), agent, emitter, "error");
        }
    }

    private String executeGatewayStream(OpenclawAgent agent, String model, String prompt, String conversationId, SseEmitter emitter) throws Exception {
        String baseUrl = gatewayBaseUrl(agent);
        if (baseUrl.startsWith("ws://") || baseUrl.startsWith("wss://")) {
            return executeCliStreamCompat(agent, prompt, emitter);
        }

        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", true);
        body.put("messages", List.of(new JSONObject()
            .fluentPut("role", "user")
            .fluentPut("content", prompt)));
        body.put("metadata", new JSONObject()
            .fluentPut("agent_id", agent.getId())
            .fluentPut("agent_key", agent.getAgentKey())
            .fluentPut("conversation_id", conversationId));

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .timeout(java.time.Duration.ofSeconds(timeoutSeconds() + 5))
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body.toJSONString(), StandardCharsets.UTF_8));
        if (StringUtils.hasText(gatewayToken)) {
            builder.header("Authorization", "Bearer " + gatewayToken.trim());
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
        HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String error = readStream(response.body());
            throw new JeecgBootException("OpenClaw Gateway returned HTTP " + response.statusCode() + ": " + trim(error, MAX_ERROR_LENGTH));
        }
        String output = readGatewayEventStream(response.body(), emitter);
        if (!StringUtils.hasText(output)) {
            return executeCliStreamCompat(agent, prompt, emitter);
        }
        return output;
    }

    private String executeCliStreamCompat(OpenclawAgent agent, String prompt, SseEmitter emitter) throws Exception {
        CliResult result = executeCli(agent.getAgentKey(), prompt);
        ParsedOutput parsed = parseOutput(result.stdout);
        boolean timeout = OpenclawConstants.RUN_STATUS_TIMEOUT.equalsIgnoreCase(parsed.status);
        boolean success = result.exitCode == 0 && ("ok".equalsIgnoreCase(parsed.status) || !StringUtils.hasText(parsed.status));
        if (timeout) {
            throw new RunTimeoutException(firstText(parsed.errorMessage, result.stderr, "OpenClaw CLI timed out after " + timeoutSeconds() + " seconds"));
        }
        if (!success) {
            throw new JeecgBootException(firstText(result.stderr, parsed.errorMessage, "OpenClaw CLI exited with code " + result.exitCode));
        }
        String output = parsed.outputSummary;
        if (!StringUtils.hasText(output)) {
            output = result.stdout;
        }
        appendDelta(output, new StringBuilder(), emitter);
        return output;
    }

    private String readGatewayEventStream(InputStream inputStream, SseEmitter emitter) throws IOException {
        StringBuilder output = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        long deadline = System.currentTimeMillis() + timeoutSeconds() * 1000L;
        byte[] chunk = new byte[4096];
        try (InputStream in = inputStream) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                if (System.currentTimeMillis() > deadline) {
                    throw new RunTimeoutException("OpenClaw Gateway stream timed out after " + timeoutSeconds() + " seconds");
                }
                buffer.append(new String(chunk, 0, read, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace('\r', '\n'));
                drainSseBuffer(buffer, output, emitter);
            }
            String remaining = buffer.toString().trim();
            if (StringUtils.hasText(remaining)) {
                appendDelta(extractDelta(remaining), output, emitter);
            }
        }
        return output.toString();
    }

    private void drainSseBuffer(StringBuilder buffer, StringBuilder output, SseEmitter emitter) throws IOException {
        int index;
        while ((index = buffer.indexOf("\n\n")) >= 0) {
            String event = buffer.substring(0, index).trim();
            buffer.delete(0, index + 2);
            if (!StringUtils.hasText(event)) {
                continue;
            }
            for (String line : event.split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("data:")) {
                    continue;
                }
                String data = trimmed.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    return;
                }
                appendDelta(extractDelta(data), output, emitter);
            }
        }
    }

    private String extractDelta(String data) {
        if (!StringUtils.hasText(data)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(data);
            JSONArray choices = root.getJSONArray("choices");
            if (choices != null && !choices.isEmpty()) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.getJSONObject("delta");
                if (delta != null && StringUtils.hasText(delta.getString("content"))) {
                    return delta.getString("content");
                }
                JSONObject message = choice.getJSONObject("message");
                if (message != null && StringUtils.hasText(message.getString("content"))) {
                    return message.getString("content");
                }
                String text = choice.getString("text");
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            if (StringUtils.hasText(root.getString("delta"))) {
                return root.getString("delta");
            }
            if (StringUtils.hasText(root.getString("text"))) {
                return root.getString("text");
            }
            if (StringUtils.hasText(root.getString("output_text"))) {
                return root.getString("output_text");
            }
        } catch (Exception ignored) {
            return data;
        }
        return null;
    }

    private void appendDelta(String delta, StringBuilder output, SseEmitter emitter) throws IOException {
        if (!StringUtils.hasText(delta)) {
            return;
        }
        output.append(delta);
        sendEvent(emitter, "delta", new JSONObject().fluentPut("text", delta));
    }

    private void finishRun(OpenclawAgentRun run, Date startTime, String status, String output, String error) {
        if (run == null) {
            return;
        }
        Date finishTime = new Date();
        run.setFinishTime(finishTime);
        run.setDurationMs(finishTime.getTime() - startTime.getTime());
        run.setStatus(status);
        run.setOutputSummary(trim(output, MAX_SUMMARY_LENGTH));
        run.setErrorMessage(trim(error, MAX_ERROR_LENGTH));
        run.setErrorType(StringUtils.hasText(error) ? OpenclawConstants.RUN_ERROR_OPENCLAW_ERROR : null);
        updateById(run);
    }

    private void finishFailedStreamRun(OpenclawAgentRun run, Date startTime, String status, String message, OpenclawAgent agent, SseEmitter emitter, String event) {
        try {
            finishRun(run, startTime, status, null, message);
            if (run != null && agent != null) {
                run.setErrorType(OpenclawConstants.RUN_STATUS_TIMEOUT.equals(status) ? OpenclawConstants.RUN_ERROR_GATEWAY_TIMEOUT : OpenclawConstants.RUN_ERROR_OPENCLAW_ERROR);
                persistRunArtifacts(run, agent, null, null, new JeecgBootException(message));
                updateById(run);
            }
            JSONObject payload = run == null ? new JSONObject().fluentPut("status", status).fluentPut("errorMessage", trim(message, MAX_ERROR_LENGTH)) : streamPayload(run, agent, null, message);
            sendEvent(emitter, event, payload);
            if (run != null && agent != null) {
                safeAuditLog("agent_chat_stream", run, agent);
            }
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(sendError);
        }
    }

    private void safeAuditLog(String action, OpenclawAgentRun run, OpenclawAgent agent) {
        try {
            auditRun(action, run, agent);
        } catch (Exception ignored) {
            // SSE runs execute asynchronously; missing request/security context must not alter persisted run status.
        }
    }

    private void auditRun(String action, OpenclawAgentRun run, OpenclawAgent agent) {
        if (OpenclawConstants.RUN_STATUS_SUCCESS.equals(run.getStatus())) {
            auditLogService.logSuccess(action, "agent_run", run.getId(), toResult(run, agent));
        } else {
            auditLogService.logFailure(action, "agent_run", run.getId(), toResult(run, agent));
        }
    }

    private JSONObject streamPayload(OpenclawAgentRun run, OpenclawAgent agent, String text, String error) {
        JSONObject payload = new JSONObject();
        payload.put("runId", run.getId());
        payload.put("agentId", agent == null ? run.getAgentId() : agent.getId());
        payload.put("agentKey", agent == null ? null : agent.getAgentKey());
        payload.put("agentName", run.getAgentName());
        payload.put("conversationId", run.getConversationId());
        payload.put("runType", run.getRunType());
        payload.put("streaming", run.getStreaming());
        payload.put("model", run.getModel());
        payload.put("status", run.getStatus());
        payload.put("inputSummary", run.getInputSummary());
        payload.put("outputSummary", run.getOutputSummary());
        payload.put("fullOutputPath", run.getFullOutputPath());
        payload.put("logPath", run.getLogPath());
        payload.put("errorType", run.getErrorType());
        payload.put("errorMessage", StringUtils.hasText(error) ? trim(error, MAX_ERROR_LENGTH) : run.getErrorMessage());
        payload.put("durationMs", run.getDurationMs());
        if (text != null) {
            payload.put("text", text);
        }
        return payload;
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private OpenclawAgent requireAgent(String agentId) {
        OpenclawAgent agent = agentMapper.selectById(agentId);
        if (agent == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(agent.getDelFlag())) {
            throw new JeecgBootException("Agent does not exist");
        }
        if (!OpenclawConstants.AGENT_STATUS_ENABLED.equals(agent.getStatus())) {
            throw new JeecgBootException("Agent is not enabled: " + agent.getStatus());
        }
        if (!StringUtils.hasText(agent.getAgentKey())) {
            throw new JeecgBootException("Agent key is empty; sync the agent to OpenClaw Gateway first");
        }
        return agent;
    }

    private void precheckRun(OpenclawAgent agent) {
        precheckWorkspace(agent);
        precheckGateway(agent);
    }

    private void precheckWorkspace(OpenclawAgent agent) {
        if (!StringUtils.hasText(agent.getWorkspaceId())) {
            throw new JeecgBootException("Agent workspace is not bound; rematerialize the workspace first");
        }
        OpenclawWorkspace workspace = workspaceMapper.selectById(agent.getWorkspaceId());
        if (workspace == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(workspace.getDelFlag())) {
            throw new JeecgBootException("Agent workspace does not exist; rematerialize the workspace first");
        }
        if (!java.util.Objects.equals(agent.getUserId(), workspace.getUserId())) {
            throw new JeecgBootException("Agent workspace owner does not match the agent owner");
        }
        if (!OpenclawConstants.WORKSPACE_STATUS_ACTIVE.equals(workspace.getStatus())) {
            throw new JeecgBootException("Agent workspace is not active: " + workspace.getStatus());
        }
        if (!StringUtils.hasText(workspace.getPath())) {
            throw new JeecgBootException("Agent workspace path is empty; rematerialize the workspace first");
        }
        Path workspacePath = normalizePath(workspace.getPath(), "Agent workspace path is invalid");
        Path rootPath = normalizePath(OpenclawConstants.WORKSPACE_ROOT, "OpenClaw workspace root is invalid");
        if (!workspacePath.startsWith(rootPath)) {
            throw new JeecgBootException("Agent workspace path is outside the configured workspace root");
        }
        if (Files.isSymbolicLink(workspacePath)) {
            throw new JeecgBootException("Agent workspace path must not be a symbolic link");
        }
        if (!Files.exists(workspacePath) || !Files.isDirectory(workspacePath)) {
            throw new JeecgBootException("Agent workspace directory is missing; rematerialize the workspace first");
        }
    }

    private void precheckGateway(OpenclawAgent agent) {
        if (!StringUtils.hasText(agent.getGatewayId())) {
            throw new JeecgBootException("Agent is not assigned to an OpenClaw Gateway; sync Gateway config first");
        }
        OpenclawGatewayNode gateway = gatewayNodeMapper.selectById(agent.getGatewayId());
        if (gateway == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(gateway.getDelFlag())) {
            throw new JeecgBootException("OpenClaw Gateway node does not exist; sync Gateway config first");
        }
        if (OpenclawConstants.STATUS_DISABLED.equals(gateway.getStatus())) {
            throw new JeecgBootException("OpenClaw Gateway node is disabled");
        }
        if (!StringUtils.hasText(gateway.getBaseUrl())) {
            throw new JeecgBootException("OpenClaw Gateway base URL is empty");
        }
        if (!OpenclawConstants.RUN_STATUS_SUCCESS.equals(gateway.getLastSyncStatus())) {
            throw new JeecgBootException("OpenClaw Gateway config is not synced successfully: " + firstText(gateway.getLastSyncMessage(), gateway.getLastSyncStatus()));
        }
    }

    private Path normalizePath(String value, String errorMessage) {
        try {
            return Paths.get(value).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new JeecgBootException(errorMessage + ": " + e.getMessage());
        }
    }

    private String normalizePrompt(OpenclawAgentRunTestDTO dto) {
        String prompt = dto == null ? null : dto.getPrompt();
        if (!StringUtils.hasText(prompt)) {
            throw new JeecgBootException("Prompt cannot be empty");
        }
        prompt = prompt.trim();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new JeecgBootException("Prompt is too long, max length is " + MAX_PROMPT_LENGTH);
        }
        return prompt;
    }

    private void checkRunQuota(LoginUser user, OpenclawAgent agent) {
        OpenclawUserQuota quota = quotaService.getOrCreateQuota(user);
        if (!OpenclawConstants.STATUS_ENABLED.equals(quota.getStatus())) {
            throw new JeecgBootException("OpenClaw quota is disabled");
        }
        Date dayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long todayRuns = lambdaQuery()
            .eq(OpenclawAgentRun::getUserId, user.getId())
            .ge(OpenclawAgentRun::getCreateTime, dayStart)
            .eq(OpenclawAgentRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count();
        if (quota.getMaxDailyRuns() != null && todayRuns >= quota.getMaxDailyRuns()) {
            throw new JeecgBootException("Daily run quota exceeded");
        }
        if (agent.getMaxDailyRuns() != null) {
            Long agentTodayRuns = lambdaQuery()
                .eq(OpenclawAgentRun::getAgentId, agent.getId())
                .ge(OpenclawAgentRun::getCreateTime, dayStart)
                .eq(OpenclawAgentRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
                .count();
            if (agentTodayRuns >= agent.getMaxDailyRuns()) {
                throw new JeecgBootException("Agent daily run quota exceeded");
            }
        }
        Long runningRuns = lambdaQuery()
            .eq(OpenclawAgentRun::getUserId, user.getId())
            .eq(OpenclawAgentRun::getStatus, OpenclawConstants.RUN_STATUS_RUNNING)
            .eq(OpenclawAgentRun::getDelFlag, OpenclawConstants.DEL_FLAG_NORMAL)
            .count();
        if (quota.getMaxConcurrentRuns() != null && runningRuns >= quota.getMaxConcurrentRuns()) {
            throw new JeecgBootException("Concurrent run quota exceeded");
        }
    }

    private OpenclawAgentRun createRunningRun(LoginUser user, OpenclawAgent agent, String prompt, Date startTime) {
        OpenclawAgentRun run = new OpenclawAgentRun();
        run.setUserId(user.getId());
        run.setUsername(user.getUsername());
        run.setAgentId(agent.getId());
        run.setAgentName(agent.getName());
        run.setRunType(OpenclawConstants.RUN_TYPE_TEST);
        run.setStreaming(0);
        run.setModel("openclaw/" + agent.getAgentKey());
        run.setStatus(OpenclawConstants.RUN_STATUS_RUNNING);
        run.setInputSummary(trim(prompt, MAX_SUMMARY_LENGTH));
        run.setStartTime(startTime);
        run.setDelFlag(OpenclawConstants.DEL_FLAG_NORMAL);
        return run;
    }

    private CliResult executeCli(String agentKey, String prompt) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(StringUtils.hasText(openclawCliPath) ? openclawCliPath : "openclaw");
        command.add("agent");
        command.add("--agent");
        command.add(agentKey);
        command.add("--message");
        command.add(prompt);
        command.add("--json");
        command.add("--timeout");
        command.add(String.valueOf(timeoutSeconds()));

        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(timeoutSeconds() + 5, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RunTimeoutException("OpenClaw CLI timed out after " + timeoutSeconds() + " seconds");
        }
        CliResult result = new CliResult();
        result.exitCode = process.exitValue();
        result.stdout = stdout.get(5, TimeUnit.SECONDS);
        result.stderr = stderr.get(5, TimeUnit.SECONDS);
        return result;
    }

    private long timeoutSeconds() {
        long value = runTimeoutSeconds == null ? 600L : runTimeoutSeconds;
        return Math.max(1L, Math.min(value, 3600L));
    }

    private String normalizeConversationId(OpenclawAgentRunTestDTO dto) {
        String value = dto == null ? null : dto.getConversationId();
        if (!StringUtils.hasText(value)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        value = value.trim();
        if (value.length() > 64) {
            throw new JeecgBootException("Conversation id is too long, max length is 64");
        }
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new JeecgBootException("Conversation id can only contain letters, numbers, underscore and hyphen");
        }
        return value;
    }

    private String gatewayBaseUrl(OpenclawAgent agent) {
        String baseUrl = null;
        if (StringUtils.hasText(agent.getGatewayId())) {
            OpenclawGatewayNode node = gatewayNodeMapper.selectById(agent.getGatewayId());
            if (node != null && Integer.valueOf(OpenclawConstants.DEL_FLAG_NORMAL).equals(node.getDelFlag())) {
                baseUrl = node.getBaseUrl();
            }
        }
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = defaultGatewayBaseUrl;
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new JeecgBootException("OpenClaw Gateway base URL is empty");
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String readStream(InputStream inputStream) {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private void applyCliResult(OpenclawAgentRun run, CliResult result) {
        ParsedOutput parsed = parseOutput(result.stdout);
        boolean timeout = OpenclawConstants.RUN_STATUS_TIMEOUT.equalsIgnoreCase(parsed.status);
        boolean success = result.exitCode == 0 && ("ok".equalsIgnoreCase(parsed.status) || !StringUtils.hasText(parsed.status));
        if (timeout) {
            run.setStatus(OpenclawConstants.RUN_STATUS_TIMEOUT);
            run.setErrorType(OpenclawConstants.RUN_ERROR_GATEWAY_TIMEOUT);
        } else {
            run.setStatus(success ? OpenclawConstants.RUN_STATUS_SUCCESS : OpenclawConstants.RUN_STATUS_FAILED);
            run.setErrorType(success ? null : OpenclawConstants.RUN_ERROR_CLI_FALLBACK_FAILED);
        }
        run.setOutputSummary(trim(parsed.outputSummary, MAX_SUMMARY_LENGTH));
        if (!success || timeout) {
            String error = firstText(result.stderr, parsed.errorMessage, "OpenClaw CLI exited with code " + result.exitCode);
            run.setErrorMessage(trim(error, MAX_ERROR_LENGTH));
        }
    }

    private ParsedOutput parseOutput(String stdout) {
        ParsedOutput parsed = new ParsedOutput();
        if (!StringUtils.hasText(stdout)) {
            return parsed;
        }
        try {
            JSONObject root = JSON.parseObject(stdout);
            parsed.status = root.getString("status");
            parsed.errorMessage = root.getString("error");
            JSONObject result = root.getJSONObject("result");
            if (result != null) {
                JSONArray payloads = result.getJSONArray("payloads");
                if (payloads != null && !payloads.isEmpty()) {
                    List<String> texts = new ArrayList<>();
                    for (Object payload : payloads) {
                        if (payload instanceof JSONObject) {
                            String text = ((JSONObject) payload).getString("text");
                            if (StringUtils.hasText(text)) {
                                texts.add(text);
                            }
                        }
                    }
                    parsed.outputSummary = String.join(System.lineSeparator(), texts);
                }
            }
            if (!StringUtils.hasText(parsed.outputSummary)) {
                parsed.outputSummary = root.getString("summary");
            }
        } catch (Exception e) {
            parsed.outputSummary = stdout;
        }
        return parsed;
    }

    private OpenclawAgentRunResultVO toResult(OpenclawAgentRun run, OpenclawAgent agent) {
        OpenclawAgentRunResultVO vo = new OpenclawAgentRunResultVO();
        vo.setRunId(run.getId());
        vo.setAgentId(agent.getId());
        vo.setAgentKey(agent.getAgentKey());
        vo.setAgentName(agent.getName());
        vo.setConversationId(run.getConversationId());
        vo.setRunType(run.getRunType());
        vo.setStreaming(run.getStreaming());
        vo.setModel(run.getModel());
        vo.setStatus(run.getStatus());
        vo.setInputSummary(run.getInputSummary());
        vo.setOutputSummary(run.getOutputSummary());
        vo.setFullOutputPath(run.getFullOutputPath());
        vo.setLogPath(run.getLogPath());
        vo.setErrorType(run.getErrorType());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setDurationMs(run.getDurationMs());
        return vo;
    }

    private void persistRunArtifacts(OpenclawAgentRun run, OpenclawAgent agent, CliResult cliResult, String output, Exception error) {
        if (run == null || agent == null || !StringUtils.hasText(run.getId())) {
            return;
        }
        try {
            OpenclawWorkspace workspace = workspaceMapper.selectById(agent.getWorkspaceId());
            if (workspace == null || !StringUtils.hasText(workspace.getPath())) {
                return;
            }
            Path workspacePath = normalizePath(workspace.getPath(), "Agent workspace path is invalid");
            Path rootPath = normalizePath(OpenclawConstants.WORKSPACE_ROOT, "OpenClaw workspace root is invalid");
            if (!workspacePath.startsWith(rootPath) || Files.isSymbolicLink(workspacePath)) {
                return;
            }
            Path outputDir = workspacePath.resolve("output").normalize();
            Path logDir = workspacePath.resolve("logs").normalize();
            if (!outputDir.startsWith(workspacePath) || !logDir.startsWith(workspacePath)) {
                return;
            }
            Files.createDirectories(outputDir);
            Files.createDirectories(logDir);

            String fullOutput = firstText(output, cliResult == null ? null : cliResult.stdout);
            if (fullOutput == null) {
                fullOutput = "";
            }
            Path outputFile = outputDir.resolve("run-" + run.getId() + ".txt").normalize();
            Files.writeString(outputFile, fullOutput, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            run.setFullOutputPath(outputFile.toString());

            Path logFile = logDir.resolve("run-" + run.getId() + ".log").normalize();
            Files.writeString(logFile, runLog(run, agent, cliResult, error), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            run.setLogPath(logFile.toString());
        } catch (Exception artifactError) {
            String message = firstText(run.getErrorMessage(), "") + "\nArtifact write failed: " + artifactError.getMessage();
            run.setErrorMessage(trim(message.trim(), MAX_ERROR_LENGTH));
            if (!StringUtils.hasText(run.getErrorType())) {
                run.setErrorType(OpenclawConstants.RUN_ERROR_UNKNOWN);
            }
        }
    }

    private String runLog(OpenclawAgentRun run, OpenclawAgent agent, CliResult cliResult, Exception error) {
        StringBuilder builder = new StringBuilder();
        builder.append("runId=").append(run.getId()).append('\n');
        builder.append("agentId=").append(agent.getId()).append('\n');
        builder.append("agentKey=").append(agent.getAgentKey()).append('\n');
        builder.append("runType=").append(run.getRunType()).append('\n');
        builder.append("status=").append(run.getStatus()).append('\n');
        builder.append("errorType=").append(firstText(run.getErrorType(), "")).append('\n');
        builder.append("startTime=").append(run.getStartTime()).append('\n');
        builder.append("finishTime=").append(run.getFinishTime()).append('\n');
        builder.append("durationMs=").append(run.getDurationMs()).append("\n\n");
        builder.append("input:\n").append(firstText(run.getInputSummary(), "")).append("\n\n");
        if (cliResult != null) {
            builder.append("exitCode=").append(cliResult.exitCode).append("\n\n");
            builder.append("stdout:\n").append(firstText(cliResult.stdout, "")).append("\n\n");
            builder.append("stderr:\n").append(firstText(cliResult.stderr, "")).append("\n\n");
        }
        if (error != null) {
            builder.append("error:\n").append(error.getClass().getName()).append(": ").append(firstText(error.getMessage(), "")).append('\n');
        }
        return builder.toString();
    }

    private String classifyError(Exception e) {
        if (e instanceof RunTimeoutException) {
            return OpenclawConstants.RUN_ERROR_GATEWAY_TIMEOUT;
        }
        if (e instanceof JeecgBootException && StringUtils.hasText(e.getMessage()) && e.getMessage().contains("CLI")) {
            return OpenclawConstants.RUN_ERROR_CLI_FALLBACK_FAILED;
        }
        if (e instanceof JeecgBootException) {
            return OpenclawConstants.RUN_ERROR_OPENCLAW_ERROR;
        }
        return OpenclawConstants.RUN_ERROR_UNKNOWN;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static class CliResult {
        private int exitCode;
        private String stdout;
        private String stderr;
    }

    private static class ParsedOutput {
        private String status;
        private String outputSummary;
        private String errorMessage;
    }

    private static class RunTimeoutException extends RuntimeException {
        private RunTimeoutException(String message) {
            super(message);
        }
    }
}
