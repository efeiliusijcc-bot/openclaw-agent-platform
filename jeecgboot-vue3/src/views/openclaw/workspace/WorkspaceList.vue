<template>
  <BasicTable @register="registerTable">
    <template #action="{ record }">
      <TableAction :actions="actions(record)" />
    </template>
  </BasicTable>
</template>

<script lang="ts" setup name="OpenclawWorkspaceList">
  import { h } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { checkWorkspaceHealth, listWorkspaces, rematerializeWorkspace } from '../api';
  import { commonTimeColumns, keywordSearch, readonlyPathColumn } from '../common';

  const { createMessage } = useMessage();

  const [registerTable, { reload }] = useTable({
    title: 'Workspaces',
    api: listWorkspaces,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Workspace Name', dataIndex: 'name', width: 160 },
      { title: 'Workspace Key', dataIndex: 'workspaceKey', width: 210 },
      { title: 'Owner User ID', dataIndex: 'userId', width: 170 },
      { title: 'Username', dataIndex: 'username', width: 120 },
      readonlyPathColumn('Path'),
      { title: 'Quota (MB)', dataIndex: 'quotaSizeMb', width: 120 },
      { title: 'Used (MB)', dataIndex: 'usedSizeMb', width: 100 },
      { title: 'Status', dataIndex: 'status', width: 100 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: {
      title: 'Actions',
      dataIndex: 'action',
      width: 190,
      fixed: 'right',
      slots: { customRender: 'action' },
    },
  });

  function actions(record) {
    return [
      { label: 'Health Check', auth: 'openclaw:workspace:health', onClick: () => openHealthCheck(record) },
      {
        label: 'Rematerialize',
        auth: 'openclaw:workspace:rematerialize',
        popConfirm: {
          title: 'Regenerate base files for this workspace?',
          confirm: () => rematerialize(record),
        },
      },
    ];
  }

  async function openHealthCheck(record) {
    const result = unwrap(await checkWorkspaceHealth(record.id));
    showHealthResult(result);
  }

  async function rematerialize(record) {
    const result = unwrap(await rematerializeWorkspace(record.id));
    if (result?.healthy) {
      createMessage.success('Workspace rematerialized and health check passed');
    } else {
      createMessage.warning('Workspace rematerialized, but health check still has issues');
    }
    showHealthResult(result);
    reload();
  }

  function unwrap(response) {
    return response?.result || response;
  }

  function showHealthResult(result) {
    const errors = result?.errors || [];
    const warnings = result?.warnings || [];
    const checkedItems = result?.checkedItems || [];
    Modal.info({
      title: result?.healthy ? 'Workspace health check passed' : 'Workspace health check failed',
      width: 720,
      content: h('div', [
        h('p', `Path: ${result?.path || '-'}`),
        h('p', `Status: ${result?.status || '-'}`),
        h('p', `Checked: ${checkedItems.length ? checkedItems.join(', ') : '-'}`),
        errors.length ? h('p', { style: 'color: #cf1322;' }, `Errors: ${errors.join('; ')}`) : null,
        warnings.length ? h('p', { style: 'color: #d48806;' }, `Warnings: ${warnings.join('; ')}`) : null,
      ]),
    });
  }
</script>
