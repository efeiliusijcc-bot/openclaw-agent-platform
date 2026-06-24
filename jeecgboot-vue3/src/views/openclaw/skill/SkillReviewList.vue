<template>
  <div>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>

    <a-drawer v-model:open="detailVisible" title="Skill 审核详情" width="920px" destroyOnClose>
      <a-empty v-if="!detail" description="未选择审核记录" />
      <template v-else>
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="审核">{{ detail.review?.id }}</a-descriptions-item>
          <a-descriptions-item label="状态">{{ detail.review?.status }}</a-descriptions-item>
          <a-descriptions-item label="草稿">{{ detail.review?.draftId }}</a-descriptions-item>
          <a-descriptions-item label="草稿版本">v{{ detail.review?.versionNo }}</a-descriptions-item>
          <a-descriptions-item label="提交人">{{ detail.review?.submitterUsername || detail.review?.submitterId }}</a-descriptions-item>
          <a-descriptions-item label="提交时间">{{ detail.review?.submittedTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="审核人">{{ detail.review?.reviewerUsername || '-' }}</a-descriptions-item>
          <a-descriptions-item label="发布版本">v{{ detail.review?.publishedVersionNo || '-' }}</a-descriptions-item>
          <a-descriptions-item label="提交说明" :span="2">{{ detail.review?.submitComment || '-' }}</a-descriptions-item>
          <a-descriptions-item label="审核说明" :span="2">{{ detail.review?.reviewComment || '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-tabs style="margin-top: 12px">
          <a-tab-pane key="files" tab="快照">
            <div v-for="(value, path) in detail.files || {}" :key="path" class="review-file">
              <strong>{{ path }}</strong>
              <pre class="review-pre">{{ value }}</pre>
            </div>
          </a-tab-pane>
          <a-tab-pane key="diff" tab="已发布差异">
            <a-empty v-if="!detail.publishedDiffs?.length" description="暂无上一已发布版本差异" />
            <div v-for="item in detail.publishedDiffs || []" :key="item.path" class="review-diff">
              <a-tag :color="item.changeType === 'added' ? 'green' : item.changeType === 'deleted' ? 'red' : 'blue'">{{ item.changeType }}</a-tag>
              <strong>{{ item.path }}</strong>
              <div class="review-hash">{{ item.beforeHash || '-' }} -> {{ item.afterHash || '-' }}</div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="test" tab="测试报告">
            <pre class="review-pre">{{ detail.testReport ? JSON.stringify(detail.testReport, null, 2) : '-' }}</pre>
          </a-tab-pane>
          <a-tab-pane key="ai" tab="AI 记录">
            <pre class="review-pre">{{ detail.aiRecords?.length ? JSON.stringify(detail.aiRecords, null, 2) : '-' }}</pre>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-drawer>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillReviewList">
  import { h, ref } from 'vue';
  import { Input, Modal } from 'ant-design-vue';
  import { BasicTable, TableAction, useTable } from '/@/components/Table';
  import { useMessage } from '/@/hooks/web/useMessage';
  import { approveSkillReview, getSkillReview, listSkillReviews, rejectSkillReview } from '../api';
  import { keywordSearch } from '../common';

  const { createMessage } = useMessage();
  const detailVisible = ref(false);
  const detail = ref<any>(null);

  const [registerTable, { reload }] = useTable({
    title: 'Skill 审核',
    api: listSkillReviews,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: '审核 ID', dataIndex: 'id', width: 180 },
      { title: '草稿 ID', dataIndex: 'draftId', width: 180 },
      { title: '版本', dataIndex: 'versionNo', width: 90 },
      { title: '状态', dataIndex: 'status', width: 120 },
      { title: '提交人', dataIndex: 'submitterUsername', width: 130 },
      { title: '审核人', dataIndex: 'reviewerUsername', width: 130 },
      { title: '发布版本', dataIndex: 'publishedVersionNo', width: 140 },
      { title: '提交时间', dataIndex: 'submittedTime', width: 170 },
      { title: '提交说明', dataIndex: 'submitComment', ellipsis: true },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 210, fixed: 'right', slots: { customRender: 'action' } },
  });

  function actions(record) {
    const actionable = record.status === 'SUBMITTED';
    return [
      { label: '详情', auth: 'openclaw:skill:review', onClick: () => openDetail(record) },
      { label: '通过', auth: 'openclaw:skill:review', ifShow: actionable, onClick: () => approve(record) },
      { label: '驳回', auth: 'openclaw:skill:review', ifShow: actionable, onClick: () => reject(record) },
    ];
  }

  async function openDetail(record) {
    detail.value = await getSkillReview(record.id);
    detailVisible.value = true;
  }

  function approve(record) {
    let comment = '';
    Modal.confirm({
      title: '通过并发布这个固定版本？',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: '审核说明',
        onChange: (event: Event) => {
          comment = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: '通过',
      onOk: async () => {
        const result = await approveSkillReview(record.id, { comment });
        createMessage.success(`已发布版本 ${result.publishedVersionNo}`);
        reload();
        if (detailVisible.value) {
          await openDetail(result);
        }
      },
    });
  }

  function reject(record) {
    let comment = '';
    Modal.confirm({
      title: '驳回这条审核？',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: '驳回原因',
        onChange: (event: Event) => {
          comment = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: '驳回',
      onOk: async () => {
        await rejectSkillReview(record.id, { comment });
        createMessage.warning('已驳回审核');
        reload();
      },
    });
  }
</script>

<style scoped>
  .review-file,
  .review-diff {
    margin-bottom: 12px;
  }

  .review-pre {
    max-height: 360px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    background: #f5f5f5;
    padding: 8px;
    margin: 8px 0 0;
  }

  .review-hash {
    margin-top: 4px;
    color: #595959;
    font-size: 12px;
    word-break: break-all;
  }
</style>
