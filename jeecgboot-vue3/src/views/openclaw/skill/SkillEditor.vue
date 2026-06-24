<template>
  <div class="skill-editor">
    <div class="editor-header">
      <a-space>
        <a-button preIcon="ant-design:arrow-left-outlined" @click="goBack">返回</a-button>
        <a-button preIcon="ant-design:reload-outlined" @click="loadTree">刷新</a-button>
        <a-button type="primary" preIcon="ant-design:save-outlined" :disabled="!currentPath || !canEdit" @click="saveCurrentFile">保存</a-button>
        <a-button preIcon="ant-design:check-circle-outlined" :disabled="!canEdit" @click="runLint">Lint</a-button>
        <a-button preIcon="ant-design:edit-outlined" :loading="repairing" :disabled="!canEdit" @click="openAiEdit">AI 编辑</a-button>
        <a-button preIcon="ant-design:tool-outlined" :loading="repairing" :disabled="!canEdit" @click="runRepair">AI 修复</a-button>
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
              <a-button block preIcon="ant-design:unordered-list-outlined" :loading="batchTesting" :disabled="!canEdit" @click="openBatchTest">批量测试</a-button>
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
        <a-card size="small" title="测试报告" class="test-report-card" :loading="reportLoading">
          <a-empty v-if="!selectedReport" description="未选择测试报告" />
          <template v-else>
            <a-descriptions :column="1" size="small" bordered>
              <a-descriptions-item label="状态">{{ selectedReport.status }}</a-descriptions-item>
              <a-descriptions-item label="测试运行">{{ selectedReport.testRunId }}</a-descriptions-item>
              <a-descriptions-item label="Agent">{{ selectedReport.agentKey || '-' }}</a-descriptions-item>
              <a-descriptions-item label="Lint">{{ selectedReport.lintStatus || '-' }}</a-descriptions-item>
              <a-descriptions-item label="Gateway">{{ selectedReport.gatewayStatus || '-' }}</a-descriptions-item>
              <a-descriptions-item label="耗时">{{ selectedReport.durationMs ?? '-' }}ms</a-descriptions-item>
              <a-descriptions-item label="错误">{{ selectedReport.error?.type || '-' }} / {{ selectedReport.error?.code || '-' }}</a-descriptions-item>
            </a-descriptions>
            <a-tabs size="small">
              <a-tab-pane key="input" tab="输入"><pre class="report-pre">{{ selectedReport.input || '-' }}</pre></a-tab-pane>
              <a-tab-pane key="output" tab="输出"><pre class="report-pre">{{ selectedReport.output || '-' }}</pre></a-tab-pane>
              <a-tab-pane key="error" tab="错误"><pre class="report-pre">{{ selectedReport.error?.message || '-' }}</pre></a-tab-pane>
              <a-tab-pane key="logs" tab="日志"><pre class="report-pre">{{ (selectedReport.logs || []).join('\n') || '-' }}</pre></a-tab-pane>
            </a-tabs>
          </template>
        </a-card>
        <a-card size="small" title="版本历史" class="version-card" :loading="versionLoading">
          <a-space direction="vertical" style="width: 100%">
            <a-button block preIcon="ant-design:reload-outlined" @click="loadVersions">刷新版本</a-button>
            <a-empty v-if="!versions.length" description="暂无版本" />
            <div v-for="item in versions" :key="item.id" class="version-item">
              <div class="version-head">
                <strong>v{{ item.versionNo }} · {{ item.sourceType }}</strong>
                <span>{{ item.createdTime || '-' }}</span>
              </div>
              <div class="version-status">
                Lint: {{ item.lintStatus || '-' }} / Test: {{ item.testStatus || '-' }}
              </div>
              <div v-if="item.reviewStatus" class="version-status">Review: {{ item.reviewStatus }}</div>
              <div class="version-summary">{{ item.summary || '-' }}</div>
              <a-space>
                <a-button size="small" @click="openVersionDetail(item.versionNo)">详情</a-button>
                <a-button size="small" @click="openVersionDiff(item.versionNo)">差异</a-button>
                <a-button size="small" type="primary" :disabled="!canSubmitVersion(item)" @click="submitVersionReview(item)">鎻愪氦瀹℃牳</a-button>
                <a-button size="small" danger :disabled="!canEdit" @click="rollbackVersion(item.versionNo)">回滚</a-button>
              </a-space>
            </div>
          </a-space>
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

    <a-modal v-model:open="aiEditVisible" title="AI 编辑 Skill" :confirmLoading="repairing" @ok="runAiEdit" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="修改要求" required>
          <a-textarea
            v-model:value="aiEditInstruction"
            :rows="6"
            placeholder="描述你希望如何修改这个 Skill。例如：改成发票抽取 Skill，并增加 JSON 输出示例。"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="repairVisible" :title="repairTitle" width="920px" :footer="null" destroyOnClose>
      <a-alert v-if="repairResult?.summary" :message="repairResult.summary" :type="repairResult.source === 'ai' ? 'success' : 'warning'" show-icon />
      <div v-if="repairResult?.warnings?.length" class="repair-warnings">
        <a-alert v-for="item in repairResult.warnings" :key="item" type="warning" :message="item" show-icon />
      </div>
      <a-empty v-if="!repairResult?.files?.length" description="暂无修复建议" />
      <div v-for="file in repairResult?.files || []" :key="file.path" class="repair-file">
        <div class="repair-file-header">
          <strong>{{ file.action || 'upsert' }} {{ file.path }}</strong>
          <a-button v-if="!repairResult?.recordId" size="small" type="primary" :loading="applyingRepair" @click="applyRepairFile(file)">应用</a-button>
        </div>
        <p v-if="file.explanation">{{ file.explanation }}</p>
        <a-tabs size="small">
          <a-tab-pane key="diff" tab="差异">
            <pre class="repair-diff">{{ file.diff || '暂无差异。' }}</pre>
          </a-tab-pane>
          <a-tab-pane key="content" tab="新内容">
            <pre class="repair-diff">{{ file.action === 'delete' ? '（删除文件）' : file.content }}</pre>
          </a-tab-pane>
        </a-tabs>
      </div>
      <a-button v-if="repairResult?.files?.length" type="primary" block :loading="applyingRepair" @click="applyAllRepairs">
        {{ repairResult?.recordId ? (suggestionMode === 'aiEdit' ? '确认并应用 AI 编辑' : '确认并应用 AI 修复') : '应用全部建议' }}
      </a-button>
      <a-space v-if="repairResult?.source === 'applied' && suggestionMode === 'repair'" direction="vertical" style="width: 100%; margin-top: 12px">
        <a-button block preIcon="ant-design:check-circle-outlined" @click="runLint">重新运行 Lint</a-button>
        <a-button block type="primary" preIcon="ant-design:play-circle-outlined" @click="runTest">重新运行测试</a-button>
      </a-space>
    </a-modal>

    <a-modal v-model:open="batchVisible" title="批量测试用例" :confirmLoading="batchTesting" @ok="runBatchTest" destroyOnClose>
      <a-form layout="vertical">
        <a-form-item label="Prompts" required>
          <a-textarea v-model:value="batchPromptText" :rows="8" placeholder="每行一个 Prompt，最多 10 条。" />
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

    <a-modal v-model:open="versionDetailVisible" :title="selectedVersion ? `版本 v${selectedVersion.versionNo}` : '版本详情'" width="920px" :footer="null" destroyOnClose>
      <a-empty v-if="!selectedVersion" description="未选择版本" />
      <template v-else>
        <a-descriptions :column="2" size="small" bordered>
          <a-descriptions-item label="来源">{{ selectedVersion.sourceType }}</a-descriptions-item>
          <a-descriptions-item label="记录">{{ selectedVersion.sourceRecordId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Lint">{{ selectedVersion.lintStatus || '-' }}</a-descriptions-item>
          <a-descriptions-item label="测试">{{ selectedVersion.testStatus || '-' }}</a-descriptions-item>
          <a-descriptions-item label="测试运行">{{ selectedVersion.testRunId || '-' }}</a-descriptions-item>
          <a-descriptions-item label="Hash">{{ selectedVersion.fileHash || '-' }}</a-descriptions-item>
        </a-descriptions>
        <a-tabs size="small" style="margin-top: 12px">
          <a-tab-pane key="files" tab="文件">
            <div v-for="(value, path) in selectedVersion.files || {}" :key="path" class="version-file">
              <strong>{{ path }}</strong>
              <pre class="report-pre">{{ value }}</pre>
            </div>
          </a-tab-pane>
          <a-tab-pane key="record" tab="AI 记录">
            <pre class="report-pre">{{ selectedVersion.sourceRecord ? JSON.stringify(selectedVersion.sourceRecord, null, 2) : '-' }}</pre>
          </a-tab-pane>
          <a-tab-pane key="report" tab="测试报告">
            <pre class="report-pre">{{ selectedVersion.testReport ? JSON.stringify(selectedVersion.testReport, null, 2) : '-' }}</pre>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-modal>

    <a-modal v-model:open="versionDiffVisible" title="版本差异" width="720px" :footer="null" destroyOnClose>
      <a-empty v-if="!versionDiff?.diffs?.length" description="暂无文件变更" />
      <div v-for="item in versionDiff?.diffs || []" :key="item.path" class="version-diff-row">
        <a-tag :color="item.changeType === 'added' ? 'green' : item.changeType === 'deleted' ? 'red' : 'blue'">{{ item.changeType }}</a-tag>
        <strong>{{ item.path }}</strong>
        <div class="version-status">{{ item.beforeHash || '-' }} -> {{ item.afterHash || '-' }}</div>
      </div>
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
    applySkillDraftAiEdit,
    applySkillDraftRepair,
    createSkillDraftFile,
    deleteSkillDraftFile,
    diffSkillDraftVersion,
    getSkillDraft,
    getSkillDraftTestReport,
    getSkillDraftTree,
    getSkillDraftVersion,
    lintSkillDraft,
    listSkillDraftVersions,
    listSkillDraftTests,
    previewSkillDraftAiEdit,
    readSkillDraftFile,
    repairSkillDraft,
    rollbackSkillDraftVersion,
    runSkillDraftBatchTests,
    runSkillDraftTest,
    saveSkillDraftFile,
    submitSkillDraftVersionReview,
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
  const repairTitle = ref('AI 修复建议');
  const repairVisible = ref(false);
  const repairResult = ref<any>(null);
  const suggestionMode = ref<'aiEdit' | 'repair'>('repair');
  const testRuns = ref<any[]>([]);
  const selectedReport = ref<any>(null);
  const reportLoading = ref(false);
  const createVisible = ref(false);
  const createDirectory = ref(false);
  const newPath = ref('');
  const versions = ref<any[]>([]);
  const versionLoading = ref(false);
  const selectedVersion = ref<any>(null);
  const versionDetailVisible = ref(false);
  const versionDiff = ref<any>(null);
  const versionDiffVisible = ref(false);

  const editorMode = computed(() => {
    if (currentPath.value.endsWith('.json')) return 'application/json';
    if (currentPath.value.endsWith('.md')) return 'text/markdown';
    if (currentPath.value.endsWith('.py')) return 'python';
    if (currentPath.value.endsWith('.yml') || currentPath.value.endsWith('.yaml')) return 'yaml';
    return 'text/plain';
  });
  const hasLintMessages = computed(() => (lintResult.value?.errors?.length || 0) > 0 || (lintResult.value?.warnings?.length || 0) > 0);
  const canEdit = computed(() => ['editing', 'lint_failed', 'lint_passed', 'test_failed', 'test_passed', 'rejected'].includes(draft.value?.status));

  onMounted(async () => {
    await loadDraft();
    await loadTree();
    await loadTestRuns();
    await loadVersions();
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
    await loadVersions();
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
    await loadDraft();
    await loadVersions();
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
        await loadDraft();
        await loadVersions();
      },
    });
  }

  async function runLint() {
    if (!canEdit.value) return;
    lintResult.value = await lintSkillDraft(draftId.value);
    createMessage[lintResult.value.passed ? 'success' : 'warning'](lintResult.value.status);
    await loadDraft();
    await loadVersions();
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
      await loadTestReport(result.id);
      await loadDraft();
      await loadTestRuns();
      await loadVersions();
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
      createMessage.warning('请至少输入一个 Prompt');
      return;
    }
    if (prompts.length > 10) {
      createMessage.warning('批量测试最多支持 10 个 Prompt');
      return;
    }
    batchTesting.value = true;
    try {
      const runs = await runSkillDraftBatchTests(draftId.value, {
        cases: prompts.map((prompt, index) => ({ name: `用例 ${index + 1}`, prompt })),
      });
      const failed = (runs || []).filter((item) => item.status !== 'success').length;
      createMessage[failed ? 'warning' : 'success'](`批量测试完成：${prompts.length - failed}/${prompts.length} 通过`);
      batchVisible.value = false;
      await loadDraft();
      await loadTestRuns();
      await loadVersions();
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
      createMessage.warning('请描述要修改的 Skill 内容');
      return;
    }
    repairing.value = true;
    try {
      repairResult.value = await previewSkillDraftAiEdit(draftId.value, {
        instruction: aiEditInstruction.value.trim(),
      });
      suggestionMode.value = 'aiEdit';
      repairTitle.value = 'AI 编辑建议';
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
        instruction: '分析最近一次 Lint 和测试失败原因，并建议最小且安全的 Skill 文件修改。',
      });
      suggestionMode.value = 'repair';
      repairTitle.value = 'AI 修复建议';
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
        title: suggestionMode.value === 'aiEdit' ? '应用这次 AI 编辑预览？' : '应用这次 AI 修复预览？',
        content: '系统会把建议文件写入当前草稿。应用后请重新运行 Lint 和测试。',
        onOk: async () => {
          await applySuggestionPreview();
        },
      });
      return;
    }
    createMessage.warning('请重新生成建议后再应用，当前缺少 recordId。');
  }

  async function applySuggestionPreview() {
    applyingRepair.value = true;
    try {
      if (suggestionMode.value === 'aiEdit') {
        repairResult.value = await applySkillDraftAiEdit(draftId.value, {
          recordId: repairResult.value.recordId,
          reason: repairResult.value?.summary,
        });
        createMessage.success('AI 编辑已应用，请继续运行 Lint 和测试。');
        repairVisible.value = false;
      } else {
        repairResult.value = await applySkillDraftRepair(draftId.value, {
          recordId: repairResult.value.recordId,
          reason: repairResult.value?.summary,
        });
        createMessage.success('修复建议已应用，请重新运行 Lint 和测试。');
      }
      await loadDraft();
      await loadTree();
      await loadTestRuns();
      await loadVersions();
      if (currentPath.value) {
        await onSelectFile([currentPath.value]);
      }
    } finally {
      applyingRepair.value = false;
    }
  }

  function canSubmitVersion(item) {
    return item?.lintStatus === 'lint_passed' && item?.testStatus === 'success' && !['SUBMITTED', 'APPROVED'].includes(item?.reviewStatus);
  }

  function submitVersionReview(item) {
    if (!canSubmitVersion(item)) return;
    let submitComment = '';
    Modal.confirm({
      title: `鎻愪氦 v${item.versionNo} 瀹℃牳?`,
      content: h(Input.TextArea, {
        rows: 3,
        placeholder: '提交说明',
        onChange: (event: Event) => {
          submitComment = (event.target as HTMLTextAreaElement).value;
        },
      }),
      okText: '鎻愪氦瀹℃牳',
      onOk: async () => {
        const review = await submitSkillDraftVersionReview(draftId.value, item.versionNo, { submitComment });
        createMessage.success(`已提交审核 ${review.id}：${review.status}`);
        await loadVersions();
      },
    });
  }

  async function loadTestRuns() {
    const result = await listSkillDraftTests(draftId.value, { pageNo: 1, pageSize: 5 });
    testRuns.value = result?.records || [];
    if (!selectedReport.value && testRuns.value.length) {
      await loadTestReport(testRuns.value[0].id);
    }
  }

  async function loadTestReport(testRunId: string) {
    if (!testRunId) return;
    reportLoading.value = true;
    try {
      selectedReport.value = await getSkillDraftTestReport(draftId.value, testRunId);
    } finally {
      reportLoading.value = false;
    }
  }

  async function loadVersions() {
    versionLoading.value = true;
    try {
      versions.value = await listSkillDraftVersions(draftId.value);
    } finally {
      versionLoading.value = false;
    }
  }

  async function openVersionDetail(versionNo: number) {
    selectedVersion.value = await getSkillDraftVersion(draftId.value, versionNo);
    versionDetailVisible.value = true;
  }

  async function openVersionDiff(versionNo: number) {
    versionDiff.value = await diffSkillDraftVersion(draftId.value, { fromVersionNo: versionNo });
    versionDiffVisible.value = true;
  }

  function rollbackVersion(versionNo: number) {
    if (!canEdit.value) return;
    Modal.confirm({
      title: `回滚到 v${versionNo}?`,
      content: '回滚会生成一个新的版本，之后需要重新运行 Lint 和 Test。',
      onOk: async () => {
        const result = await rollbackSkillDraftVersion(draftId.value, versionNo);
        createMessage.success(`已回滚并生成 v${result.versionNo}，请重新运行 Lint/Test`);
        versionDetailVisible.value = false;
        versionDiffVisible.value = false;
        await loadDraft();
        await loadTree();
        await loadVersions();
        if (currentPath.value) {
          await onSelectFile([currentPath.value]).catch(() => {
            currentPath.value = '';
            content.value = '';
          });
        }
      },
    });
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

  .test-report-card {
    margin-bottom: 12px;
  }

  .version-card {
    margin-bottom: 12px;
  }

  .version-item {
    border: 1px solid #e5e7eb;
    border-radius: 6px;
    padding: 8px;
    background: #fff;
  }

  .version-head {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 12px;
  }

  .version-status,
  .version-summary {
    margin: 4px 0;
    color: #595959;
    font-size: 12px;
    word-break: break-word;
  }

  .version-file,
  .version-diff-row {
    margin-bottom: 12px;
  }

  .report-pre {
    max-height: 180px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-word;
    background: #f5f5f5;
    padding: 8px;
    margin: 8px 0 0;
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
