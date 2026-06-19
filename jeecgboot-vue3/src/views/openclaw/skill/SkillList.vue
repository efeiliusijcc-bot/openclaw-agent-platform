<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:skill:add'" @click="openAdd">创建 Skill</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>
    <a-modal v-model:open="visible" :title="form.id ? '编辑 Skill' : '创建 Skill 草稿'" @ok="submit" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="Skill 名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="版本号"><a-input v-model:value="form.version" placeholder="默认 1.0.0" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillList">
  import { h, reactive, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { addSkill, checkSkillQuality, deleteSkill, disableSkill, editSkill, exportSkill, listSkills } from '../api';
  import { commonTimeColumns, keywordSearch, readonlyPathColumn } from '../common';

  const { createMessage } = useMessage();
  const visible = ref(false);
  const form = reactive<any>({});
  const [registerTable, { reload }] = useTable({
    title: 'Skill Studio',
    api: listSkills,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Skill 名称', dataIndex: 'name', width: 160 },
      { title: 'slug', dataIndex: 'slug', width: 170 },
      { title: '版本号', dataIndex: 'version', width: 100 },
      { title: '员工 ID', dataIndex: 'ownerUserId', width: 170 },
      { title: '员工账号', dataIndex: 'ownerUsername', width: 120 },
      { title: '范围', dataIndex: 'scope', width: 90 },
      { title: '状态', dataIndex: 'status', width: 130 },
      readonlyPathColumn('交付目录'),
      { title: '文件大小', dataIndex: 'fileSize', width: 110 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 320, fixed: 'right' },
  });

  function reset(data: any = {}) {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(form, { version: '1.0.0' }, data);
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
      createMessage.warning('请填写 Skill 名称');
      return;
    }
    await (form.id ? editSkill(form) : addSkill(form));
    visible.value = false;
    reload();
  }

  function actions(record) {
    return [
      { label: '编辑', auth: 'openclaw:skill:edit', onClick: () => openEdit(record) },
      { label: '质量检查', auth: 'openclaw:skill:quality', onClick: () => showQuality(record) },
      { label: '导出', auth: 'openclaw:skill:export', onClick: () => exportSkill(record) },
      {
        label: '删除',
        color: 'error',
        auth: 'openclaw:skill:delete',
        popConfirm: { title: '删除前请确认没有 Agent 绑定该 Skill，是否继续？', confirm: async () => (await deleteSkill({ id: record.id }), reload()) },
      },
      {
        label: '禁用',
        auth: 'openclaw:skill:disable',
        onClick: () => Modal.confirm({ title: '确认禁用该 Skill？', onOk: async () => (await disableSkill({ id: record.id }), reload()) }),
      },
    ];
  }

  async function showQuality(record) {
    const result = await checkSkillQuality(record.id);
    const missingFiles = result.missingFiles || [];
    const warnings = result.warnings || [];
    const checklist = result.checklist || [];
    Modal.info({
      title: result.passed ? 'Skill 交付检查通过' : 'Skill 交付检查需要处理',
      width: 760,
      content: h('div', { class: 'quality-result' }, [
        h('p', `评分: ${result.score || 0} / 100`),
        h('p', `文件数: ${result.fileCount || 0}`),
        h('p', `大小: ${result.totalSize || 0} bytes`),
        missingFiles.length ? h('p', { class: 'quality-error' }, `缺失文件: ${missingFiles.join(', ')}`) : null,
        warnings.length ? h('p', { class: 'quality-warning' }, `风险提示: ${warnings.join('; ')}`) : null,
        checklist.length ? h('div', [h('p', '检查清单:'), h('ul', checklist.map((item) => h('li', item)))]) : null,
      ]),
    });
  }
</script>

<style scoped>
  .quality-result :deep(p) {
    margin: 0 0 8px;
  }

  .quality-result :deep(ul) {
    margin: 0;
    padding-left: 20px;
  }

  .quality-error {
    color: #cf1322;
  }

  .quality-warning {
    color: #d48806;
  }
</style>
