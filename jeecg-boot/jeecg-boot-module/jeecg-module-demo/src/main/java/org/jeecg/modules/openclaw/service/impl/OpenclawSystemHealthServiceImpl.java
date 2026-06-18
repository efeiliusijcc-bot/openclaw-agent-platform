package org.jeecg.modules.openclaw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.openclaw.constant.OpenclawConstants;
import org.jeecg.modules.openclaw.entity.OpenclawAgent;
import org.jeecg.modules.openclaw.entity.OpenclawAgentRun;
import org.jeecg.modules.openclaw.entity.OpenclawGatewayNode;
import org.jeecg.modules.openclaw.entity.OpenclawSkill;
import org.jeecg.modules.openclaw.entity.OpenclawWorkspace;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawAgentRunMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawGatewayNodeMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawSkillMapper;
import org.jeecg.modules.openclaw.mapper.OpenclawWorkspaceMapper;
import org.jeecg.modules.openclaw.service.IOpenclawPermissionService;
import org.jeecg.modules.openclaw.service.IOpenclawSystemHealthService;
import org.jeecg.modules.openclaw.vo.OpenclawSystemHealthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

@Service
public class OpenclawSystemHealthServiceImpl implements IOpenclawSystemHealthService {
    private static final String STATUS_UP = "UP";
    private static final String STATUS_WARN = "WARN";
    private static final String STATUS_DOWN = "DOWN";

    @Autowired
    private IOpenclawPermissionService permissionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private OpenclawAgentMapper agentMapper;
    @Autowired
    private OpenclawWorkspaceMapper workspaceMapper;
    @Autowired
    private OpenclawSkillMapper skillMapper;
    @Autowired
    private OpenclawAgentRunMapper runMapper;
    @Autowired
    private OpenclawGatewayNodeMapper gatewayNodeMapper;

    @Value("${openclaw.workspace.root:" + OpenclawConstants.WORKSPACE_ROOT + "}")
    private String workspaceRoot;

    @Value("${openclaw.skill.root:" + OpenclawConstants.SKILL_ROOT + "}")
    private String skillRoot;

    @Override
    public OpenclawSystemHealthVO check() {
        LoginUser user = permissionService.currentUser();
        if (!permissionService.isAdmin(user)) {
            throw new JeecgBootException("Only OpenClaw administrators can view system health");
        }

        OpenclawSystemHealthVO health = new OpenclawSystemHealthVO();
        health.setCheckedAt(new Date());
        health.getComponents().add(checkDatabase());
        health.getComponents().add(checkRedis());
        health.getPaths().add(pathHealth("workspaceRoot", workspaceRoot, true));
        health.getPaths().add(pathHealth("skillRoot", skillRoot, true));
        health.setGateways(gatewayHealth());
        health.setLatestFailedRun(latestFailedRun());
        fillSummary(health.getSummary(), health.getGateways());
        health.setHealthy(isHealthy(health));
        return health;
    }

    private OpenclawSystemHealthVO.ComponentHealth checkDatabase() {
        long started = System.currentTimeMillis();
        OpenclawSystemHealthVO.ComponentHealth item = component("database");
        try {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            item.setStatus(Integer.valueOf(1).equals(value) ? STATUS_UP : STATUS_DOWN);
            item.setMessage("select 1 returned " + value);
        } catch (Exception e) {
            item.setStatus(STATUS_DOWN);
            item.setMessage(trim(e.getMessage()));
        }
        item.setLatencyMs(System.currentTimeMillis() - started);
        return item;
    }

