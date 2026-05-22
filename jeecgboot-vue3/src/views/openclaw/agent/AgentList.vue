<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:agent:add'" @click="openAdd">
          New Agent
        </a-button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="agentStatusColor(record.status)">{{ record.status }}</a-tag>
        </template>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>

    <a-modal v-model:open="visible" :title="form.id ? 'Edit Agent' : 'New Agent'" @ok="submit" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="Agent Name" required>
          <a-input v-model:value="form.name" />
        </a-form-item>
        <a-form-item label="Description">
          <a-textarea v-model:value="form.description" :rows="3" />
        </a-form-item>
        <a-form-item label="Max Skills">
          <a-input-number v-model:value="form.maxSkills" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="Max Daily Runs">
          <a-input-number v-model:value="form.maxDailyRuns" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="Config JSON">
          <a-textarea v-model:value="form.configJson" :rows="4" />
        </a-form-item>
        <a-form-item label="Remark">
          <a-textarea v-model:value="form.remark" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="bindVisible" title="Bind Skill" :footer="null" width="720px" destroyOnClose>
      <a-space style="width: 100%; margin-bottom: 12px">
        <a-select
          v-model:value="bindSkillId"
          show-search
          allow-clear
          optionFilterProp="label"
          :options="skillOptions"
          placeholder="Select Skill"
          style="width: 420px"
        />
        <a-button type="primary" v-auth="'openclaw:agent:bindSkill'" @click="submitBind">Bind</a-button>
      </a-space>
      <a-table size="small" rowKey="id" :columns="bindingColumns" :dataSource="bindingRows" :pagination="false">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'action'">
            <a-button type="link" danger v-auth="'openclaw:agent:unbindSkill'" @click="removeBinding(record)">Unbind</a-button>
          </template>
        </template>
      </a-table>
    </a-modal>

    <a-modal
      v-model:open="runVisible"
      title="Run Test"
      :confirmLoading="runLoading"
      width="720px"
      destroyOnClose
      @ok="submitRunTest"
    >
      <a-form layout="vertical">
        <a-form-item label="Prompt" required>
          <a-textarea
            v-model:value="runPrompt"
            :rows="5"
            :maxlength="MAX_PROMPT_LENGTH"
            show-count
            placeholder="Enter a test prompt"
          />
        </a-form-item>
      </a-form>
      <a-descriptions v-if="runResult" size="small" bordered :column="1">
        <a-descriptions-item label="Status">
          <a-tag :color="runStatusColor(runResult.status)">{{ runResult.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Duration (ms)">{{ runResult.durationMs ?? '-' }}</a-descriptions-item>
        <a-descriptions-item v-if="runResult.outputSummary" label="Output Summary">
          <pre class="run-output">{{ runResult.outputSummary }}</pre>
        </a-descriptions-item>
        <a-descriptions-item v-if="runResult.errorMessage" label="Error Message">
          <pre class="run-output run-error">{{ runResult.errorMessage }}</pre>
        </a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawAgentList">
  import { reactive, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { useRouter } from 'vue-router';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    addAgent,
    bindSkill,
    deleteAgent,
    disableAgent,
    editAgent,
    listAgentSkills,
    listAgents,
    listSkills,
    runAgentTest,
    unbindSkill,
  } from '../api';
  import { commonTimeColumns, keywordSearch } from '../common';

  const MAX_PROMPT_LENGTH = 8000;
  const { createMessage } = useMessage();
  const router = useRouter();
  const visible = ref(false);
  const bindVisible = ref(false);
  const currentAgent = ref<any>();
  const bindSkillId = ref<string>();
  const skillOptions = ref<any[]>([]);
  const bindingRows = ref<any[]>([]);
  const runVisible = ref(false);
  const runLoading = ref(false);
  const runPrompt = ref('');
  const runResult = ref<any>();
  const form = reactive<any>({});

  const bindingColumns = [
    { title: 'Skill', dataIndex: 'skillName' },
    { title: 'Skill ID', dataIndex: 'skillId', width: 220 },
    { title: 'Enabled', dataIndex: 'enabled', width: 90 },
    { title: 'Create Time', dataIndex: 'createTime', width: 170 },
    { title: 'Action', key: 'action', width: 90 },
  ];

  const [registerTable, { reload }] = useTable({
    title: 'My Agents',
    api: listAgents,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Agent Name', dataIndex: 'name', width: 160 },
      { title: 'Agent Key', dataIndex: 'agentKey', width: 220 },
      { title: 'User ID', dataIndex: 'userId', width: 170 },
      { title: 'Username', dataIndex: 'username', width: 120 },
      { title: 'Workspace ID', dataIndex: 'workspaceId', width: 170 },
      { title: 'Status', dataIndex: 'status', width: 100 },
      { title: 'Max Skills', dataIndex: 'maxSkills', width: 100 },
      { title: 'Daily Runs', dataIndex: 'maxDailyRuns', width: 100 },
      { title: 'Gateway', dataIndex: 'gatewayId', width: 160 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 250, fixed: 'right' },
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
      createMessage.warning('Please enter agent name');
      return;
    }
    await (form.id ? editAgent(form) : addAgent(form));
    visible.value = false;
    reload();
  }

  function actions(record) {
    return [
      { label: 'Edit', auth: 'openclaw:agent:edit', onClick: () => openEdit(record) },
      { label: 'Run Test', auth: 'openclaw:agent:list', onClick: () => openRunTest(record) },
      { label: 'Bind Skill', auth: 'openclaw:agent:bindSkill', onClick: () => openBind(record) },
      { label: 'Runs', auth: 'openclaw:run:list', onClick: () => router.push({ path: '/openclaw/run', query: { agentId: record.id } }) },
      {
        label: 'Delete',
        color: 'error',
        auth: 'openclaw:agent:delete',
        popConfirm: { title: 'Confirm delete this agent?', confirm: async () => (await deleteAgent({ id: record.id }), reload()) },
      },
      {
        label: 'Disable',
        auth: 'openclaw:agent:disable',
        onClick: () => Modal.confirm({ title: 'Confirm disable this agent?', onOk: async () => (await disableAgent({ id: record.id }), reload()) }),
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

  function openRunTest(record) {
    currentAgent.value = record;
    runPrompt.value = 'Reply with exactly: OK';
    runResult.value = undefined;
    runVisible.value = true;
  }

  async function submitRunTest() {
    const prompt = (runPrompt.value || '').trim();
    if (!prompt) {
      createMessage.warning('Prompt cannot be empty');
      return;
    }
    if (prompt.length > MAX_PROMPT_LENGTH) {
      createMessage.warning(`Prompt length cannot exceed ${MAX_PROMPT_LENGTH}`);
      return;
    }
    runLoading.value = true;
    try {
      const result: any = await runAgentTest(currentAgent.value.id, { prompt });
      runResult.value = result?.result || result;
      if (runResult.value?.status === 'success') {
        createMessage.success('Run completed');
      } else if (runResult.value?.status === 'timeout') {
        createMessage.warning(runResult.value?.errorMessage || 'Run timeout');
      } else {
        createMessage.error(runResult.value?.errorMessage || 'Run failed');
      }
    } finally {
      runLoading.value = false;
    }
  }

  async function submitBind() {
    if (!bindSkillId.value) {
      createMessage.warning('Please select a skill');
      return;
    }
    await bindSkill({ agentId: currentAgent.value.id, skillId: bindSkillId.value });
    createMessage.success('Bind success');
    bindSkillId.value = undefined;
    await loadBindings(currentAgent.value.id);
  }

  async function removeBinding(record) {
    await unbindSkill({ agentId: record.agentId, skillId: record.skillId });
    createMessage.success('Unbind success');
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

  function agentStatusColor(status: string) {
    if (status === 'disabled') {
      return 'red';
    }
    if (status === 'draft') {
      return 'gold';
    }
    return 'blue';
  }

  function runStatusColor(status: string) {
    if (status === 'success') {
      return 'green';
    }
    if (status === 'failed') {
      return 'red';
    }
    if (status === 'timeout') {
      return 'orange';
    }
    return 'blue';
  }
</script>

<style scoped>
  .run-output {
    margin: 0;
    max-height: 260px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
  }

  .run-error {
    color: #cf1322;
  }
</style>
