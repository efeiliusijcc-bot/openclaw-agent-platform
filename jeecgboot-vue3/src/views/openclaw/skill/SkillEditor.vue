<template>
  <div class="skill-editor">
    <div class="editor-header">
      <a-space>
        <a-button preIcon="ant-design:arrow-left-outlined" @click="goBack">返回</a-button>
        <a-button preIcon="ant-design:reload-outlined" @click="loadTree">刷新</a-button>
        <a-button type="primary" preIcon="ant-design:save-outlined" :disabled="!currentPath" @click="saveCurrentFile">保存</a-button>
        <a-button preIcon="ant-design:check-circle-outlined" @click="runLint">Lint</a-button>
      </a-space>
      <div class="current-path">{{ currentPath || '请选择文件' }}</div>
    </div>

    <div class="editor-layout">
      <aside class="file-panel">
        <div class="panel-toolbar">
          <a-button size="small" preIcon="ant-design:file-add-outlined" @click="openCreateFile(false)">文件</a-button>
          <a-button size="small" preIcon="ant-design:folder-add-outlined" @click="openCreateFile(true)">目录</a-button>
          <a-button size="small" danger preIcon="ant-design:delete-outlined" :disabled="!currentPath || currentPath === 'SKILL.md'" @click="deleteCurrentFile">
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
          <CodeEditor v-model:value="content" :mode="editorMode" :readonly="!currentPath" />
        </div>
      </main>

      <aside class="test-panel">
        <a-card size="small" title="测试运行" class="test-run-card">
          <a-form layout="vertical">
            <a-form-item label="测试 Prompt">
              <a-textarea v-model:value="testPrompt" :rows="4" />
            </a-form-item>
            <a-form-item label="期望输出">
              <a-textarea v-model:value="expectedOutput" :rows="2" />
            </a-form-item>
            <a-button type="primary" block preIcon="ant-design:play-circle-outlined" :loading="testing" @click="runTest">运行测试</a-button>
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
  import { computed, onMounted, ref } from 'vue';
  import { Modal } from 'ant-design-vue';
  import { useRoute, useRouter } from 'vue-router';
  import { CodeEditor } from '/@/components/CodeEditor';
  import { useMessage } from '/@/hooks/web/useMessage';
  import {
    createSkillDraftFile,
    deleteSkillDraftFile,
    getSkillDraftTree,
    lintSkillDraft,
    listSkillDraftTests,
    readSkillDraftFile,
    runSkillDraftTest,
    saveSkillDraftFile,
  } from '../api';

  const route = useRoute();
  const router = useRouter();
  const { createMessage } = useMessage();
  const draftId = computed(() => String(route.params.id || ''));
  const treeData = ref<any[]>([]);
  const currentPath = ref('');
  const content = ref('');
  const lintResult = ref<any>(null);
  const testPrompt = ref('');
  const expectedOutput = ref('');
  const testing = ref(false);
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

  onMounted(async () => {
    await loadTree();
    await loadTestRuns();
  });

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
    if (!currentPath.value) return;
    await saveSkillDraftFile(draftId.value, { path: currentPath.value, content: content.value });
    createMessage.success('已保存');
    await loadTree();
  }

  function openCreateFile(directory: boolean) {
    createDirectory.value = directory;
    newPath.value = '';
    createVisible.value = true;
  }

  async function submitCreateFile() {
    if (!newPath.value) {
      createMessage.warning('请填写相对路径');
      return;
    }
    await createSkillDraftFile(draftId.value, { path: newPath.value, directory: createDirectory.value, content: '' });
    createVisible.value = false;
    await loadTree();
  }

  function deleteCurrentFile() {
    if (!currentPath.value || currentPath.value === 'SKILL.md') return;
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
    lintResult.value = await lintSkillDraft(draftId.value);
    createMessage[lintResult.value.passed ? 'success' : 'warning'](lintResult.value.status);
  }

  async function runTest() {
    if (!testPrompt.value) {
      createMessage.warning('请填写测试 Prompt');
      return;
    }
    testing.value = true;
    try {
      const result = await runSkillDraftTest(draftId.value, { prompt: testPrompt.value, expectedOutput: expectedOutput.value });
      createMessage[result.status === 'success' ? 'success' : 'warning'](`测试 ${result.status}`);
      await loadTestRuns();
    } finally {
      testing.value = false;
    }
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
