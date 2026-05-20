<template>
  <div>
    <BasicTable @register="registerTable">
      <template #tableTitle>
        <a-button type="primary" preIcon="ant-design:plus-outlined" v-auth="'openclaw:skill:add'" @click="openAdd">新增 Skill</a-button>
      </template>
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>
    <a-modal v-model:open="visible" :title="form.id ? '编辑 Skill' : '新增 Skill'" @ok="submit" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="Skill 名称" required><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="版本"><a-input v-model:value="form.version" placeholder="默认 1.0.0" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" :rows="3" /></a-form-item>
        <a-form-item label="备注"><a-textarea v-model:value="form.remark" :rows="2" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillList">
  import { reactive, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { addSkill, deleteSkill, disableSkill, editSkill, exportSkill, listSkills } from '../api';
  import { commonTimeColumns, keywordSearch, readonlyPathColumn } from '../common';

  const { createMessage } = useMessage();
  const visible = ref(false);
  const form = reactive<any>({});
  const [registerTable, { reload }] = useTable({
    title: '我的 Skill',
    api: listSkills,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Skill 名称', dataIndex: 'name', width: 160 },
      { title: 'slug', dataIndex: 'slug', width: 170 },
      { title: '版本', dataIndex: 'version', width: 100 },
      { title: '用户ID', dataIndex: 'ownerUserId', width: 170 },
      { title: '用户名', dataIndex: 'ownerUsername', width: 120 },
      { title: '范围', dataIndex: 'scope', width: 90 },
      { title: '状态', dataIndex: 'status', width: 130 },
      readonlyPathColumn('后端生成路径'),
      { title: '文件大小', dataIndex: 'fileSize', width: 110 },
      ...commonTimeColumns,
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 260, fixed: 'right' },
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
      createMessage.warning('请输入 Skill 名称');
      return;
    }
    await (form.id ? editSkill(form) : addSkill(form));
    visible.value = false;
    reload();
  }
  function actions(record) {
    return [
      { label: '编辑', auth: 'openclaw:skill:edit', onClick: () => openEdit(record) },
      { label: '导出', auth: 'openclaw:skill:export', onClick: () => exportSkill(record) },
      {
        label: '删除',
        color: 'error',
        auth: 'openclaw:skill:delete',
        popConfirm: { title: '已绑定 Agent 的 Skill 不能删除，确认删除？', confirm: async () => (await deleteSkill({ id: record.id }), reload()) },
      },
      {
        label: '禁用',
        auth: 'openclaw:skill:disable',
        onClick: () => Modal.confirm({ title: '确认禁用该 Skill？', onOk: async () => (await disableSkill({ id: record.id }), reload()) }),
      },
    ];
  }
</script>
