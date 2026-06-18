<template>
  <BasicTable @register="registerTable">
    <template #action="{ record }">
      <TableAction :actions="actions(record)" />
    </template>
  </BasicTable>
</template>

<script lang="ts" setup name="OpenclawSkillReviewList">
  import { h } from 'vue';
  import { Input, Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { approveSkill, disableSkill, listSkills, rejectSkill } from '../api';
  import { keywordSearch } from '../common';

  const [registerTable, { reload }] = useTable({
    title: 'Skill Review',
    api: listSkills,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Skill Name', dataIndex: 'name', width: 170 },
      { title: 'Slug', dataIndex: 'slug', width: 170 },
      { title: 'Version', dataIndex: 'version', width: 100 },
      { title: 'Owner User ID', dataIndex: 'ownerUserId', width: 170 },
      { title: 'Owner Username', dataIndex: 'ownerUsername', width: 130 },
      { title: 'Scope', dataIndex: 'scope', width: 90 },
      { title: 'Status', dataIndex: 'status', width: 130 },
      { title: 'Description', dataIndex: 'description', ellipsis: true },
      { title: 'Remark', dataIndex: 'remark', ellipsis: true },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 180, fixed: 'right', slots: { customRender: 'action' } },
  });

  function actions(record) {
    const canReview = record.status !== 'approved' && record.status !== 'disabled';
    return [
      {
        label: 'Approve',
        auth: 'openclaw:skill:disable',
        ifShow: canReview,
        popConfirm: {
          title: 'Approve this Skill for shared use?',
          confirm: async () => {
            await approveSkill({ id: record.id });
            reload();
          },
        },
      },
      {
        label: 'Reject',
        auth: 'openclaw:skill:disable',
        ifShow: canReview,
        onClick: () => reject(record),
      },
      {
        label: 'Disable',
        auth: 'openclaw:skill:disable',
        ifShow: record.status !== 'disabled',
        popConfirm: {
          title: 'Disable this Skill?',
          confirm: async () => {
            await disableSkill({ id: record.id });
            reload();
          },
        },
      },
    ];
  }

  function reject(record) {
    let reason = '';
    Modal.confirm({
      title: 'Reject this Skill?',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: 'Reject reason',
        onChange: (event: Event) => {
          reason = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: 'Reject',
      cancelText: 'Cancel',
      onOk: async () => {
        await rejectSkill({ id: record.id, reason });
        reload();
      },
    });
  }
</script>
