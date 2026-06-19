<template>
  <div>
    <BasicTable @register="registerTable">
      <template #result="{ text }">
        <a-tag :color="text === 'success' ? 'green' : 'red'">{{ text || '-' }}</a-tag>
      </template>
      <template #detail="{ record }">
        <a-button v-if="record.detailJson" type="link" size="small" @click="openDetail(record)">查看详情</a-button>
        <span v-else>-</span>
      </template>
    </BasicTable>

    <a-modal v-model:open="detailVisible" title="审计详情" :footer="null" width="820px" destroyOnClose>
      <a-descriptions v-if="detailRecord" bordered size="small" :column="2">
        <a-descriptions-item label="操作">{{ detailRecord.action || '-' }}</a-descriptions-item>
        <a-descriptions-item label="结果">{{ detailRecord.result || '-' }}</a-descriptions-item>
        <a-descriptions-item label="对象类型">{{ detailRecord.targetType || '-' }}</a-descriptions-item>
        <a-descriptions-item label="对象ID">{{ detailRecord.targetId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="用户">{{ detailRecord.username || detailRecord.userId || '-' }}</a-descriptions-item>
        <a-descriptions-item label="时间">{{ detailRecord.createTime || '-' }}</a-descriptions-item>
      </a-descriptions>
      <pre class="audit-detail">{{ formattedDetail }}</pre>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawAuditLogList">
  import { computed, ref } from 'vue';
  import { BasicTable, useTable } from '/@/components/Table';
  import { listAuditLogs } from '../api';
  import { keywordSearch } from '../common';

  const detailVisible = ref(false);
  const detailRecord = ref<any>();
  const formattedDetail = computed(() => {
    const detailJson = detailRecord.value?.detailJson;
    if (!detailJson) {
      return '-';
    }
    try {
      return JSON.stringify(JSON.parse(detailJson), null, 2);
    } catch (error) {
      return detailJson;
    }
  });

  const [registerTable] = useTable({
    title: '系统审计日志',
    api: listAuditLogs,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '用户ID', dataIndex: 'userId', width: 170 },
      { title: '用户名', dataIndex: 'username', width: 120 },
      { title: '操作', dataIndex: 'action', width: 150 },
      { title: '结果', dataIndex: 'result', width: 100, slots: { customRender: 'result' } },
      { title: '对象类型', dataIndex: 'targetType', width: 120 },
      { title: '对象ID', dataIndex: 'targetId', width: 170 },
      { title: 'IP', dataIndex: 'ip', width: 140 },
      { title: 'User Agent', dataIndex: 'userAgent', ellipsis: true },
      { title: '详情', dataIndex: 'detailJson', width: 100, slots: { customRender: 'detail' } },
      { title: '时间', dataIndex: 'createTime', width: 170 },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch('action', '操作') },
  });

  function openDetail(record: any) {
    detailRecord.value = record;
    detailVisible.value = true;
  }
</script>

<style scoped>
  .audit-detail {
    margin: 12px 0 0;
    max-height: 520px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    background: #f7f8fa;
    border: 1px solid #e5e6eb;
    border-radius: 4px;
    padding: 12px;
  }
</style>
