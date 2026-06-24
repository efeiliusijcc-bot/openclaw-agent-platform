import type { AppRouteRecordRaw } from '/@/router/types';
import { LAYOUT } from '/@/router/constant';

export const AI_ROUTE: AppRouteRecordRaw = {
  path: '',
  name: 'ai-parent',
  component: LAYOUT,
  meta: {
    title: 'ai',
  },
  children: [
    {
      path: '/ai',
      name: 'ai',
      component: () => import('/@/views/dashboard/ai/index.vue'),
      meta: {
        title: 'AI助手',
      },
    },
  ],
};

export const OPENCLAW_SKILL_DRAFT_EDITOR_ROUTE: AppRouteRecordRaw = {
  path: '',
  name: 'openclaw-skill-draft-editor-parent',
  component: LAYOUT,
  meta: {
    title: 'OpenClaw Skill 草稿编辑',
    hideMenu: true,
  },
  children: [
    {
      path: '/openclaw/skill-drafts/editor/:id',
      name: 'OpenclawSkillEditorStatic',
      component: () => import('/@/views/openclaw/skill/SkillEditor.vue'),
      meta: {
        title: 'Skill 草稿编辑',
        hideMenu: true,
        hideBreadcrumb: true,
      },
    },
  ],
};

export const staticRoutesList = [AI_ROUTE, OPENCLAW_SKILL_DRAFT_EDITOR_ROUTE];
