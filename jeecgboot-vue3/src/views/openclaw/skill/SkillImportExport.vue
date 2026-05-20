<template>
  <div>
    <a-card :bordered="false" title="Skill 导入导出">
      <a-upload :showUploadList="false" accept=".zip" :customRequest="upload">
        <a-button type="primary" preIcon="ant-design:import-outlined" v-auth="'openclaw:skill:import'">导入 Skill zip</a-button>
      </a-upload>
    </a-card>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction :actions="[{ label: '导出', auth: 'openclaw:skill:export', onClick: () => exportSkill(record) }]" />
      </template>
    </BasicTable>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillImportExport">
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { exportSkill, importSkill, listSkills } from '../api';
  import { keywordSearch } from '../common';

  const { createMessage } = useMessage();
  const [registerTable, { reload }] = useTable({
    title: '可导出的 Skill',
    api: listSkills,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Skill 名称', dataIndex: 'name', width: 180 },
      { title: 'slug', dataIndex: 'slug', width: 180 },
      { title: '版本', dataIndex: 'version', width: 100 },
      { title: '状态', dataIndex: 'status', width: 130 },
      { title: 'checksum', dataIndex: 'checksum', ellipsis: true },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 100, fixed: 'right' },
  });

  async function upload(options) {
    try {
      await importSkill(options.file);
      createMessage.success('导入成功');
      reload();
      options.onSuccess?.();
    } catch (e) {
      options.onError?.(e);
    }
  }
</script>
