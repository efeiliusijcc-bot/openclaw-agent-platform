<template>
  <div class="skill-editor">
    <div class="editor-header">
      <a-space>
        <a-button preIcon="ant-design:arrow-left-outlined" @click="goBack">返回</a-button>
        <a-button preIcon="ant-design:reload-outlined" @click="loadTree">刷新</a-button>
        <a-button type="primary" preIcon="ant-design:save-outlined" :disabled="!currentPath || !canEdit" @click="saveCurrentFile">保存</a-button>
        <a-button preIcon="ant-design:check-circle-outlined" :disabled="!canEdit" @click="runLint">Lint</a-button>
        <a-button preIcon="ant-design:edit-outlined" :loading="repairing" :disabled="!canEdit" @click="openAiEdit">AI Edit</a-button>
        <a-button preIcon="ant-design:tool-outlined" :loading="repairing" :disabled="!canEdit" @click="runRepair">AI Repair</a-button>
        <a-button preIcon="ant-design:audit-outlined" :disabled="!canSubmit" @click="submitReview">Submit</a-button>
        <a-button preIcon="ant-design:check-outlined" :disabled="!canReview" @click="approveReview">Approve</a-button>
        <a-button danger preIcon="ant-design:close-outlined" :disabled="!canReview" @click="rejectReview">Reject</a-button>
        <a-button preIcon="ant-design:cloud-upload-outlined" :disabled="!canPublish" @click="publishReview">Publish</a-button>
      </a-space>
      <div class="current-path">{{ draft?.status || '-' }} / {{ currentPath || '请选择文件' }}</div>
    </div>

    <div class="editor-layout">
      <aside class="file-panel">
        <div class="panel-toolbar">
          <a-button size="small" preIcon="ant-design:file-add-outlined" :disabled="!canEdit" @click="openCreateFile(false)">文件</a-button>
          <a-button size="small" preIcon="ant-design:folder-add-outlined" :disabled="!canEdit" @click="openCreateFile(true)">目录</a-button>
          <a-button size="small" danger preIcon="ant-design:delete-outlined" :disabled="!canEdit || !currentPath || currentPath === 'SKILL.md'" @click="deleteCurrentFile">
            删除
          </a-button>
        </div>
        <a-tree
          class="file-tree"
          :tree-data="treeData"
          :field-names="{ title: 'title', key: 'key', children: 'children' }"
          defaultExpandAll
          @select="onSelectFile"
        />
      </aside>

      <main class="code-panel">
        <div class="code-frame">
          <CodeEditor v-model:value="content" :mode="editorMode" :readonly="!currentPath || !canEdit" />
        </div>
      </main>

      <aside class="test-panel">
        <a-card size="small" title="测试运行" class="test-run-card">
          <a-form layout="vertical">
            <a-form-item label="测试 Prompt">
              <a-textarea v-model:value="testPrompt" :rows="4" :disabled="!canEdit" />
            </a-form-item>
            <a-form-item label="期望输出">
              <a-textarea v-model:value="expectedOutput" :rows="2" :disabled="!canEdit" />
            </a-form-item>
            <a-space direction="vertical" style="width: 100%">
              <a-button type="primary" block preIcon="ant-design:play-circle-outlined" :loading="testing" :disabled="!canEdit" @click="runTest">运行测试</a-button>
              <a-button block preIcon="ant-design:unordered-list-outlined" :loading="batchTesting" :disabled="!canEdit" @click="openBatchTest">Batch Test</a-button>
            </a-space>
          </a-form>
          <a-divider />
          <div class="test-history">
            <a-alert
              v-for="item in testRuns"
              :key="item.id"
              :type="item.status === 'success' ? 'success' : 'error'"
              :message="`${item.status} · ${item.durationMs || 0}ms`"
              :description="item.outputSummary || item.errorMessage"
              show-icon
            />
            <a-empty v-if="!testRuns.length" description="暂无测试记录" />
          </div>
        </a-card>
        <a-card size="small" title="Lint 结果">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="状态">{{ lintResult?.status || '-' }}</a-descriptions-item>
            <a-descriptions-item label="文件数">{{ lintResult?.fileCount ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="总大小">{{ lintResult?.totalSize ?? '-' }}</a-descriptions-item>
          </a-descriptions>
          <a-divider />
          <div class="result-list">
            <a-alert v-for="item in lintResult?.errors || []" :key="`e-${item}`" type="error" :message="item" show-icon />
            <a-alert v-for="item in lintResult?.warnings || []" :key="`w-${item}`" type="warning" :message="item" show-icon />
            <a-empty v-if="!hasLintMessages" description="暂无校验结果" />
          </div>
        </a-card>
      </aside>
    </div>

    <a-modal v-model:open="aiEditVisible" title="AI Edit Skill" :confirmLoading="repairing" @ok="runAiEdit" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="Change Request" required>
          <a-textarea
            v-model:value="aiEditInstruction"
            :rows="6"
            placeholder="Describe how to change this Skill. Example: make it focus on invoice extraction and add a JSON output example."
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="repairVisible" :title="repairTitle" width="920px" :footer="null" destroyOnClose>
      <a-alert v-if="repairResult?.summary" :message="repairResult.summary" :type="repairResult.source === 'ai' ? 'success' : 'warning'" show-icon />
      <div v-if="repairResult?.warnings?.length" class="repair-warnings">
        <a-alert v-for="item in repairResult.warnings" :key="item" type="warning" :message="item" show-icon />
      </div>
      <a-empty v-if="!repairResult?.files?.length" description="No repair suggestions" />
      <div v-for="file in repairResult?.files || []" :key="file.path" class="repair-file">
        <div class="repair-file-header">
          <strong>{{ file.action || 'upsert' }} {{ file.path }}</strong>
          <a-button v-if="!repairResult?.recordId" size="small" type="primary" :loading="applyingRepair" @click="applyRepairFile(file)">Apply</a-button>
        </div>
        <p v-if="file.explanation">{{ file.explanation }}</p>
        <a-tabs size="small">
          <a-tab-pane key="diff" tab="Diff">
            <pre class="repair-diff">{{ file.diff || 'No diff available.' }}</pre>
          </a-tab-pane>
          <a-tab-pane key="content" tab="New Content">
            <pre class="repair-diff">{{ file.action === 'delete' ? '(delete file)' : file.content }}</pre>
          </a-tab-pane>
        </a-tabs>
      </div>
      <a-button v-if="repairResult?.files?.length" type="primary" block :loading="applyingRepair" @click="applyAllRepairs">
        {{ repairResult?.recordId ? 'Confirm and Apply AI Edit' : 'Apply All Suggestions' }}
      </a-button>
    </a-modal>

    <a-modal v-model:open="batchVisible" title="Batch Test Cases" :confirmLoading="batchTesting" @ok="runBatchTest" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="Prompts" required>
          <a-textarea v-model:value="batchPromptText" :rows="8" placeholder="One prompt per line. Up to 10 prompts." />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="createVisible" :title="createDirectory ? '新建目录' : '新建文件'" @ok="submitCreateFile" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="相对路径" required>
          <a-input v-model:value="newPath" :placeholder="createDirectory ? 'examples' : 'examples/test_prompt.md'" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup name="OpenclawSkillEditor">
  import { computed, h, onMounted, ref } from 'vue';
  import { Input, Modal } from 'ant-design-vue';
  import { useRoute, useRouter } from 'vue-router';
  import { CodeEditor } from '/@/components/CodeEditor';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    approveSkillDraft,
    applySkillDraftAiEdit,
    applySkillDraftRepair,
    createSkillDraftFile,
    deleteSkillDraftFile,
    getSkillDraft,
    getSkillDraftTree,
    lintSkillDraft,
    listSkillDraftTests,
    publishSkillDraft,
    previewSkillDraftAiEdit,
    readSkillDraftFile,
    rejectSkillDraft,
    repairSkillDraft,
    runSkillDraftBatchTests,
    runSkillDraftTest,
    saveSkillDraftFile,
    submitSkillDraft,
  } from '../api';

  const route = useRoute();
  const router = useRouter();
  const { createMessage } = useMessage();
  const draftId = computed(() => String(route.params.id || ''));
  const draft = ref<any>(null);
  const treeData = ref<any[]>([]);
  const currentPath = ref('');
  const content = ref('');
  const lintResult = ref<any>(null);
  const testPrompt = ref('');
  const expectedOutput = ref('');
  const testing = ref(false);
  const batchTesting = ref(false);
  const batchVisible = ref(false);
  const batchPromptText = ref('');
  const repairing = ref(false);
  const applyingRepair = ref(false);
  const aiEditVisible = ref(false);
  const aiEditInstruction = ref('');
  const repairTitle = ref('AI Repair Suggestions');
  const repairVisible = ref(false);
  const repairResult = ref<any>(null);
  const testRuns = ref<any[]>([]);
  const createVisible = ref(false);
  const createDirectory = ref(false);
  const newPath = ref('');

  const editorMode = computed(() => {
    if (currentPath.value.endsWith('.json')) return 'application/json';
    if (currentPath.value.endsWith('.md')) return 'text/markdown';
    if (currentPath.value.endsWith('.py')) return 'python';
    if (currentPath.value.endsWith('.yml') || currentPath.value.endsWith('.yaml')) return 'yaml';
    return 'text/plain';
  });
  const hasLintMessages = computed(() => (lintResult.value?.errors?.length || 0) > 0 || (lintResult.value?.warnings?.length || 0) > 0);
  const canEdit = computed(() => ['editing', 'lint_failed', 'lint_passed', 'test_failed', 'rejected'].includes(draft.value?.status));
  const canReview = computed(() => draft.value?.status === 'submitted');
  const canPublish = computed(() => draft.value?.status === 'approved');
  const canSubmit = computed(() => canEdit.value && draft.value?.lastTestStatus === 'success');

  onMounted(async () => {
    await loadDraft();
    await loadTree();
    await loadTestRuns();
  });

  async function loadDraft() {
    draft.value = await getSkillDraft(draftId.value);
    if (draft.value?.lastLintResultJson) {
      try {
        lintResult.value = JSON.parse(draft.value.lastLintResultJson);
      } catch {
        lintResult.value = null;
      }
    }
  }

  async function loadTree() {
    const nodes = await getSkillDraftTree(draftId.value);
    treeData.value = normalizeNodes(nodes || []);
  }

  function normalizeNodes(nodes) {
    return nodes.map((node) => ({
      title: node.name,
      key: node.path,
      selectable: node.type === 'file',
      children: normalizeNodes(node.children || []),
    }));
  }

  async function onSelectFile(keys) {
    const path = keys?.[0];
    if (!path) return;
    const file = await readSkillDraftFile(draftId.value, path);
    currentPath.value = file.path;
    content.value = file.content || '';
  }

  async function saveCurrentFile() {
    if (!currentPath.value || !canEdit.value) return;
    await saveSkillDraftFile(draftId.value, { path: currentPath.value, content: content.value });
    createMessage.success('已保存');
    await loadTree();
    await loadDraft();
  }

  function openCreateFile(directory: boolean) {
    if (!canEdit.value) return;
    createDirectory.value = directory;
    newPath.value = '';
    createVisible.value = true;
  }

  async function submitCreateFile() {
    if (!canEdit.value) return;
    if (!newPath.value) {
      createMessage.warning('请填写相对路径');
      return;
    }
    await createSkillDraftFile(draftId.value, { path: newPath.value, directory: createDirectory.value, content: '' });
    createVisible.value = false;
    await loadTree();
  }

  function deleteCurrentFile() {
    if (!canEdit.value || !currentPath.value || currentPath.value === 'SKILL.md') return;
    Modal.confirm({
      title: `确认删除 ${currentPath.value}?`,
      onOk: async () => {
        await deleteSkillDraftFile(draftId.value, currentPath.value);
        currentPath.value = '';
        content.value = '';
        await loadTree();
      },
    });
  }

  async function runLint() {
    if (!canEdit.value) return;
    lintResult.value = await lintSkillDraft(draftId.value);
    createMessage[lintResult.value.passed ? 'success' : 'warning'](lintResult.value.status);
    await loadDraft();
  }

  async function runTest() {
    if (!canEdit.value) return;
    if (!testPrompt.value) {
      createMessage.warning('请填写测试 Prompt');
      return;
    }
    testing.value = true;
    try {
      const result = await runSkillDraftTest(draftId.value, { prompt: testPrompt.value, expectedOutput: expectedOutput.value });
      createMessage[result.status === 'success' ? 'success' : 'warning'](`测试 ${result.status}`);
      await loadDraft();
      await loadTestRuns();
    } finally {
      testing.value = false;
    }
  }

  function openBatchTest() {
    if (!canEdit.value) return;
    batchPromptText.value = '';
    batchVisible.value = true;
  }

  async function runBatchTest() {
    if (!canEdit.value) return;
    const prompts = batchPromptText.value
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean);
    if (!prompts.length) {
      createMessage.warning('Please enter at least one prompt');
      return;
    }
    if (prompts.length > 10) {
      createMessage.warning('Batch test supports at most 10 prompts');
      return;
    }
    batchTesting.value = true;
    try {
      const runs = await runSkillDraftBatchTests(draftId.value, {
        cases: prompts.map((prompt, index) => ({ name: `Case ${index + 1}`, prompt })),
      });
      const failed = (runs || []).filter((item) => item.status !== 'success').length;
      createMessage[failed ? 'warning' : 'success'](`Batch test finished: ${prompts.length - failed}/${prompts.length} passed`);
      batchVisible.value = false;
      await loadDraft();
      await loadTestRuns();
    } finally {
      batchTesting.value = false;
    }
  }

  function openAiEdit() {
    if (!canEdit.value) return;
    aiEditInstruction.value = '';
    aiEditVisible.value = true;
  }

  async function runAiEdit() {
    if (!canEdit.value) return;
    if (!aiEditInstruction.value.trim()) {
      createMessage.warning('Please describe the Skill change');
      return;
    }
    repairing.value = true;
    try {
      repairResult.value = await previewSkillDraftAiEdit(draftId.value, {
        instruction: aiEditInstruction.value.trim(),
      });
      repairTitle.value = 'AI Edit Suggestions';
      aiEditVisible.value = false;
      repairVisible.value = true;
    } finally {
      repairing.value = false;
    }
  }

  async function runRepair() {
    if (!canEdit.value) return;
    repairing.value = true;
    try {
      const latestFailed = testRuns.value.find((item) => item.status !== 'success');
      repairResult.value = await repairSkillDraft(draftId.value, {
        testRunId: latestFailed?.id || draft.value?.lastTestRunId,
        instruction: 'Analyze the latest lint and test failure, then suggest the smallest safe Skill file changes.',
      });
      repairTitle.value = 'AI Repair Suggestions';
      repairVisible.value = true;
    } finally {
      repairing.value = false;
    }
  }

  async function applyRepairFile(file) {
    await applyRepairs([file]);
  }

  async function applyAllRepairs() {
    await applyRepairs(repairResult.value?.files || []);
  }

  async function applyRepairs(files) {
    if (!files?.length) return;
    if (repairResult.value?.recordId) {
      Modal.confirm({
        title: 'Apply this AI edit preview?',
        content: 'This will write the suggested files into the current draft. Run Lint and tests after applying.',
        onOk: async () => {
          await applyAiEditPreview();
        },
      });
      return;
    }
    applyingRepair.value = true;
    try {
      await applySkillDraftRepair(draftId.value, {
        reason: repairResult.value?.summary,
        files: files.map((file) => ({
          path: file.path,
          action: file.action || 'upsert',
          content: file.content || '',
          explanation: file.explanation || '',
        })),
      });
      createMessage.success('Repair applied');
      repairVisible.value = false;
      await loadDraft();
      await loadTree();
      await loadTestRuns();
      if (currentPath.value) {
        await onSelectFile([currentPath.value]);
      }
    } finally {
      applyingRepair.value = false;
    }
  }

  async function applyAiEditPreview() {
    applyingRepair.value = true;
    try {
      await applySkillDraftAiEdit(draftId.value, {
        recordId: repairResult.value.recordId,
        reason: repairResult.value?.summary,
      });
      createMessage.success('AI edit applied. Please run Lint and tests next.');
      repairVisible.value = false;
      await loadDraft();
      await loadTree();
      await loadTestRuns();
      if (currentPath.value) {
        await onSelectFile([currentPath.value]);
      }
    } finally {
      applyingRepair.value = false;
    }
  }

  function submitReview() {
    if (!canSubmit.value) return;
    Modal.confirm({
      title: 'Submit this draft for review?',
      onOk: async () => {
        await submitSkillDraft(draftId.value);
        createMessage.success('Submitted for review');
        goBack();
      },
    });
  }

  function approveReview() {
    if (!canReview.value) return;
    Modal.confirm({
      title: 'Approve this draft?',
      onOk: async () => {
        await approveSkillDraft(draftId.value);
        createMessage.success('Approved');
        await loadDraft();
      },
    });
  }

  function rejectReview() {
    if (!canReview.value) return;
    let reason = '';
    Modal.confirm({
      title: 'Reject this draft?',
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: 'Reject reason',
        onChange: (event: Event) => {
          reason = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: 'Reject',
      onOk: async () => {
        await rejectSkillDraft(draftId.value, reason);
        createMessage.warning('Rejected');
        await loadDraft();
      },
    });
  }

  function publishReview() {
    if (!canPublish.value) return;
    Modal.confirm({
      title: 'Publish this draft as a formal Skill?',
      onOk: async () => {
        const skill = await publishSkillDraft(draftId.value);
        createMessage.success(`Published ${skill.slug} ${skill.version}`);
        await loadDraft();
      },
    });
  }

  async function loadTestRuns() {
    const result = await listSkillDraftTests(draftId.value, { pageNo: 1, pageSize: 5 });
    testRuns.value = result?.records || [];
  }

  function goBack() {
    router.push({ path: '/openclaw/skill-drafts' });
  }
</script>

<style scoped>
  .skill-editor {
    padding: 12px;
  }

  .editor-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }

  .current-path {
    color: #595959;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .editor-layout {
    display: grid;
    grid-template-columns: 260px minmax(0, 1fr) 330px;
    gap: 12px;
    min-height: calc(100vh - 170px);
  }

  .file-panel,
  .test-panel,
  .code-panel {
    min-width: 0;
  }

  .file-panel {
    border: 1px solid #d9d9d9;
    background: #fff;
    padding: 10px;
    overflow: auto;
  }

  .panel-toolbar {
    display: flex;
    gap: 6px;
    margin-bottom: 10px;
  }

  .file-tree {
    min-height: 420px;
  }

  .code-frame {
    height: 100%;
    min-height: 560px;
    border: 1px solid #d9d9d9;
    background: #fff;
  }

  .result-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .repair-warnings {
    display: grid;
    gap: 8px;
    margin-top: 10px;
  }

  .repair-file {
    border: 1px solid #d9d9d9;
    margin: 12px 0;
    padding: 10px;
  }

  .repair-file-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .repair-diff {
    max-height: 260px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    background: #f5f5f5;
    padding: 8px;
    margin: 8px 0 0;
  }

  .test-run-card {
    margin-bottom: 12px;
  }

  .test-history {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  @media (max-width: 1200px) {
    .editor-layout {
      grid-template-columns: 220px minmax(0, 1fr);
    }

    .test-panel {
      grid-column: 1 / -1;
    }
  }
</style>
