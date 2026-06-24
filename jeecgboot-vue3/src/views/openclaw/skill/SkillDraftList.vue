<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-space>
          <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:skill:draft:add'" @click="openAdd">新建草稿</a-button>
          <a-button preIcon="ant-design:robot-outlined" v-auth="'openclaw:skill:draft:add'" @click="openGenerate">AI 生成</a-button>
          <a-button preIcon="ant-design:copy-outlined" v-auth="'openclaw:skill:draft:add'" @click="openFromSkill">从 Skill 创建</a-button>
        </a-space>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'draftName'">
          <a-button type="link" size="small" v-auth="'openclaw:skill:draft:edit'" @click="openEditor(record)">
            {{ record.draftName }}
          </a-button>
        </template>
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
        </template>
        <template v-if="column.dataIndex === 'lastLintStatus'">
          <a-tag v-if="record.lastLintStatus" :color="record.lastLintStatus === 'lint_passed' ? 'green' : 'red'">
            {{ record.lastLintStatus }}
          </a-tag>
        </template>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>

    <a-modal v-model:open="addVisible" title="新建 Skill 草稿" @ok="submitAdd" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="草稿名称" required><a-input v-model:value="form.draftName" /></a-form-item>
        <a-form-item label="Skill Slug" required><a-input v-model:value="form.skillSlug" placeholder="excel-summary" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="generateVisible" title="AI 生成 Skill 草稿" @ok="submitGenerate" destroyOnClose>
      <a-form :model="generateForm" layout="vertical">
        <a-form-item label="需求描述" required>
          <a-textarea v-model:value="generateForm.requirement" :rows="5" placeholder="描述这个 Skill 需要帮助 Agent 完成什么任务。" />
        </a-form-item>
        <a-form-item label="草稿名称"><a-input v-model:value="generateForm.draftName" placeholder="可选" /></a-form-item>
        <a-form-item label="Skill Slug"><a-input v-model:value="generateForm.skillSlug" placeholder="可选，例如 excel-summary" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="generateForm.description" :rows="3" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="fromSkillVisible" title="从正式 Skill 创建草稿" @ok="submitFromSkill" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="Skill ID" required>
          <a-input v-model:value="sourceSkillId" placeholder="输入 Skill 列表中的 ID" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillDraftList">
  import { reactive, ref } from 'vue';
  import { useRouter } from 'vue-router';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    addSkillDraft,
    createSkillDraftFromSkill,
    generateSkillDraft,
    lintSkillDraft,
    listSkillDrafts,
  } from '../api';
  import { commonTimeColumns, keywordSearch } from '../common';

  const router = useRouter();
  const { createMessage } = useMessage();
  const addVisible = ref(false);
  const generateVisible = ref(false);
  const fromSkillVisible = ref(false);
  const sourceSkillId = ref('');
  const form = reactive<any>({});
  const generateForm = reactive<any>({});

  const [registerTable, { reload }] = useTable({
    title: 'Skill 开发工作台',
    api: listSkillDrafts,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '草稿名称', dataIndex: 'draftName', width: 180 },
      { title: 'Skill Slug', dataIndex: 'skillSlug', width: 180 },
      { title: '所属用户', dataIndex: 'ownerUsername', width: 120 },
      { title: '状态', dataIndex: 'status', width: 120 },
      { title: 'Lint', dataIndex: 'lastLintStatus', width: 120 },
      { title: '测试', dataIndex: 'lastTestStatus', width: 120 },
      { title: '基础版本', dataIndex: 'baseVersion', width: 110 },
      { title: '草稿目录', dataIndex: 'draftPath', width: 320, ellipsis: true },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('draftName', '草稿名称') },
    actionColumn: {
      title: '操作',
      dataIndex: 'action',
      width: 140,
      fixed: 'right',
      slots: { customRender: 'action' },
    },
  });

  function statusColor(status) {
    return {
      editing: 'blue',
      lint_passed: 'green',
      lint_failed: 'red',
      test_passed: 'green',
      test_failed: 'red',
      submitted: 'orange',
      approved: 'green',
      rejected: 'red',
      published: 'purple',
    }[status] || 'default';
  }

  function resetForm() {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(form, { draftName: '', skillSlug: '', description: '' });
  }

  function openAdd() {
    resetForm();
    addVisible.value = true;
  }

  function resetGenerateForm() {
    Object.keys(generateForm).forEach((key) => delete generateForm[key]);
    Object.assign(generateForm, { requirement: '', draftName: '', skillSlug: '', description: '' });
  }

  function openGenerate() {
    resetGenerateForm();
    generateVisible.value = true;
  }

  function openFromSkill() {
    sourceSkillId.value = '';
    fromSkillVisible.value = true;
  }

  async function submitAdd() {
    if (!form.draftName || !form.skillSlug) {
      createMessage.warning('请填写草稿名称和 Skill Slug');
      return;
    }
    const draft = await addSkillDraft(form);
    addVisible.value = false;
    reload();
    openEditor(draft);
  }

  async function submitGenerate() {
    if (!generateForm.requirement) {
      createMessage.warning('请描述 Skill 需求');
      return;
    }
    const draft = await generateSkillDraft(generateForm);
    generateVisible.value = false;
    createMessage.success('已生成 Skill 草稿');
    reload();
    openEditor(draft);
  }

  async function submitFromSkill() {
    if (!sourceSkillId.value) {
      createMessage.warning('请填写 Skill ID');
      return;
    }
    const draft = await createSkillDraftFromSkill(sourceSkillId.value);
    fromSkillVisible.value = false;
    reload();
    openEditor(draft);
  }

  function openEditor(record) {
    router.push({ path: `/openclaw/skill-drafts/editor/${record.id}` });
  }

  async function runLint(record) {
    const result = await lintSkillDraft(record.id);
    createMessage[result.passed ? 'success' : 'warning'](
      `Lint ${result.status}：${result.errors?.length || 0} 个错误，${result.warnings?.length || 0} 个警告`
    );
    reload();
  }

  function actions(record) {
    return [
      { label: '编辑', auth: 'openclaw:skill:draft:edit', onClick: () => openEditor(record) },
      { label: 'Lint', auth: 'openclaw:skill:draft:lint', ifShow: !['submitted', 'approved', 'published'].includes(record.status), onClick: () => runLint(record) },
    ];
  }
</script>
