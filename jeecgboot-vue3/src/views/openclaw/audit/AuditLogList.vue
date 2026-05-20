<template>
  <BasicTable @register="registerTable" />
</template>

<script lang="ts" setup name="OpenclawAuditLogList">
  import { BasicTable, useTable } from '/@/components/Table';
  import { listAuditLogs } from '../api';
  import { keywordSearch } from '../common';

  const [registerTable] = useTable({
    title: '系统审计日志',
    api: listAuditLogs,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      { title: '操作', dataIndex: 'action', width: 150 },
      { title: '对象类型', dataIndex: 'targetType', width: 120 },
      { title: '对象ID', dataIndex: 'targetId', width: 170 },
      { title: 'IP', dataIndex: 'ip', width: 140 },
      { title: 'User Agent', dataIndex: 'userAgent', ellipsis: true },
      { title: '详情', dataIndex: 'detailJson', ellipsis: true },
      { title: '时间', dataIndex: 'createTime', width: 170 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('action', '操作') },
  });
</script>
