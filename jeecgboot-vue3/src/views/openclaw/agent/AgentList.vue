<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:agent:add'" @click="openAdd">新增 Agent</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>
    <a-modal v-model:open="visible" :title="form.id ? '编辑 Agent' : '新增 Agent'" @ok="submit" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="Agent 名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
        <a-form-item label="最大 Skill 数"><a-input-number v-model:value="form.maxSkills" :min="1" style="width:100%" /></a-form-item>
        <a-form-item label="每日运行次数"><a-input-number v-model:value="form.maxDailyRuns" :min="1" style="width:100%" /></a-form-item>
        <a-form-item label="配置 JSON"><a-textarea v-model:value="form.configJson" :rows="4" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawAgentList">
  import { reactive, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { addAgent, deleteAgent, disableAgent, editAgent, listAgents } from '../api';
  import { commonTimeColumns, keywordSearch } from '../common';

  const { createMessage } = useMessage();
  const visible = ref(false);
  const form = reactive<any>({});
  const [registerTable, { reload }] = useTable({
    title: '我的 Agent',
    api: listAgents,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Agent 名称', dataIndex: 'name', width: 160 },
      { title: 'Agent 标识', dataIndex: 'agentKey', width: 220 },
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      { title: '工作区ID', dataIndex: 'workspaceId', width: 170 },
      { title: '状态', dataIndex: 'status', width: 100 },
      { title: '最大 Skill', dataIndex: 'maxSkills', width: 100 },
      { title: '每日运行', dataIndex: 'maxDailyRuns', width: 100 },
      { title: 'Gateway', dataIndex: 'gatewayId', width: 160 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 210, fixed: 'right' },
  });

  function reset(data: any = {}) {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(form, { maxSkills: 10, maxDailyRuns: 100, configJson: '{}' }, data);
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
    if (!form.name) {
      createMessage.warning('请输入 Agent 名称');
      return;
    }
    await (form.id ? editAgent(form) : addAgent(form));
    visible.value = false;
    reload();
  }
  function actions(record) {
    return [
      { label: '编辑', auth: 'openclaw:agent:edit', onClick: () => openEdit(record) },
      {
        label: '删除',
        color: 'error',
        auth: 'openclaw:agent:delete',
        popConfirm: { title: '确认逻辑删除该 Agent？', confirm: async () => (await deleteAgent({ id: record.id }), reload()) },
      },
      {
        label: '禁用',
        auth: 'openclaw:agent:disable',
        onClick: () => Modal.confirm({ title: '确认禁用该 Agent？', onOk: async () => (await disableAgent({ id: record.id }), reload()) }),
      },
    ];
  }
</script>
