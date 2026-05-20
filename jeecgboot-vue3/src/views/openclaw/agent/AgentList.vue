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
    <a-modal v-model:open="bindVisible" title="绑定 Skill" :footer="null" width="720px" destroyOnClose>
      <a-space style="width: 100%; margin-bottom: 12px">
        <a-select
          v-model:value="bindSkillId"
          show-search
          allow-clear
          optionFilterProp="label"
          :options="skillOptions"
          placeholder="选择 Skill"
          style="width: 420px"
        />
        <a-button type="primary" v-auth="'openclaw:agent:bindSkill'" @click="submitBind">绑定</a-button>
      </a-space>
      <a-table size="small" rowKey="id" :columns="bindingColumns" :dataSource="bindingRows" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" danger v-auth="'openclaw:agent:unbindSkill'" @click="removeBinding(record)">解绑</a-button>
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawAgentList">
  import { reactive, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { useRouter } from 'vue-router';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { addAgent, bindSkill, deleteAgent, disableAgent, editAgent, listAgentSkills, listAgents, listSkills, unbindSkill } from '../api';
  import { commonTimeColumns, keywordSearch } from '../common';

  const { createMessage } = useMessage();
  const router = useRouter();
  const visible = ref(false);
  const bindVisible = ref(false);
  const currentAgent = ref<any>();
  const bindSkillId = ref<string>();
  const skillOptions = ref<any[]>([]);
  const bindingRows = ref<any[]>([]);
  const form = reactive<any>({});
  const bindingColumns = [
    { title: 'Skill', dataIndex: 'skillName' },
    { title: 'Skill ID', dataIndex: 'skillId', width: 220 },
    { title: '状态', dataIndex: 'enabled', width: 90 },
    { title: '创建时间', dataIndex: 'createTime', width: 170 },
    { title: '操作', key: 'action', width: 90 },
  ];
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
      { label: '绑定 Skill', auth: 'openclaw:agent:bindSkill', onClick: () => openBind(record) },
      { label: '运行记录', auth: 'openclaw:run:list', onClick: () => router.push({ path: '/openclaw/run', query: { agentId: record.id } }) },
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
  async function openBind(record) {
    currentAgent.value = record;
    bindSkillId.value = undefined;
    bindVisible.value = true;
    await loadSkillOptions();
    await loadBindings(record.id);
  }
  async function submitBind() {
    if (!bindSkillId.value) {
      createMessage.warning('请选择 Skill');
      return;
    }
    await bindSkill({ agentId: currentAgent.value.id, skillId: bindSkillId.value });
    createMessage.success('绑定成功');
    bindSkillId.value = undefined;
    await loadBindings(currentAgent.value.id);
  }
  async function removeBinding(record) {
    await unbindSkill({ agentId: record.agentId, skillId: record.skillId });
    createMessage.success('解绑成功');
    await loadBindings(currentAgent.value.id);
  }
  async function loadSkillOptions() {
    const result: any = await listSkills({ pageNo: 1, pageSize: 1000 });
    const records = result?.records || result?.result?.records || [];
    skillOptions.value = records.map((item) => ({
      label: `${item.name} (${item.slug || item.id}@${item.version || '1.0.0'})`,
      value: item.id,
    }));
  }
  async function loadBindings(agentId: string) {
    const result: any = await listAgentSkills({ agentId, pageNo: 1, pageSize: 1000 });
    const records = result?.records || result?.result?.records || [];
    bindingRows.value = records.map((item) => ({
      ...item,
      skillName: skillOptions.value.find((skill) => skill.value === item.skillId)?.label || item.skillId,
    }));
  }
</script>
