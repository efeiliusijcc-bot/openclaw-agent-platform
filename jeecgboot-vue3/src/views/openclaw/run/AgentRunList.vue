<template>
  <BasicTable @register="registerTable" />
</template>

<script lang="ts" setup name="OpenclawAgentRunList">
  import { BasicTable, useTable } from '/@/components/Table';
  import { listRuns } from '../api';
  import { keywordSearch } from '../common';

  const [registerTable] = useTable({
    title: 'Agent 运行记录',
    api: listRuns,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Agent', dataIndex: 'agentName', width: 160 },
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      { title: '状态', dataIndex: 'status', width: 120 },
      { title: '输入摘要', dataIndex: 'inputSummary', ellipsis: true },
      { title: '输出摘要', dataIndex: 'outputSummary', ellipsis: true },
      { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true },
      { title: '开始时间', dataIndex: 'startTime', width: 170 },
      { title: '结束时间', dataIndex: 'finishTime', width: 170 },
      { title: '耗时(ms)', dataIndex: 'durationMs', width: 100 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('agentName', 'Agent') },
  });
</script>
