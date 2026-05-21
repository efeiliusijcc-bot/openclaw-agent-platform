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
import org.jeecg.modules.openclaw.entity.OpenclawUserQuota;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentRunMapper;
import org.jeecg.modules.openclaw.service.IOpenclawAgentRunService;
import org.jeecg.modules.openclaw.service.IOpenclawAuditLogService;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawUserQuotaService;
import org.jeecg.modules.openclaw.vo.OpenclawAgentRunResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private IOpenclawPermissionService permissionService;
    @Autowired
    private IOpenclawUserQuotaService quotaService;
    @Autowired
    private IOpenclawAuditLogService auditLogService;

    @Value("${openclaw.run.cli-path:${OPENCLAW_CLI_PATH:openclaw}}")
    private String openclawCliPath;

    @Value("${openclaw.run.timeout-seconds:${OPENCLAW_RUN_TIMEOUT_SECONDS:600}}")
    private Long runTimeoutSeconds;

    @Override
    public OpenclawAgentRunResultVO runTest(String agentId, OpenclawAgentRunTestDTO dto) {
        LoginUser user = permissionService.currentUser();
        OpenclawAgent agent = requireAgent(agentId);
        permissionService.checkOwnerOrAdmin(agent.getUserId());
        String prompt = normalizePrompt(dto);
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
            updateById(run);
            auditLogService.log("agent_run_test", "agent_run", run.getId(), toResult(run, agent));
            return toResult(run, agent);
        } catch (Exception e) {
            Date finishTime = new Date();
            run.setFinishTime(finishTime);
            run.setDurationMs(finishTime.getTime() - startTime.getTime());
            run.setStatus("failed");
            run.setErrorMessage(trim(e.getMessage(), MAX_ERROR_LENGTH));
            updateById(run);
            auditLogService.log("agent_run_test", "agent_run", run.getId(), toResult(run, agent));
            return toResult(run, agent);
        }
    }

    private OpenclawAgent requireAgent(String agentId) {
        OpenclawAgent agent = agentMapper.selectById(agentId);
        if (agent == null || Integer.valueOf(OpenclawConstants.DEL_FLAG_DELETED).equals(agent.getDelFlag())) {
            throw new JeecgBootException("Agent does not exist");
        }
        if (OpenclawConstants.AGENT_STATUS_DISABLED.equals(agent.getStatus())) {
            throw new JeecgBootException("Agent is disabled");
        }
        if (!StringUtils.hasText(agent.getAgentKey())) {
            throw new JeecgBootException("Agent key is empty; sync the agent to OpenClaw Gateway first");
        }
        return agent;
    }

    private String normalizePrompt(OpenclawAgentRunTestDTO dto) {
        String prompt = dto == null ? null : dto.getPrompt();
        if (!StringUtils.hasText(prompt)) {
            throw new JeecgBootException("Prompt cannot be empty");
        }
        prompt = prompt.trim();
        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new JeecgBootException("Prompt is too long");
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
            .eq(OpenclawAgentRun::getStatus, "running")
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
        run.setStatus("running");
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
            throw new JeecgBootException("OpenClaw CLI timed out after " + timeoutSeconds() + " seconds");
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

    private String readStream(InputStream inputStream) {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private void applyCliResult(OpenclawAgentRun run, CliResult result) {
        ParsedOutput parsed = parseOutput(result.stdout);
        boolean success = result.exitCode == 0 && ("ok".equalsIgnoreCase(parsed.status) || !StringUtils.hasText(parsed.status));
        run.setStatus(success ? "success" : "failed");
        run.setOutputSummary(trim(parsed.outputSummary, MAX_SUMMARY_LENGTH));
        if (!success) {
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
        vo.setStatus(run.getStatus());
        vo.setInputSummary(run.getInputSummary());
        vo.setOutputSummary(run.getOutputSummary());
        vo.setErrorMessage(run.getErrorMessage());
        vo.setDurationMs(run.getDurationMs());
        return vo;
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
}
