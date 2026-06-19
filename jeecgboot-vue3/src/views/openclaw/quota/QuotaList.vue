<template>
  <div>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction
          :actions="[
            { label: '用量', auth: 'openclaw:quota:list', onClick: () => openUsage(record) },
            { label: '编辑', auth: 'openclaw:quota:edit', onClick: () => openEdit(record) },
          ]"
        />
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
    <a-modal v-model:open="usageVisible" title="当前配额用量" :footer="null" destroyOnClose>
      <a-descriptions v-if="usage" :column="1" bordered size="small">
        <a-descriptions-item label="用户">{{ usage.quota?.username || usage.quota?.userId }}</a-descriptions-item>
        <a-descriptions-item label="Agent">{{ formatUsage(usage.usedAgents, usage.quota?.maxAgents) }}</a-descriptions-item>
        <a-descriptions-item label="Workspace">{{ formatUsage(usage.usedWorkspaces, usage.quota?.maxWorkspaces) }}</a-descriptions-item>
        <a-descriptions-item label="Skill">{{ formatUsage(usage.usedSkills, usage.quota?.maxSkills) }}</a-descriptions-item>
        <a-descriptions-item label="今日运行">{{ formatUsage(usage.todayRuns, usage.quota?.maxDailyRuns) }}</a-descriptions-item>
        <a-descriptions-item label="并发运行">{{ formatUsage(usage.runningRuns, usage.quota?.maxConcurrentRuns) }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ usage.quota?.status }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawQuotaList">
  import { reactive, ref } from 'vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { editQuota, getQuotaUsage, listQuotas } from '../api';
  import { keywordSearch } from '../common';

  const visible = ref(false);
  const usageVisible = ref(false);
  const form = reactive<any>({});
  const usage = ref<any>(null);
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
    actionColumn: { width: 140, fixed: 'right' },
  });
  function openEdit(record) {
    Object.keys(form).forEach((key) => delete form[key]);
    Object.assign(form, record);
    visible.value = true;
  }
  async function openUsage(record) {
    usage.value = await getQuotaUsage({ userId: record.userId });
    usageVisible.value = true;
  }
  function formatUsage(used?: number, limit?: number) {
    return `${used ?? 0} / ${limit ?? '-'}`;
  }
  async function submit() {
    await editQuota(form);
    visible.value = false;
    reload();
  }
</script>
