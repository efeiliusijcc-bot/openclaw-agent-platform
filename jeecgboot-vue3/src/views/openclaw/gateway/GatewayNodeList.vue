<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:gateway:add'" @click="openAdd">登记 Gateway</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>

    <a-modal v-model:open="visible" :title="form.id ? '编辑 Gateway' : '登记 Gateway'" @ok="submit" destroyOnClose width="720px">
      <a-form :model="form" layout="vertical">
        <a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="地址" required><a-input v-model:value="form.baseUrl" placeholder="https://gateway.example.com" /></a-form-item>
        <a-form-item label="状态"><a-select v-model:value="form.status" :options="statusOptions" /></a-form-item>
        <a-form-item label="配置文件路径">
          <a-input v-model:value="form.configPath" placeholder="/data/openclaw-gateway/.openclaw/jeecg-agents.json" />
        </a-form-item>
        <a-form-item label="Workspace 根目录">
          <a-input v-model:value="form.workspaceRoot" placeholder="/data/openclaw-platform/workspaces" />
        </a-form-item>
        <a-form-item label="最大 Agent"><a-input-number v-model:value="form.maxAgents" :min="0" style="width: 100%" /></a-form-item>
        <a-form-item label="最大并发"><a-input-number v-model:value="form.maxConcurrentRuns" :min="0" style="width: 100%" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="previewVisible" title="Gateway 配置预览" :footer="null" destroyOnClose width="900px">
      <a-textarea v-model:value="previewContent" :rows="22" readonly />
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawGatewayNodeList">
  import { reactive, ref } from 'vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { addGateway, deleteGateway, editGateway, listGateways, previewGatewayConfig, syncGatewayConfig } from '../api';
  import { keywordSearch } from '../common';

  const { createMessage } = useMessage();
  const visible = ref(false);
  const previewVisible = ref(false);
  const previewContent = ref('');
  const form = reactive<any>({});
  const statusOptions = [
    { label: 'online', value: 'online' },
    { label: 'offline', value: 'offline' },
    { label: 'disabled', value: 'disabled' },
  ];
  const [registerTable, { reload }] = useTable({
    title: 'Gateway 节点管理',
    api: listGateways,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '名称', dataIndex: 'name', width: 150 },
      { title: '地址', dataIndex: 'baseUrl', ellipsis: true },
      { title: '状态', dataIndex: 'status', width: 100 },
      { title: '配置文件', dataIndex: 'configPath', width: 280, ellipsis: true },
      { title: 'Workspace 根目录', dataIndex: 'workspaceRoot', width: 260, ellipsis: true },
      { title: '最大 Agent', dataIndex: 'maxAgents', width: 110 },
      { title: '当前 Agent', dataIndex: 'currentAgents', width: 110 },
      { title: '同步状态', dataIndex: 'lastSyncStatus', width: 110 },
      { title: '最近同步', dataIndex: 'lastSyncTime', width: 170 },
      { title: '同步信息', dataIndex: 'lastSyncMessage', width: 240, ellipsis: true },
      {
        title: '需重启',
        dataIndex: 'restartRequired',
        width: 90,
        customRender: ({ text }) => (Number(text) === 1 ? '是' : '否'),
      },
      { title: '最后心跳', dataIndex: 'lastHeartbeat', width: 170 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 260, fixed: 'right' },
  });

  function reset(data: any = {}) {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(
      form,
      {
        status: 'offline',
        maxAgents: 0,
        maxConcurrentRuns: 0,
        configPath: '/data/openclaw-gateway/.openclaw/jeecg-agents.json',
        workspaceRoot: '/data/openclaw-platform/workspaces',
        restartRequired: 1,
      },
      data
    );
  }

  function openAdd() {
    reset();
    visible.value = true;
  }

  function openEdit(record) {
    reset(record);
    visible.value = true;
  }

  async function submit() {
    await (form.id ? editGateway(form) : addGateway(form));
    visible.value = false;
    reload();
  }

  async function openPreview(record) {
    const result: any = await previewGatewayConfig(record.id);
    previewContent.value = result?.content || result?.result?.content || '';
    previewVisible.value = true;
  }

  async function syncGateway(record) {
    const result: any = await syncGatewayConfig(record.id);
    const message = result?.message || result?.result?.message || '配置已写入，当前版本需要手动重启 OpenClaw Gateway';
    createMessage.success(message);
    reload();
  }

  function actions(record) {
    return [
      { label: '预览', auth: 'openclaw:gateway:list', onClick: () => openPreview(record) },
      { label: '同步', auth: 'openclaw:gateway:sync', onClick: () => syncGateway(record) },
      { label: '编辑', auth: 'openclaw:gateway:edit', onClick: () => openEdit(record) },
      {
        label: '删除',
        color: 'error',
        auth: 'openclaw:gateway:disable',
        popConfirm: { title: '确认逻辑删除该节点？', confirm: async () => (await deleteGateway({ id: record.id }), reload()) },
      },
    ];
  }
</script>
