<template>
  <BasicTable @register="registerTable">
    <template #action="{ record }">
      <TableAction :actions="[{ label: '禁用', auth: 'openclaw:skill:disable', onClick: async () => (await disableSkill({ id: record.id }), reload()) }]" />
    </template>
  </BasicTable>
</template>

<script lang="ts" setup name="OpenclawSkillReviewList">
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { disableSkill, listSkills } from '../api';
  import { keywordSearch } from '../common';

  const [registerTable, { reload }] = useTable({
    title: 'Skill 审核管理',
    api: listSkills,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Skill 名称', dataIndex: 'name', width: 170 },
      { title: 'slug', dataIndex: 'slug', width: 170 },
      { title: '版本', dataIndex: 'version', width: 100 },
      { title: '用户ID', dataIndex: 'ownerUserId', width: 170 },
      { title: '用户名', dataIndex: 'ownerUsername', width: 120 },
      { title: '范围', dataIndex: 'scope', width: 90 },
      { title: '状态', dataIndex: 'status', width: 130 },
      { title: '描述', dataIndex: 'description', ellipsis: true },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 100, fixed: 'right' },
  });
</script>