    private OpenclawSystemHealthVO.ComponentHealth checkRedis() {
        long started = System.currentTimeMillis();
        OpenclawSystemHealthVO.ComponentHealth item = component("redis");
        if (redisTemplate == null) {
            item.setStatus(STATUS_WARN);
            item.setMessage("RedisTemplate is not available");
            item.setLatencyMs(0L);
            return item;
        }
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            item.setStatus(StringUtils.hasText(pong) ? STATUS_UP : STATUS_WARN);
            item.setMessage(StringUtils.hasText(pong) ? pong : "Redis ping returned empty response");
        } catch (Exception e) {
            item.setStatus(STATUS_DOWN);
            item.setMessage(trim(e.getMessage()));
        }
        item.setLatencyMs(System.currentTimeMillis() - started);
        return item;
    }

    private List<OpenclawSystemHealthVO.GatewayHealth> gatewayHealth() {
        return gatewayNodeMapper.selectList(new QueryWrapper<OpenclawGatewayNode>()
                .eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL)
                .orderByDesc("update_time", "create_time"))
            .stream()
            .map(this::toGatewayHealth)
            .toList();
    }

    private OpenclawSystemHealthVO.GatewayHealth toGatewayHealth(OpenclawGatewayNode node) {
        OpenclawSystemHealthVO.GatewayHealth item = new OpenclawSystemHealthVO.GatewayHealth();
        item.setId(node.getId());
        item.setName(node.getName());
        item.setStatus(node.getStatus());
        item.setBaseUrl(node.getBaseUrl());
        item.setConfigPath(configPath(node));
        item.setCurrentAgents(node.getCurrentAgents());
        item.setMaxAgents(node.getMaxAgents());
        item.setCurrentRunning(node.getCurrentRunning());
        item.setMaxConcurrentRuns(node.getMaxConcurrentRuns());
        item.setLastSyncStatus(node.getLastSyncStatus());
        item.setLastSyncMessage(node.getLastSyncMessage());
        item.setLastSyncTime(node.getLastSyncTime());
        item.setRestartRequired(Integer.valueOf(1).equals(node.getRestartRequired()));
        item.setConfigFile(pathHealth("gatewayConfig:" + node.getId(), configPath(node), false));
        fillGatewayStatus(item);
        return item;
    }

    private void fillGatewayStatus(OpenclawSystemHealthVO.GatewayHealth item) {
        if (OpenclawConstants.GATEWAY_STATUS_DISABLED.equals(item.getStatus())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway node is disabled");
            return;
        }
        if (OpenclawConstants.GATEWAY_STATUS_OFFLINE.equals(item.getStatus())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway node is offline");
            return;
        }
        if (!OpenclawConstants.GATEWAY_STATUS_ONLINE.equals(item.getStatus())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway node status is unknown: " + firstText(item.getStatus(), "empty"));
            return;
        }
        if (!StringUtils.hasText(item.getBaseUrl())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway base URL is empty");
            return;
        }
        if (item.getConfigFile() != null && STATUS_DOWN.equals(item.getConfigFile().getStatus())) {
            item.setHealthStatus(STATUS_DOWN);
            item.setHealthMessage("Gateway config file is not available: " + item.getConfigFile().getMessage());
            return;
        }
        if (!OpenclawConstants.RUN_STATUS_SUCCESS.equals(item.getLastSyncStatus())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Last sync is not successful: " + firstText(item.getLastSyncMessage(), item.getLastSyncStatus(), "never synced"));
            return;
        }
        if (Boolean.TRUE.equals(item.getRestartRequired())) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway restart is required after config sync");
            return;
        }
        if (item.getMaxAgents() != null
            && item.getMaxAgents() > 0
            && safeCount(item.getCurrentAgents()) > item.getMaxAgents()) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway agent capacity exceeded: " + safeCount(item.getCurrentAgents()) + "/" + item.getMaxAgents());
            return;
        }
        if (item.getMaxConcurrentRuns() != null
            && item.getMaxConcurrentRuns() > 0
            && safeCount(item.getCurrentRunning()) >= item.getMaxConcurrentRuns()) {
            item.setHealthStatus(STATUS_WARN);
            item.setHealthMessage("Gateway concurrent run capacity is full: " + safeCount(item.getCurrentRunning()) + "/" + item.getMaxConcurrentRuns());
            return;
        }
        item.setHealthStatus(STATUS_UP);
        item.setHealthMessage("Gateway config is synced");
    }

    private OpenclawAgentRun latestFailedRun() {
        return runMapper.selectOne(new QueryWrapper<OpenclawAgentRun>()
            .eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL)
            .in("status", OpenclawConstants.RUN_STATUS_FAILED, OpenclawConstants.RUN_STATUS_TIMEOUT)
            .orderByDesc("create_time")
            .last("limit 1"));
    }

    private void fillSummary(OpenclawSystemHealthVO.Summary summary, List<OpenclawSystemHealthVO.GatewayHealth> gateways) {
        summary.setAgents(agentMapper.selectCount(normalQuery(OpenclawAgent.class)));
        summary.setWorkspaces(workspaceMapper.selectCount(normalQuery(OpenclawWorkspace.class)));
        summary.setSkills(skillMapper.selectCount(normalQuery(OpenclawSkill.class)));
        summary.setRuns(runMapper.selectCount(normalQuery(OpenclawAgentRun.class)));
        summary.setFailedRuns(runMapper.selectCount(new QueryWrapper<OpenclawAgentRun>()
            .eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL)
            .in("status", OpenclawConstants.RUN_STATUS_FAILED, OpenclawConstants.RUN_STATUS_TIMEOUT)));
        summary.setGateways(gatewayNodeMapper.selectCount(normalQuery(OpenclawGatewayNode.class)));
        summary.setErrorAgents(agentMapper.selectCount(new QueryWrapper<OpenclawAgent>()
            .eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL)
            .eq("status", OpenclawConstants.AGENT_STATUS_ERROR)));
        summary.setErrorWorkspaces(workspaceMapper.selectCount(new QueryWrapper<OpenclawWorkspace>()
            .eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL)
            .eq("status", OpenclawConstants.WORKSPACE_STATUS_ERROR)));
        summary.setGatewayAttention(gateways.stream()
            .filter(item -> !STATUS_UP.equals(item.getHealthStatus()))
            .count());
    }

    private <T> QueryWrapper<T> normalQuery(Class<T> ignored) {
        return new QueryWrapper<T>().eq("del_flag", OpenclawConstants.DEL_FLAG_NORMAL);
    }

    private OpenclawSystemHealthVO.PathHealth pathHealth(String name, String value, boolean directoryExpected) {
        OpenclawSystemHealthVO.PathHealth item = new OpenclawSystemHealthVO.PathHealth();
        item.setName(name);
        item.setPath(value);
        if (!StringUtils.hasText(value)) {
            item.setStatus(STATUS_DOWN);
            item.setExists(false);
            item.setMessage("Path is empty");
            return item;
        }
        try {
            Path path = Paths.get(value).toAbsolutePath().normalize();
            boolean exists = Files.exists(path);
            boolean directory = exists && Files.isDirectory(path);
            boolean symbolicLink = exists && Files.isSymbolicLink(path);
            boolean readable = exists && Files.isReadable(path);
            boolean writable = exists && Files.isWritable(path);
            item.setPath(path.toString());
            item.setExists(exists);
            item.setDirectory(directory);
            item.setSymbolicLink(symbolicLink);
            item.setReadable(readable);
            item.setWritable(writable);
            if (!exists) {
                item.setStatus(STATUS_DOWN);
                item.setMessage("Path does not exist");
            } else if (symbolicLink) {
                item.setStatus(STATUS_DOWN);
                item.setMessage("Symbolic links are not allowed");
            } else if (directoryExpected && !directory) {
                item.setStatus(STATUS_DOWN);
                item.setMessage("Directory expected");
            } else if (!readable || !writable) {
                item.setStatus(STATUS_WARN);
                item.setMessage("Path is not fully readable and writable");
            } else {
                item.setStatus(STATUS_UP);
                item.setMessage("OK");
            }
        } catch (Exception e) {
            item.setStatus(STATUS_DOWN);
            item.setMessage(trim(e.getMessage()));
        }
        return item;
    }

    private OpenclawSystemHealthVO.ComponentHealth component(String name) {
        OpenclawSystemHealthVO.ComponentHealth item = new OpenclawSystemHealthVO.ComponentHealth();
        item.setName(name);
        return item;
    }

    private boolean isHealthy(OpenclawSystemHealthVO health) {
        boolean componentsUp = health.getComponents().stream().noneMatch(item -> STATUS_DOWN.equals(item.getStatus()));
        boolean pathsUp = health.getPaths().stream().noneMatch(item -> STATUS_DOWN.equals(item.getStatus()));
        boolean configsUp = health.getGateways().stream()
            .map(OpenclawSystemHealthVO.GatewayHealth::getConfigFile)
            .noneMatch(item -> item != null && STATUS_DOWN.equals(item.getStatus()));
        boolean gatewaysUp = health.getGateways().stream()
            .noneMatch(item -> STATUS_DOWN.equals(item.getHealthStatus()));
        return componentsUp && pathsUp && configsUp && gatewaysUp;
    }

    private String configPath(OpenclawGatewayNode node) {
        return StringUtils.hasText(node.getConfigPath()) ? node.getConfigPath() : OpenclawConstants.DEFAULT_GATEWAY_CONFIG_PATH;
    }

    private String trim(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
