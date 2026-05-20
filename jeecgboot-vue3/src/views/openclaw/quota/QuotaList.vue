<template>
  <div>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction :actions="[{ label: '编辑', auth: 'openclaw:quota:edit', onClick: () => openEdit(record) }]" />
      </template>
    </BasicTable>
    <a-modal v-model:open="visible" title="编辑用户配额" @ok="submit" destroyOnClose>
      <a-form :model="form" layout="vertical">
        <a-form-item label="最大 Agent"><a-input-number v-model:value="form.maxAgents" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="最大 Workspace"><a-input-number v-model:value="form.maxWorkspaces" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="最大 Skill"><a-input-number v-model:value="form.maxSkills" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="最大存储(MB)"><a-input-number v-model:value="form.maxStorageMb" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="每日运行次数"><a-input-number v-model:value="form.maxDailyRuns" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="并发运行数"><a-input-number v-model:value="form.maxConcurrentRuns" :min="0" style="width:100%" /></a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="form.status" :options="[{label:'enabled',value:'enabled'},{label:'disabled',value:'disabled'}]" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawQuotaList">
  import { reactive, ref } from 'vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { editQuota, listQuotas } from '../api';
  import { keywordSearch } from '../common';

  const visible = ref(false);
  const form = reactive<any>({});
  const [registerTable, { reload }] = useTable({
    title: '用户配额管理',
    api: listQuotas,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      { title: 'Agent', dataIndex: 'maxAgents', width: 90 },
      { title: 'Workspace', dataIndex: 'maxWorkspaces', width: 110 },
      { title: 'Skill', dataIndex: 'maxSkills', width: 90 },
      { title: '存储(MB)', dataIndex: 'maxStorageMb', width: 100 },
      { title: '每日运行', dataIndex: 'maxDailyRuns', width: 100 },
      { title: '并发', dataIndex: 'maxConcurrentRuns', width: 90 },
      { title: '状态', dataIndex: 'status', width: 100 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('username', '用户名') },
    actionColumn: { width: 100, fixed: 'right' },
  });
  function openEdit(record) {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(form, record);
    visible.value = true;
  }
  async function submit() {
    await editQuota(form);
    visible.value = false;
    reload();
  }
</script>
