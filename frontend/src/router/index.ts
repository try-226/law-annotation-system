import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import type { Role } from '../api/types'
import BasicLayout from '../layouts/BasicLayout.vue'
import { landingRouteName, roleCanAccess } from './routePolicy'

export { landingRouteName } from './routePolicy'
import { authState, restoreSession } from '../state/auth'
import LawDetailView from '../views/law/LawDetailView.vue'
import LawImportView from '../views/law/LawImportView.vue'
import LawListView from '../views/law/LawListView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: Role[]
    title?: string
    accountLanding?: boolean
    navGroup?: 'dashboard' | 'users' | 'laws' | 'tasks' | 'my-tasks' | 'role-tasks'
    breadcrumbs?: string[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
  },
  {
    path: '/session-recovery',
    name: 'session-recovery',
    component: () => import('../views/SessionRecoveryView.vue'),
  },
  {
    path: '/',
    component: BasicLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'account-home',
        component: () => import('../views/RouterLandingView.vue'),
        meta: { accountLanding: true },
      },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('../views/dashboard/DashboardView.vue'),
        meta: { roles: ['ADMIN'], title: '工作台', navGroup: 'dashboard', breadcrumbs: ['工作台'] },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { roles: ['ADMIN'], title: '用户管理', navGroup: 'users', breadcrumbs: ['用户管理'] },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('../views/ProfileView.vue'),
        meta: { title: '个人信息' },
      },
      {
        path: 'profile/password',
        name: 'change-password',
        component: () => import('../views/ChangePasswordView.vue'),
        meta: { title: '修改密码' },
      },
      {
        path: 'laws',
        name: 'law-list',
        component: LawListView,
        meta: { roles: ['ADMIN'], title: '法律管理', navGroup: 'laws', breadcrumbs: ['法律管理'] },
      },
      {
        path: 'search',
        name: 'admin-search',
        component: () => import('../views/search/SearchResultsView.vue'),
        meta: { roles: ['ADMIN'], title: '全库搜索', navGroup: 'laws', breadcrumbs: ['法律管理', '全库搜索'] },
      },
      {
        path: 'laws/import',
        name: 'law-import',
        component: LawImportView,
        meta: { roles: ['ADMIN'], title: '导入法律', navGroup: 'laws', breadcrumbs: ['法律管理', '导入法律'] },
      },
      {
        path: 'laws/recycle',
        name: 'law-recycle',
        component: () => import('../views/recycle/LawRecycleView.vue'),
        meta: { roles: ['ADMIN'], title: '法律回收站', navGroup: 'laws', breadcrumbs: ['法律管理', '回收站'] },
      },
      {
        path: 'field-config',
        name: 'field-config',
        component: () => import('../views/field/FieldConfigView.vue'),
        meta: { roles: ['ADMIN'], title: '字段配置', navGroup: 'laws', breadcrumbs: ['法律管理', '字段配置'] },
      },
      {
        path: 'laws/:lawId',
        name: 'law-detail',
        component: LawDetailView,
        meta: { roles: ['ADMIN'], title: '法律详情', navGroup: 'laws', breadcrumbs: ['法律管理', '法律详情'] },
      },
      {
        path: 'laws/:lawId/history',
        name: 'law-history',
        component: () => import('../views/history/LawHistoryView.vue'),
        meta: { roles: ['ADMIN'], title: '历史记录', navGroup: 'laws', breadcrumbs: ['法律管理', '历史记录'] },
      },
      {
        path: 'laws/:lawId/history/content-versions/:contentVersionId',
        name: 'history-content-version',
        component: () => import('../views/history/HistoryDetailView.vue'),
        meta: { roles: ['ADMIN'], title: '内容版本历史', navGroup: 'laws', breadcrumbs: ['法律管理', '历史记录', '内容版本'] },
      },
      {
        path: 'laws/:lawId/history/annotation-versions/:annotationVersionId',
        name: 'history-annotation-version',
        component: () => import('../views/history/HistoryDetailView.vue'),
        meta: { roles: ['ADMIN'], title: '标注版本历史', navGroup: 'laws', breadcrumbs: ['法律管理', '历史记录', '标注版本'] },
      },
      {
        path: 'laws/:lawId/history/audits/:auditId',
        name: 'history-law-audit',
        component: () => import('../views/history/HistoryDetailView.vue'),
        meta: { roles: ['ADMIN'], title: '法律审计历史', navGroup: 'laws', breadcrumbs: ['法律管理', '历史记录', '审计详情'] },
      },
      {
        path: 'laws/:lawId/history/tasks/:taskId',
        name: 'task-history',
        component: () => import('../views/history/HistoryDetailView.vue'),
        meta: { roles: ['ADMIN', 'ANNOTATOR'], title: '任务历史', navGroup: 'role-tasks', breadcrumbs: ['任务历史'] },
      },
      {
        path: 'tasks',
        name: 'admin-tasks',
        component: () => import('../views/task/AdminTaskListView.vue'),
        meta: { roles: ['ADMIN'], title: '任务管理', navGroup: 'tasks', breadcrumbs: ['任务管理'] },
      },
      {
        path: 'tasks/:taskId',
        name: 'admin-task-detail',
        component: () => import('../views/task/TaskDetailView.vue'),
        meta: { roles: ['ADMIN'], title: '任务详情', navGroup: 'tasks', breadcrumbs: ['任务管理', '任务详情'] },
      },
      {
        path: 'tasks/:taskId/review',
        name: 'review-workbench',
        component: () => import('../views/review/ReviewWorkbenchView.vue'),
        meta: { roles: ['ADMIN'], title: '审核工作台', navGroup: 'tasks', breadcrumbs: ['任务管理', '审核工作台'] },
      },
      {
        path: 'my-tasks',
        name: 'my-tasks',
        component: () => import('../views/task/MyTasksView.vue'),
        meta: { roles: ['ANNOTATOR'], title: '我的任务', navGroup: 'my-tasks', breadcrumbs: ['我的任务'] },
      },
      {
        path: 'my-tasks/:taskId',
        name: 'my-task-detail',
        component: () => import('../views/task/TaskDetailView.vue'),
        meta: { roles: ['ANNOTATOR'], title: '任务详情', navGroup: 'my-tasks', breadcrumbs: ['我的任务', '任务详情'] },
      },
      {
        path: 'my-tasks/:taskId/annotation',
        name: 'annotation-workbench',
        component: () => import('../views/annotation/AnnotationWorkbenchView.vue'),
        meta: { roles: ['ANNOTATOR'], title: '标注工作台', navGroup: 'my-tasks', breadcrumbs: ['我的任务', '标注工作台'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach(async (to) => {
  if (authState.status === 'unknown') {
    await restoreSession()
  }

  if (authState.status === 'restore-error') {
    if (to.name === 'session-recovery') {
      return true
    }
    return { name: 'session-recovery', query: { redirect: to.fullPath } }
  }

  if (to.name === 'session-recovery') {
    if (authState.status === 'authenticated' && authState.user) {
      return { name: landingRouteName(authState.user.role) }
    }
    return { name: 'login' }
  }

  if (to.name === 'login') {
    if (authState.status === 'authenticated' && authState.user) {
      return { name: landingRouteName(authState.user.role) }
    }
    return true
  }

  if (to.meta.requiresAuth && (authState.status !== 'authenticated' || !authState.user)) {
    return { name: 'login' }
  }

  if (to.meta.accountLanding && authState.user) {
    return { name: landingRouteName(authState.user.role) }
  }

  if (authState.user && !roleCanAccess(authState.user.role, to.meta.roles)) {
    return { name: landingRouteName(authState.user.role) }
  }

  return true
})

export default router
