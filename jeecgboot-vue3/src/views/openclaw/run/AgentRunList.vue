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
        <a-descriptions-item label="Run Type">{{ detailRecord.runType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Conversation ID">{{ detailRecord.conversationId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Model">{{ detailRecord.model || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Streaming">{{ detailRecord.streaming === 1 ? '是' : '否' }}</a-descriptions-item>
        <a-descriptions-item label="Status">
          <a-tag :color="statusColor(detailRecord.status)">{{ detailRecord.status || '-' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Start Time">{{ detailRecord.startTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Finish Time">{{ detailRecord.finishTime || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Duration (ms)">{{ detailRecord.durationMs ?? '-' }}</a-descriptions-item>
        <a-descriptions-item label="Input Summary">
          <pre class="run-text">{{ detailRecord.inputSummary || '-' }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="Output Summary">
          <pre class="run-text">{{ detailRecord.outputSummary || '-' }}</pre>
        </a-descriptions-item>
        <a-descriptions-item label="Error Message">
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
      { title: 'Run Type', dataIndex: 'runType', width: 100 },
      { title: 'Conversation ID', dataIndex: 'conversationId', width: 180, ellipsis: true },
      { title: 'User ID', dataIndex: 'userId', width: 170 },
      { title: 'Username', dataIndex: 'username', width: 120 },
      { title: 'Model', dataIndex: 'model', width: 160, ellipsis: true },
      { title: 'Status', dataIndex: 'status', width: 120, slots: { customRender: 'status' } },
      { title: 'Input Summary', dataIndex: 'inputSummary', ellipsis: true },
      { title: 'Output Summary', dataIndex: 'outputSummary', ellipsis: true },
      { title: 'Error Message', dataIndex: 'errorMessage', ellipsis: true },
      { title: 'Start Time', dataIndex: 'startTime', width: 170 },
      { title: 'Finish Time', dataIndex: 'finishTime', width: 170 },
      { title: 'Duration (ms)', dataIndex: 'durationMs', width: 120 },
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
    if (status === 'running') {
      return 'blue';
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
