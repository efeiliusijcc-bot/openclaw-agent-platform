<template>
  <div>
    <BasicTable @register="registerTable">
      <template #action="{ record }">
        <TableAction :actions="actions(record)" />
      </template>
    </BasicTable>

    <a-drawer v-model:open="detailVisible" title="Skill Review Detail" width="920px" destroyOnClose>
      <a-empty v-if="!detail" description="No review selected" />
      <template v-else>
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="Review">{{ detail.review?.id }}</a-descriptions-item>
          <a-descriptions-item label="Status">{{ detail.review?.status }}</a-descriptions-item>
          <a-descriptions-item label="Draft">{{ detail.review?.draftId }}</a-descriptions-item>
          <a-descriptions-item label="Draft Version">v{{ detail.review?.versionNo }}</a-descriptions-item>
          <a-descriptions-item label="Submitter">{{ detail.review?.submitterUsername || detail.review?.submitterId }}</a-descriptions-item>
          <a-descriptions-item label="Submitted">{{ detail.review?.submittedTime || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Reviewer">{{ detail.review?.reviewerUsername || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Published">v{{ detail.review?.publishedVersionNo || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Submit Comment" :span="2">{{ detail.review?.submitComment || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Review Comment" :span="2">{{ detail.review?.reviewComment || '-' }}</a-descriptions-item>
        </a-descriptions>

        <a-tabs style="margin-top: 12px">
          <a-tab-pane key="files" tab="Snapshot">
            <div v-for="(value, path) in detail.files || {}" :key="path" class="review-file">
              <strong>{{ path }}</strong>
              <pre class="review-pre">{{ value }}</pre>
            </div>
          </a-tab-pane>
          <a-tab-pane key="diff" tab="Published Diff">
            <a-empty v-if="!detail.publishedDiffs?.length" description="No previous published version diff" />
            <div v-for="item in detail.publishedDiffs || []" :key="item.path" class="review-diff">
              <a-tag :color="item.changeType === 'added' ? 'green' : item.changeType === 'deleted' ? 'red' : 'blue'">{{ item.changeType }}</a-tag>
              <strong>{{ item.path }}</strong>
              <div class="review-hash">{{ item.beforeHash || '-' }} -> {{ item.afterHash || '-' }}</div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="test" tab="Test Report">
            <pre class="review-pre">{{ detail.testReport ? JSON.stringify(detail.testReport, null, 2) : '-' }}</pre>
          </a-tab-pane>
          <a-tab-pane key="ai" tab="AI Records">
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
    title: 'Skill Reviews',
    api: listSkillReviews,
    rowKey: 'id',
    bordered: true,
    columns: [
      { title: 'Review ID', dataIndex: 'id', width: 180 },
      { title: 'Draft ID', dataIndex: 'draftId', width: 180 },
      { title: 'Version', dataIndex: 'versionNo', width: 90 },
      { title: 'Status', dataIndex: 'status', width: 120 },
      { title: 'Submitter', dataIndex: 'submitterUsername', width: 130 },
      { title: 'Reviewer', dataIndex: 'reviewerUsername', width: 130 },
      { title: 'Published Version', dataIndex: 'publishedVersionNo', width: 140 },
      { title: 'Submitted Time', dataIndex: 'submittedTime', width: 170 },
      { title: 'Submit Comment', dataIndex: 'submitComment', ellipsis: true },
    ],
    formConfig: { labelWidth: 90, schemas: keywordSearch() },
    actionColumn: { width: 210, fixed: 'right', slots: { customRender: 'action' } },
  });

  function actions(record) {
    const actionable = record.status === 'SUBMITTED';
    return [
      { label: 'Detail', auth: 'openclaw:skill:review', onClick: () => openDetail(record) },
      { label: 'Approve', auth: 'openclaw:skill:review', ifShow: actionable, onClick: () => approve(record) },
      { label: 'Reject', auth: 'openclaw:skill:review', ifShow: actionable, onClick: () => reject(record) },
    ];
  }

  async function openDetail(record) {
    detail.value = await getSkillReview(record.id);
    detailVisible.value = true;
  }

  function approve(record) {
    let comment = '';
    Modal.confirm({
      title: 'Approve and publish this fixed version?',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: 'Review comment',
        onChange: (event: Event) => {
          comment = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: 'Approve',
      onOk: async () => {
        const result = await approveSkillReview(record.id, { comment });
        createMessage.success(`Published version ${result.publishedVersionNo}`);
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
      title: 'Reject this review?',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: 'Reject reason',
        onChange: (event: Event) => {
          comment = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: 'Reject',
      onOk: async () => {
        await rejectSkillReview(record.id, { comment });
        createMessage.warning('Review rejected');
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
