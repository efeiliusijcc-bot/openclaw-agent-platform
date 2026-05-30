<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:agent:add'" @click="openAdd">
          新增 Agent
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

    <a-modal v-model:open="visible" :title="form.id ? '编辑 Agent' : '新增 Agent'" okText="确定" cancelText="取消" @ok="submit" destroyOnClose>
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

    <a-modal v-model:open="bindVisible" title="绑定 Skill" :footer="null" width="720px" destroyOnClose>
      <a-space style="width: 100%; margin-bottom: 12px">
        <a-select
          v-model:value="bindSkillId"
          show-search
          allow-clear
          optionFilterProp="label"
          :options="skillOptions"
          placeholder="请选择 Skill"
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

    <a-modal
      v-model:open="chatVisible"
      title="实时对话"
      okText="发送"
      cancelText="关闭"
      :confirmLoading="chatLoading"
      width="820px"
      destroyOnClose
      @ok="submitChat"
    >
      <a-form layout="vertical">
        <a-form-item label="Prompt" required>
          <a-textarea
            v-model:value="chatPrompt"
            :rows="4"
            :maxlength="MAX_PROMPT_LENGTH"
            show-count
            placeholder="请输入要发送给 Agent 的内容"
            :disabled="chatLoading"
          />
        </a-form-item>
      </a-form>
      <a-descriptions v-if="chatRun" size="small" bordered :column="1" style="margin-bottom: 12px">
        <a-descriptions-item label="Run ID">{{ chatRun.runId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Conversation ID">{{ chatRun.conversationId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="Status">
          <a-tag :color="runStatusColor(chatRun.status)">{{ chatRun.status || 'running' }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="Duration (ms)">{{ chatRun.durationMs ?? '-' }}</a-descriptions-item>
      </a-descriptions>
      <div class="chat-output-wrap">
        <div class="chat-output-title">流式输出</div>
        <pre class="run-output">{{ chatOutput || (chatLoading ? '等待 Agent 响应...' : '暂无输出') }}</pre>
      </div>
      <a-alert v-if="chatError" type="error" show-icon :message="chatError" style="margin-top: 12px" />
    </a-modal>

    <a-modal
      v-model:open="runVisible"
      title="运行测试"
      okText="运行"
      cancelText="取消"
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
            placeholder="请输入测试 Prompt"
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
    streamAgentChat,
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
  const chatVisible = ref(false);
  const chatLoading = ref(false);
  const chatPrompt = ref('');
  const chatOutput = ref('');
  const chatError = ref('');
  const chatRun = ref<any>();
  const form = reactive<any>({});

  const bindingColumns = [
    { title: 'Skill', dataIndex: 'skillName' },
    { title: 'Skill ID', dataIndex: 'skillId', width: 220 },
    { title: 'Enabled', dataIndex: 'enabled', width: 90 },
    { title: 'Create Time', dataIndex: 'createTime', width: 170 },
    { title: '操作', key: 'action', width: 90 },
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
    actionColumn: {
      title: '操作',
      dataIndex: 'action',
      width: 300,
      fixed: 'right',
      slots: { customRender: 'action' },
    },
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
      { label: '运行测试', onClick: () => openRunTest(record) },
      { label: '实时对话', onClick: () => openChat(record) },
      { label: '绑定 Skill', auth: 'openclaw:agent:bindSkill', onClick: () => openBind(record) },
      { label: '运行记录', onClick: () => router.push({ path: '/openclaw/run', query: { agentId: record.id } }) },
      {
        label: '删除',
        color: 'error',
        auth: 'openclaw:agent:delete',
        popConfirm: { title: '确认删除该 Agent？', confirm: async () => (await deleteAgent({ id: record.id }), reload()) },
      },
      {
        label: '禁用',
        auth: 'openclaw:agent:disable',
        onClick: () => Modal.confirm({ title: '确认禁用该 Agent？', okText: '确定', cancelText: '取消', onOk: async () => (await disableAgent({ id: record.id }), reload()) }),
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

  function openChat(record) {
    currentAgent.value = record;
    chatPrompt.value = '';
    chatOutput.value = '';
    chatError.value = '';
    chatRun.value = undefined;
    chatVisible.value = true;
  }

  async function submitRunTest() {
    const prompt = (runPrompt.value || '').trim();
    if (!prompt) {
      createMessage.warning('Prompt 不能为空');
      return;
    }
    if (prompt.length > MAX_PROMPT_LENGTH) {
      createMessage.warning(`Prompt 长度不能超过 ${MAX_PROMPT_LENGTH}`);
      return;
    }
    runLoading.value = true;
    try {
      const result: any = await runAgentTest(currentAgent.value.id, { prompt });
      runResult.value = result?.result || result;
      if (runResult.value?.status === 'success') {
        createMessage.success('运行完成');
      } else if (runResult.value?.status === 'timeout') {
        createMessage.warning(runResult.value?.errorMessage || '运行超时');
      } else {
        createMessage.error(runResult.value?.errorMessage || '运行失败');
      }
    } finally {
      runLoading.value = false;
    }
  }

  async function submitChat() {
    const prompt = (chatPrompt.value || '').trim();
    if (!prompt) {
      createMessage.warning('Prompt 不能为空');
      return;
    }
    if (prompt.length > MAX_PROMPT_LENGTH) {
      createMessage.warning(`Prompt 长度不能超过 ${MAX_PROMPT_LENGTH}`);
      return;
    }
    chatLoading.value = true;
    chatOutput.value = '';
    chatError.value = '';
    try {
      const stream = await streamAgentChat(currentAgent.value.id, {
        prompt,
        conversationId: chatRun.value?.conversationId,
      });
      await readChatStream(stream);
    } catch (error: any) {
      if (chatRun.value?.status === 'success') {
        chatError.value = '';
        return;
      }
      chatError.value = error?.message || '实时对话请求失败';
      createMessage.error(chatError.value);
    } finally {
      chatLoading.value = false;
    }
  }

  async function readChatStream(stream: ReadableStream<Uint8Array>) {
    const reader = stream.getReader();
    const decoder = new TextDecoder('UTF-8');
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const events = buffer.split('\n\n');
      buffer = events.pop() || '';
      events.forEach(handleChatEvent);
    }
    if (buffer.trim()) {
      handleChatEvent(buffer);
    }
  }

  function handleChatEvent(raw: string) {
    let eventName = 'message';
    const dataLines: string[] = [];
    raw.split(/\r?\n/).forEach((line) => {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      }
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    });
    if (!dataLines.length) {
      return;
    }
    try {
      const payload = JSON.parse(dataLines.join('\n'));
      if (eventName === 'run_created') {
        chatRun.value = payload;
      } else if (eventName === 'delta') {
        chatOutput.value += payload.text || '';
      } else if (eventName === 'done') {
        chatRun.value = payload;
        chatError.value = '';
        if (payload.outputSummary) {
          chatOutput.value = payload.outputSummary;
        }
        createMessage.success('对话完成');
      } else if (eventName === 'error' || eventName === 'timeout') {
        chatRun.value = payload;
        chatError.value = payload.errorMessage || '对话失败';
        createMessage.error(chatError.value);
      }
    } catch (error) {
      console.warn('Failed to parse chat stream event', error, raw);
    }
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

  .chat-output-wrap {
    border: 1px solid #d9d9d9;
    border-radius: 4px;
    padding: 12px;
    background: #fafafa;
  }

  .chat-output-title {
    margin-bottom: 8px;
    font-weight: 500;
  }
</style>
