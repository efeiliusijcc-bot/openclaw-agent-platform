<template>
  <div>
    <BasicTable @register="registerTable" :searchInfo="searchInfo">
      <template #status="{ text }">
        <a-tag :color="statusColor(text)">{{ text || '-' }}</a-tag>
      </template>
      <template #action="{ record }">
        <TableAction :actions="[{ label: '查看详情', onClick: () => openDetail(record) }]" />
      </template>
    </BasicTable>

    <a-modal v-model:open="detailVisible" title="运行详情" :footer="null" width="860px" destroyOnClose>
      <a-descriptions v-if="detailRecord" bordered :column="1" size="small">
        <a-descriptions-item label="Agent">{{ detailRecord.agentName }}</a-descriptions-item>
        <a-descriptions-item label="运行类型">{{ detailRecord.runType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="会话 ID">{{ detailRecord.conversationId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="模型">{{ detailRecord.model || '-' }}</a-descriptions-item>
        <a-descriptions-item label="流式输出">{{ detailRecord.streaming === 1 ? '是' : '否' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="statusColor(detailRecord.status)">{{ detailRecord.status || '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ detailRecord.startTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="结束时间">{{ detailRecord.finishTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="耗时(ms)">{{ detailRecord.durationMs ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="完整输出文件">{{ detailRecord.fullOutputPath || '-' }}</a-descriptions-item>
        <a-descriptions-item label="运行日志文件">{{ detailRecord.logPath || '-' }}</a-descriptions-item>
        <a-descriptions-item label="错误类型">{{ detailRecord.errorType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="输入摘要">
          <pre class="run-text">{{ detailRecord.inputSummary || '-' }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="输出摘要">
          <pre class="run-text">{{ detailRecord.outputSummary || '-' }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="错误信息">
          <pre class="run-text run-error">{{ detailRecord.errorMessage || '-' }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawAgentRunList">
  import { reactive, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { listRuns } from '../api';
  import { keywordSearch } from '../common';

  const route = useRoute();
  const searchInfo = reactive<any>({
    agentId: route.query.agentId,
  });
  const detailVisible = ref(false);
  const detailRecord = ref<any>();

  const [registerTable] = useTable({
    title: 'Agent 运行记录',
    api: listRuns,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Agent', dataIndex: 'agentName', width: 160 },
      { title: '运行类型', dataIndex: 'runType', width: 100 },
      { title: '会话 ID', dataIndex: 'conversationId', width: 180, ellipsis: true },
      { title: 'User ID', dataIndex: 'userId', width: 170 },
      { title: 'Username', dataIndex: 'username', width: 120 },
      { title: '模型', dataIndex: 'model', width: 160, ellipsis: true },
      { title: '状态', dataIndex: 'status', width: 120, slots: { customRender: 'status' } },
      { title: '错误类型', dataIndex: 'errorType', width: 150, ellipsis: true },
      { title: '输入摘要', dataIndex: 'inputSummary', ellipsis: true },
      { title: '输出摘要', dataIndex: 'outputSummary', ellipsis: true },
      { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true },
      { title: '开始时间', dataIndex: 'startTime', width: 170 },
      { title: '结束时间', dataIndex: 'finishTime', width: 170 },
      { title: '耗时(ms)', dataIndex: 'durationMs', width: 120 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('agentName', 'Agent') },
    actionColumn: { title: '操作', width: 120, fixed: 'right', slots: { customRender: 'action' } },
  });

  function openDetail(record: any) {
    detailRecord.value = record;
    detailVisible.value = true;
  }

  function statusColor(status: string) {
    if (status === 'success') {
      return 'green';
    }
    if (status === 'failed') {
      return 'red';
    }
    if (status === 'timeout') {
      return 'orange';
    }
    if (status === 'queued') {
      return 'cyan';
    }
    if (status === 'running') {
      return 'blue';
    }
    if (status === 'cancelled') {
      return 'default';
    }
    return 'default';
  }
</script>

<style scoped>
  .run-text {
    margin: 0;
    max-height: 280px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .run-error {
    color: #cf1322;
  }
</style>
