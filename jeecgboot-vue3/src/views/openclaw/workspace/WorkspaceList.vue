<template>
  <BasicTable @register="registerTable" />
</template>

<script lang="ts" setup name="OpenclawWorkspaceList">
  import { BasicTable, useTable } from '/@/components/Table';
  import { listWorkspaces } from '../api';
  import { commonTimeColumns, keywordSearch, readonlyPathColumn } from '../common';

  const [registerTable] = useTable({
    title: '我的工作区',
    api: listWorkspaces,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '工作区名称', dataIndex: 'name', width: 160 },
      { title: '工作区标识', dataIndex: 'workspaceKey', width: 210 },
      { title: '所属用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      readonlyPathColumn(),
      { title: '空间上限(MB)', dataIndex: 'quotaSizeMb', width: 120 },
      { title: '已用(MB)', dataIndex: 'usedSizeMb', width: 100 },
      { title: '状态', dataIndex: 'status', width: 100 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
  });
</script>
