<template>
  <div class="openclaw-health">
    <a-space class="toolbar">
      <a-button type="primary" :loading="loading" @click="loadHealth">刷新</a-button>
      <a-tag :color="health?.healthy ? 'green' : 'red'">{{ health?.healthy ? 'HEALTHY' : 'ATTENTION' }}</a-tag>
      <span class="muted">{{ health?.checkedAt || '-' }}</span>
    </a-space>

    <a-row :gutter="[12, 12]">
      <a-col :xs="12" :md="4" v-for="item in summaryItems" :key="item.label">
        <a-card size="small">
          <a-statistic :title="item.label" :value="item.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-card title="核心组件" size="small">
      <a-table size="small" rowKey="name" :columns="componentColumns" :dataSource="health?.components || []" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="目录与配置" size="small">
      <a-table size="small" rowKey="name" :columns="pathColumns" :dataSource="pathRows" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'path'">
            <a-typography-text code>{{ record.path }}</a-typography-text>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="Gateway" size="small">
      <a-table size="small" rowKey="id" :columns="gatewayColumns" :dataSource="health?.gateways || []" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.dataIndex === 'lastSyncStatus'">
            <a-tag :color="statusColor(record.lastSyncStatus === 'success' ? 'UP' : 'WARN')">{{ record.lastSyncStatus || '-' }}</a-tag>
          </template>
          <template v-if="column.dataIndex === 'configPath'">
            <a-typography-text code>{{ record.configPath }}</a-typography-text>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-card title="最近失败运行" size="small">
      <a-empty v-if="!health?.latestFailedRun" />
      <a-descriptions v-else size="small" bordered :column="2">
        <a-descriptions-item label="Run ID">{{ health.latestFailedRun.id }}</a-descriptions-item>
        <a-descriptions-item label="Agent">{{ health.latestFailedRun.agentName }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ health.latestFailedRun.status }}</a-descriptions-item>
        <a-descriptions-item label="时间">{{ health.latestFailedRun.createTime }}</a-descriptions-item>
        <a-descriptions-item label="错误" :span="2">{{ health.latestFailedRun.errorMessage || '-' }}</a-descriptions-item>
      </a-descriptions>
    </a-card>
  </div>
</template>

<script lang="ts" setup name="OpenclawSystemHealth">
  import { computed, onMounted, ref } from 'vue';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { getSystemHealth } from '../api';

  const { createMessage } = useMessage();
  const loading = ref(false);
  const health = ref<any>();

  const componentColumns = [
    { title: '组件', dataIndex: 'name', width: 160 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '耗时(ms)', dataIndex: 'latencyMs', width: 100 },
    { title: '信息', dataIndex: 'message' },
  ];
  const pathColumns = [
    { title: '名称', dataIndex: 'name', width: 180 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '路径', dataIndex: 'path' },
    { title: '信息', dataIndex: 'message', width: 240 },
  ];
  const gatewayColumns = [
    { title: '名称', dataIndex: 'name', width: 220 },
    { title: '节点状态', dataIndex: 'status', width: 100 },
    { title: '同步状态', dataIndex: 'lastSyncStatus', width: 120 },
    { title: '最后同步', dataIndex: 'lastSyncTime', width: 170 },
    { title: '配置文件', dataIndex: 'configPath' },
    { title: '同步信息', dataIndex: 'lastSyncMessage', width: 260 },
  ];

  const summaryItems = computed(() => {
    const s = health.value?.summary || {};
    return [
      { label: 'Agents', value: s.agents || 0 },
      { label: 'Workspaces', value: s.workspaces || 0 },
      { label: 'Skills', value: s.skills || 0 },
      { label: 'Runs', value: s.runs || 0 },
      { label: 'Failed Runs', value: s.failedRuns || 0 },
      { label: 'Gateways', value: s.gateways || 0 },
    ];
  });

  const pathRows = computed(() => {
    const rows = [...(health.value?.paths || [])];
    for (const gateway of health.value?.gateways || []) {
      if (gateway.configFile) {
        rows.push({ ...gateway.configFile, name: `${gateway.name || gateway.id} config` });
      }
    }
    return rows;
  });

  function statusColor(status: string) {
    if (status === 'UP') return 'green';
    if (status === 'WARN') return 'orange';
    return 'red';
  }

  async function loadHealth() {
    loading.value = true;
    try {
      health.value = await getSystemHealth();
    } catch (error: any) {
      createMessage.error(error?.message || '健康检查失败');
    } finally {
      loading.value = false;
    }
  }

  onMounted(loadHealth);
</script>

<style scoped>
  .openclaw-health {
    padding: 12px;
  }

  .toolbar {
    margin-bottom: 12px;
  }

  .muted {
    color: rgba(0, 0, 0, 0.45);
  }

  .openclaw-health :deep(.ant-card) {
    margin-bottom: 12px;
  }
</style>
